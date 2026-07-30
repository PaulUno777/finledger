package com.pauluno.finledger.application.usecase;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pauluno.finledger.application.dto.PostSplitPaymentCommand;
import com.pauluno.finledger.application.dto.PostTransactionResult;
import com.pauluno.finledger.application.event.TransactionPosted;
import com.pauluno.finledger.application.exception.BusinessRuleException;
import com.pauluno.finledger.application.exception.ResourceNotFoundException;
import com.pauluno.finledger.application.port.in.PostSplitPaymentUseCase;
import com.pauluno.finledger.application.port.out.AccountBalanceRepository;
import com.pauluno.finledger.application.port.out.IdempotencyStore;
import com.pauluno.finledger.application.port.out.JournalEntryRepository;
import com.pauluno.finledger.application.port.out.LedgerAccountRepository;
import com.pauluno.finledger.application.port.out.OutboxWriter;
import com.pauluno.finledger.application.port.out.SplitPlanResolver;
import com.pauluno.finledger.application.port.out.SplitRuleSetRepository;
import com.pauluno.finledger.domain.exception.AccountClosedException;
import com.pauluno.finledger.domain.exception.CurrencyMismatchException;
import com.pauluno.finledger.domain.exception.InsufficientFundsException;
import com.pauluno.finledger.domain.exception.InvalidJournalEntryException;
import com.pauluno.finledger.domain.model.AccountBalance;
import com.pauluno.finledger.domain.model.AccountType;
import com.pauluno.finledger.domain.model.IdempotencyKey;
import com.pauluno.finledger.domain.model.JournalEntry;
import com.pauluno.finledger.domain.model.LedgerAccount;
import com.pauluno.finledger.domain.model.Money;
import com.pauluno.finledger.domain.model.Posting;
import com.pauluno.finledger.domain.model.SettlementStatus;
import com.pauluno.finledger.domain.model.SplitPlan;
import com.pauluno.finledger.domain.model.SplitRuleSet;
import com.pauluno.finledger.domain.model.TransactionReference;

@Service
public class PostSplitPaymentService implements PostSplitPaymentUseCase {

    private static final int MAX_OPTIMISTIC_RETRIES = 3;

    private final IdempotencyStore idempotencyStore;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final AccountBalanceRepository accountBalanceRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final OutboxWriter outboxWriter;
    private final SplitRuleSetRepository splitRuleSetRepository;
    private final SplitPlanResolver splitPlanResolver;
    private final ObjectMapper objectMapper;

