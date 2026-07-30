package com.pauluno.finledger.domain.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Pure hash-chain helpers for the append-only audit trail (plan §10).
 */
public final class AuditHashChain {

    public static final String GENESIS_PREV_HASH = "0".repeat(64);

    private AuditHashChain() {
    }

    public static String payloadHash(String payload) {
        return sha256(Objects.requireNonNull(payload, "payload"));
    }

    /**
     * {@code current_hash = SHA256(prev_hash + payload_hash + timestamp + actor)}.
     */
    public static String currentHash(
            String prevHash,
            String payloadHash,
            Instant occurredAt,
            String actor
    ) {
        Objects.requireNonNull(prevHash, "prevHash");
        Objects.requireNonNull(payloadHash, "payloadHash");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(actor, "actor");
        return sha256(prevHash + payloadHash + occurredAt.toString() + actor);
    }

    public static AuditChainVerification verify(List<AuditChainLink> links) {
        Objects.requireNonNull(links, "links");
        String expectedPrev = GENESIS_PREV_HASH;
        for (int i = 0; i < links.size(); i++) {
            AuditChainLink link = links.get(i);
            if (!expectedPrev.equals(link.prevHash())) {
                return AuditChainVerification.broken(i, links.size());
            }
            String expectedPayloadHash = payloadHash(link.payload());
            if (!expectedPayloadHash.equals(link.payloadHash())) {
                return AuditChainVerification.broken(i, links.size());
            }
            String expectedCurrent = currentHash(
                    link.prevHash(), link.payloadHash(), link.occurredAt(), link.actor());
            if (!expectedCurrent.equals(link.currentHash())) {
                return AuditChainVerification.broken(i, links.size());
            }
            expectedPrev = link.currentHash();
        }
        return AuditChainVerification.valid(links.size());
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record AuditChainLink(
            String payload,
            String payloadHash,
            String prevHash,
            String currentHash,
            Instant occurredAt,
            String actor
    ) {
    }

    public record AuditChainVerification(boolean valid, int checkedCount, Integer breakAt) {
        public static AuditChainVerification valid(int checkedCount) {
            return new AuditChainVerification(true, checkedCount, null);
        }

        public static AuditChainVerification broken(int breakAt, int checkedCount) {
            return new AuditChainVerification(false, checkedCount, breakAt);
        }
    }
}
