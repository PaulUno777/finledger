package com.pauluno.finledger.infrastructure.persistence.jpa.mapper;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import com.pauluno.finledger.domain.model.IdempotencyKey;
import com.pauluno.finledger.domain.model.JournalEntry;
import com.pauluno.finledger.domain.model.JournalEntryType;
import com.pauluno.finledger.domain.model.Money;
import com.pauluno.finledger.domain.model.Posting;
import com.pauluno.finledger.domain.model.RateSource;
import com.pauluno.finledger.domain.model.SettlementStatus;
import com.pauluno.finledger.domain.model.TransactionReference;
import com.pauluno.finledger.infrastructure.persistence.jpa.entity.JournalEntryEntity;
import com.pauluno.finledger.infrastructure.persistence.jpa.entity.PostingEntity;

public final class JournalEntryMapper {

    private JournalEntryMapper() {
    }

    public static JournalEntryEntity toEntity(JournalEntry entry) {
        JournalEntryEntity entity = new JournalEntryEntity();
        entity.setId(entry.id());
        entity.setTenantId(entry.tenantId());
        entity.setIdempotencyKey(entry.idempotencyKey().value());
        entity.setTransactionReference(entry.transactionReference().value());
        entity.setEntryType(entry.type().name());
        entity.setOccurredAt(entry.occurredAt());
        entity.setReversesEntryId(entry.reversesEntryId().orElse(null));
        entity.setRateUsed(entry.rateUsed().orElse(null));
        entity.setRateSource(entry.rateSource().map(Enum::name).orElse(null));
        entity.setRateTimestamp(entry.rateTimestamp().orElse(null));

        int lineNo = 0;
        for (Posting posting : entry.postings()) {
            PostingEntity postingEntity = new PostingEntity();
            postingEntity.setId(UUID.randomUUID());
            postingEntity.setAccountId(posting.accountId());
            postingEntity.setAmount(posting.amount().amount());
            postingEntity.setCurrency(posting.amount().currency().getCurrencyCode());
            postingEntity.setSettlementStatus(posting.settlementStatus().name());
            postingEntity.setLineNo(lineNo++);
            entity.addPosting(postingEntity);
        }
        return entity;
    }

    public static JournalEntry toDomain(JournalEntryEntity entity) {
        List<Posting> postings = new ArrayList<>(entity.getPostings().size());
        for (PostingEntity postingEntity : entity.getPostings()) {
            Currency currency = Currency.getInstance(postingEntity.getCurrency());
            postings.add(new Posting(
                    postingEntity.getAccountId(),
                    Money.of(postingEntity.getAmount(), currency),
                    SettlementStatus.valueOf(postingEntity.getSettlementStatus())
            ));
        }
        RateSource rateSource = entity.getRateSource() == null
                ? null
                : RateSource.valueOf(entity.getRateSource());
        return JournalEntry.reconstitute(
                entity.getId(),
                entity.getTenantId(),
                new IdempotencyKey(entity.getIdempotencyKey()),
                new TransactionReference(entity.getTransactionReference()),
                JournalEntryType.valueOf(entity.getEntryType()),
                postings,
                entity.getOccurredAt(),
                entity.getReversesEntryId(),
                entity.getRateUsed(),
                rateSource,
                entity.getRateTimestamp()
        );
    }
}