    public PostSplitPaymentService(
            IdempotencyStore idempotencyStore,
            LedgerAccountRepository ledgerAccountRepository,
            AccountBalanceRepository accountBalanceRepository,
            JournalEntryRepository journalEntryRepository,
            OutboxWriter outboxWriter,
            SplitRuleSetRepository splitRuleSetRepository,
            SplitPlanResolver splitPlanResolver
    ) {
        this.idempotencyStore = idempotencyStore;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.accountBalanceRepository = accountBalanceRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.outboxWriter = outboxWriter;
        this.splitRuleSetRepository = splitRuleSetRepository;
        this.splitPlanResolver = splitPlanResolver;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    @Override
    @Transactional
    public PostTransactionResult execute(PostSplitPaymentCommand command) {
        String requestHash = hashRequest(command);
        IdempotencyKey key = new IdempotencyKey(command.idempotencyKey());
        IdempotencyStore.BeginOutcome begin = idempotencyStore.tryBegin(
                command.tenantId(), key, requestHash);

        if (begin instanceof IdempotencyStore.BeginOutcome.Replay replay) {
            return deserializeResult(replay.responseSnapshot(), true);
        }

        try {
            PostTransactionResult result = executeWithRetry(command, key);
            idempotencyStore.complete(command.tenantId(), key, serializeResult(result));
            return result;
        } catch (RuntimeException ex) {
            idempotencyStore.fail(command.tenantId(), key);
            throw ex;
        }
    }

    private PostTransactionResult executeWithRetry(PostSplitPaymentCommand command, IdempotencyKey key) {
        OptimisticLockingFailureException last = null;
        for (int attempt = 1; attempt <= MAX_OPTIMISTIC_RETRIES; attempt++) {
            try {
                return postOnce(command, key);
            } catch (OptimisticLockingFailureException ex) {
                last = ex;
                try {
                    Thread.sleep(10L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
            }
        }
        throw last;
    }

    private PostTransactionResult postOnce(PostSplitPaymentCommand command, IdempotencyKey key) {
        try {
            SplitRuleSet ruleSet = splitRuleSetRepository
                    .findByTenantAndKey(command.tenantId(), command.ruleSetKey())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Split rule set not found: " + command.ruleSetKey()));

            Money total = Money.of(
                    command.totalAmount(),
                    Currency.getInstance(command.currencyCode()));
            Map<AccountType, UUID> accountsByType = new EnumMap<>(AccountType.class);
            for (Map.Entry<String, UUID> entry : command.accountsByType().entrySet()) {
                accountsByType.put(AccountType.valueOf(entry.getKey()), entry.getValue());
            }

            SplitPlan plan = splitPlanResolver.resolve(
                    ruleSet,
                    total,
                    new SplitPlanResolver.SplitContext(command.sourceAccountId(), accountsByType)
            );

            List<UUID> accountIds = new ArrayList<>();
            accountIds.add(command.sourceAccountId());
            for (SplitPlan.SplitLeg leg : plan.legs()) {
                accountIds.add(leg.accountId());
            }

            Map<UUID, LedgerAccount> accounts = new HashMap<>();
            for (UUID accountId : accountIds) {
                LedgerAccount account = ledgerAccountRepository
                        .findByIdForTenant(accountId, command.tenantId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Account not found for tenant: " + accountId));
                accounts.put(account.id(), account);
            }

            Map<UUID, AccountBalance> balances = accountBalanceRepository.findByAccountIds(accountIds);

            List<Posting> postings = new ArrayList<>();
            postings.add(new Posting(
                    command.sourceAccountId(),
                    total.negated(),
                    SettlementStatus.SETTLED
            ));
            for (SplitPlan.SplitLeg leg : plan.legs()) {
                postings.add(new Posting(leg.accountId(), leg.amount(), SettlementStatus.SETTLED));
            }

            JournalEntry entry = JournalEntry.create(
                    command.tenantId(),
                    key,
                    new TransactionReference(command.transactionReference()),
                    postings,
                    accounts,
                    balances,
                    Instant.now(),
                    null
            );

            JournalEntry saved = journalEntryRepository.save(entry);
            appendOutbox(saved);
            return PostTransactionService.toResult(saved, false);
        } catch (InvalidJournalEntryException
                 | CurrencyMismatchException
                 | InsufficientFundsException
                 | AccountClosedException
                 | IllegalArgumentException ex) {
            throw new BusinessRuleException(toCode(ex), ex.getMessage(), ex);
        }
    }

    private void appendOutbox(JournalEntry saved) {
        TransactionPosted event = new TransactionPosted(
                saved.tenantId(),
                saved.id(),
                saved.transactionReference().value(),
                saved.type().name(),
                saved.postings().stream()
                        .map(p -> new TransactionPosted.PostingSummary(
                                p.accountId(),
                                p.amount().amount().toPlainString(),
                                p.amount().currency().getCurrencyCode(),
                                p.settlementStatus().name()
                        ))
                        .toList(),
                saved.occurredAt()
        );
        try {
            outboxWriter.append(new OutboxWriter.OutboxMessage(
                    saved.tenantId(),
                    saved.id(),
                    TransactionPosted.EVENT_TYPE,
                    objectMapper.writeValueAsString(event)
            ));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize TransactionPosted outbox payload", e);
        }
    }

    private static String toCode(RuntimeException ex) {
        String name = ex.getClass().getSimpleName();
        if (name.endsWith("Exception")) {
            name = name.substring(0, name.length() - "Exception".length());
        }
        return name.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
    }

    private String hashRequest(PostSplitPaymentCommand command) {
        try {
            Map<String, Object> canonical = new HashMap<>();
            canonical.put("tenantId", command.tenantId().toString());
            canonical.put("transactionReference", command.transactionReference());
            canonical.put("totalAmount", command.totalAmount());
            canonical.put("currencyCode", command.currencyCode());
            canonical.put("sourceAccountId", command.sourceAccountId().toString());
            canonical.put("accountsByType", command.accountsByType());
            canonical.put("ruleSetKey", command.ruleSetKey());
            String json = objectMapper.writeValueAsString(canonical);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to hash idempotency payload", e);
        }
    }

    private String serializeResult(PostTransactionResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize idempotency response", e);
        }
    }

    private PostTransactionResult deserializeResult(String snapshot, boolean replayed) {
        try {
            PostTransactionResult stored = objectMapper.readValue(snapshot, PostTransactionResult.class);
            return new PostTransactionResult(
                    stored.journalEntryId(),
                    stored.tenantId(),
                    stored.type(),
                    stored.transactionReference(),
                    stored.postings(),
                    replayed
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize idempotency response", e);
        }
    }
}
