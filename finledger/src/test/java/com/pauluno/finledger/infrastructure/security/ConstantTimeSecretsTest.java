package com.pauluno.finledger.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class ConstantTimeSecretsTest {

    @Test
    void should_match_equal_secrets() {
        assertThat(ConstantTimeSecrets.equals("bootstrap-secret", "bootstrap-secret")).isTrue();
    }

    @Test
    void should_reject_mismatched_secrets() {
        assertThat(ConstantTimeSecrets.equals("bootstrap-secret", "wrong")).isFalse();
        assertThat(ConstantTimeSecrets.equals("bootstrap-secret", null)).isFalse();
        assertThat(ConstantTimeSecrets.equals(null, "x")).isFalse();
    }

    @Test
    void sha256_is_stable() {
        byte[] a = ConstantTimeSecrets.sha256("same");
        byte[] b = ConstantTimeSecrets.sha256("same");
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSize(32);
    }
}
