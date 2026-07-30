package com.pauluno.finledger.infrastructure.boot;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import com.pauluno.finledger.infrastructure.persistence.jpa.repository.SpringDataTenantRepository;

@Tag("unit")
class NoTenantStartupHintTest {

    @Test
    void should_not_throw_when_repository_count_is_zero() {
        SpringDataTenantRepository repo = mock(SpringDataTenantRepository.class);
        when(repo.count()).thenReturn(0L);
        NoTenantStartupHint hint = new NoTenantStartupHint(repo);
        assertDoesNotThrow(() -> hint.run(new DefaultApplicationArguments()));
    }

    @Test
    void should_not_throw_when_repository_fails() {
        SpringDataTenantRepository repo = mock(SpringDataTenantRepository.class);
        when(repo.count()).thenThrow(new RuntimeException("db down"));
        NoTenantStartupHint hint = new NoTenantStartupHint(repo);
        assertDoesNotThrow(() -> hint.run(new DefaultApplicationArguments()));
    }
}
