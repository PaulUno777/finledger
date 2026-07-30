package com.pauluno.finledger.domain.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class AuditHashChainTest {

    @Test
    void should_verify_genesis_and_chained_entries() {
        Instant t0 = Instant.parse("2026-07-30T10:00:00Z");
        Instant t1 = Instant.parse("2026-07-30T10:00:01Z");

        String payload0 = "{\"action\":\"CREATE_TENANT\"}";
        String payloadHash0 = AuditHashChain.payloadHash(payload0);
        String current0 = AuditHashChain.currentHash(
                AuditHashChain.GENESIS_PREV_HASH, payloadHash0, t0, "anonymous");

        String payload1 = "{\"action\":\"POST_TRANSACTION\"}";
        String payloadHash1 = AuditHashChain.payloadHash(payload1);
        String current1 = AuditHashChain.currentHash(current0, payloadHash1, t1, "anonymous");

        List<AuditHashChain.AuditChainLink> links = List.of(
                new AuditHashChain.AuditChainLink(
                        payload0, payloadHash0, AuditHashChain.GENESIS_PREV_HASH, current0, t0, "anonymous"),
                new AuditHashChain.AuditChainLink(
                        payload1, payloadHash1, current0, current1, t1, "anonymous")
        );

        AuditHashChain.AuditChainVerification verification = AuditHashChain.verify(links);
        assertThat(verification.valid()).isTrue();
        assertThat(verification.checkedCount()).isEqualTo(2);
        assertThat(verification.breakAt()).isNull();
    }

    @Test
    void should_detect_tampered_payload() {
        Instant t0 = Instant.parse("2026-07-30T10:00:00Z");
        String payload = "{\"action\":\"POST_TRANSACTION\"}";
        String payloadHash = AuditHashChain.payloadHash(payload);
        String current = AuditHashChain.currentHash(
                AuditHashChain.GENESIS_PREV_HASH, payloadHash, t0, "anonymous");

        List<AuditHashChain.AuditChainLink> links = List.of(
                new AuditHashChain.AuditChainLink(
                        "{\"action\":\"TAMPERED\"}",
                        payloadHash,
                        AuditHashChain.GENESIS_PREV_HASH,
                        current,
                        t0,
                        "anonymous")
        );

        AuditHashChain.AuditChainVerification verification = AuditHashChain.verify(links);
        assertThat(verification.valid()).isFalse();
        assertThat(verification.breakAt()).isEqualTo(0);
    }

    @Test
    void should_detect_broken_prev_hash_link() {
        Instant t0 = Instant.parse("2026-07-30T10:00:00Z");
        Instant t1 = Instant.parse("2026-07-30T10:00:01Z");

        String payload0 = "a";
        String hash0 = AuditHashChain.payloadHash(payload0);
        String current0 = AuditHashChain.currentHash(
                AuditHashChain.GENESIS_PREV_HASH, hash0, t0, "a");

        String payload1 = "b";
        String hash1 = AuditHashChain.payloadHash(payload1);
        String current1 = AuditHashChain.currentHash(current0, hash1, t1, "a");

        List<AuditHashChain.AuditChainLink> links = new ArrayList<>();
        links.add(new AuditHashChain.AuditChainLink(
                payload0, hash0, AuditHashChain.GENESIS_PREV_HASH, current0, t0, "a"));
        links.add(new AuditHashChain.AuditChainLink(
                payload1, hash1, "1".repeat(64), current1, t1, "a"));

        AuditHashChain.AuditChainVerification verification = AuditHashChain.verify(links);
        assertThat(verification.valid()).isFalse();
        assertThat(verification.breakAt()).isEqualTo(1);
    }
}
