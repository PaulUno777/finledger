package com.pauluno.finledger.application.usecase;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
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
import com.pauluno.finledger.application.audit.Auditable;
import com.pauluno.finledger.application.dto.PostTransactionResult;
import com.pauluno.finledger.application.dto.RefundTransactionCommand;
import com.pauluno.finledger.application.event.TransactionPosted;
import com.pauluno.finledger.application.exception.BusinessRuleException;
import com.pauluno.finledger.application.exception.ResourceNotFoundException;
import com.pauluno.finledger.application.port.in.RefundTransactionUseCase;
import com.pauluno.finledger.application.port.out.AccountBalanceRepository;
import com.pauluno.finledger.application.port.out.IdempotencyStore;
import com.pauluno.finledger.application.port.out.JournalEntryRepository;
import com.pauluno.finledger.application.port.out.LedgerAccountRepository;
import com.pauluno.finledger.application.port.out.OutboxWriter;
import com.pauluno.finledger.application.port.out.TenantFeeConfigRepository;
import com.pauluno.finledger.domain.exception.AccountClosedException;
import com.pauluno.finledger.domain.exception.CurrencyMismatchException;
import com.pauluno.finledger.domain.exception.InsufficientFundsException;
import com.pauluno.finledger.domain.exception.InvalidJournalEntryException;
import com.pauluno.finledger.domain.model.AccountBalance;
import com.pauluno.finledger.domain.model.FeeReversalPolicy;
import com.pauluno.finledger.domain.model.FeeReversalPolicyType;
import com.pauluno.finledger.domain.model.IdempotencyKey;
import com.pauluno.finledger.domain.model.JournalEntry;
import com.pauluno.finledger.domain.model.LedgerAccount;
import com.pauluno.finledger.domain.model.Money;
import com.pauluno.finledger.domain.model.Posting;
import com.pauluno.finledger.domain.model.TransactionReference;
import com.pauluno.finledger.domain.service.NoReverseFeePolicy;
import com.pauluno.finledger.domain.service.ProRataFeePolicy;

@Service
public class RefundTransactionService implements RefundTransactionUseCase {

    private static final int MAX_OPTIMISTIC_RETRIES = 3;

    private final IdempotencyStore idempotencyStore;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final AccountBalanceRepository accountBalanceRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final OutboxWriter outboxWriter;
    private final TenantFeeConfigRepository tenantFeeConfigRepository;
    private final ObjectMapper objectMapper;

    public RefundTransactionService(
            IdempotencyStore idempotencyStore,
            LedgerAccountRepository ledgerAccountRepository,
            AccountBalanceRepository accountBalanceRepository,
            JournalEntryRepository journalEntryRepository,
            OutboxWriter outboxWriter,
            TenantFeeConfigRepository tenantFeeConfigRepository) {
        this.idempotencyStore = idempotencyStore;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.accountBalanceRepository = accountBalanceRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.outboxWriter = outboxWriter;
        this.tenantFeeConfigRepository = tenantFeeConfigRepository;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    @Override
    @Transactional
    @Auditable(action = "REFUND_TRANSACTION", resourceType = "JOURNAL_ENTRY")
    public PostTransactionResult execute(RefundTransactionCommand command) {
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

    private PostTransactionResult executeWithRetry(RefundTransactionCommand command, IdempotencyKey key) {
        OptimisticLockingFailureException last = null;
        for (int attempt = 1; attempt <= MAX_OPTIMISTIC_RETRIES; attempt++) {
            try {
                return refundOnce(command, key);
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

    private PostTransactionResult refundOnce(RefundTransactionCommand command, IdempotencyKey key) {
        try {
            JournalEntry original = journalEntryRepository.findById(command.originalJournalEntryId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Journal entry not found: " + command.originalJournalEntryId()));
            if (!original.tenantId().equals(command.tenantId())) {
                throw new ResourceNotFoundException(
                        "Journal entry not found: " + command.originalJournalEntryId());
            }

            Money refundAmount = Money.of(
                    command.refundAmount(),
                    Currency.getInstance(command.currencyCode()));
            FeeReversalPolicyType policyType = tenantFeeConfigRepository
                    .findByTenantId(command.tenantId())
                    .orElse(FeeReversalPolicyType.NO_REVERSE);
            FeeReversalPolicy policy = switch (policyType) {
                case NO_REVERSE -> new NoReverseFeePolicy();
                case PRO_RATA -> new ProRataFeePolicy();
            };

            List<UUID> accountIds = new ArrayList<>();
            for (Posting posting : original.postings()) {
                accountIds.add(posting.accountId());
            }

            Map<UUID, LedgerAccount> accounts = new HashMap<>();
            for (UUID accountId : accountIds) {
                LedgerAccount account = ledgerAccountRepository
                        .findByIdForTenant(accountId, command.tenantId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Account not found for tenant: " + accountId));
                accounts.put(account.id(), account);
            }

            List<Posting> refundPostings = policy.calculateReversal(original, refundAmount, accounts);
            Map<UUID, AccountBalance> balances = accountBalanceRepository.findByAccountIds(accountIds);

            JournalEntry entry = JournalEntry.createRefund(
                    command.tenantId(),
                    key,
                    new TransactionReference(command.transactionReference()),
                    refundPostings,
                    accounts,
                    balances,
                    Instant.now(),
                    original.id());
            JournalEntry saved = journalEntryRepository.save(entry);
            appendOutbox(saved);
            return PostTransactionService.toResult(saved, false);
        } catch (InvalidJournalEntryException
                | CurrencyMismatchException
                | InsufficientFundsException
                | AccountClosedException
                | IllegalArgumentException
                | IllegalStateException ex) {
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
                                p.settlementStatus().name()))
                        .toList(),
                saved.occurredAt());
        try {
            outboxWriter.append(new OutboxWriter.OutboxMessage(
                    saved.tenantId(),
                    saved.id(),
                    TransactionPosted.EVENT_TYPE,
                    objectMapper.writeValueAsString(event)));
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

    private String hashRequest(RefundTransactionCommand command) {
        try {
            Map<String, Object> canonical = new HashMap<>();
            canonical.put("tenantId", command.tenantId().toString());
            canonical.put("transactionReference", command.transactionReference());
            canonical.put("originalJournalEntryId", command.originalJournalEntryId().toString());
            canonical.put("refundAmount", command.refundAmount());
            canonical.put("currencyCode", command.currencyCode());
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
                    replayed);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize idempotency response", e);
        }
    }
}
