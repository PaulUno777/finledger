package com.pauluno.finledger.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.pauluno.finledger.FinledgerApplication;
import com.pauluno.finledger.security.policy.SecurityModeViolationException;

@Tag("integration")
@Testcontainers
@SuppressWarnings("resource")
class SecurityModeProdInterlockIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("finledger")
            .withUsername("finledger")
            .withPassword("finledger");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379);

    @Test
    void should_fail_startup_when_disabled_with_prod_profile() throws Exception {
        Map<String, Object> props = new HashMap<>();
        props.put("spring.datasource.url", POSTGRES.getJdbcUrl());
        props.put("spring.datasource.username", "finledger_app");
        props.put("spring.datasource.password", "finledger");
        props.put("DB_URL", POSTGRES.getJdbcUrl());
        props.put("DB_USERNAME", "finledger_app");
        props.put("DB_PASSWORD", "finledger");
        props.put("spring.flyway.user", POSTGRES.getUsername());
        props.put("spring.flyway.password", POSTGRES.getPassword());
        props.put("spring.jpa.hibernate.ddl-auto", "validate");
        props.put("spring.flyway.enabled", "true");
        props.put("spring.data.redis.host", REDIS.getHost());
        props.put("spring.data.redis.port", String.valueOf(REDIS.getMappedPort(6379)));
        props.put("REDIS_HOST", REDIS.getHost());
        props.put("REDIS_PORT", String.valueOf(REDIS.getMappedPort(6379)));
        props.put("finledger.outbox.poll-interval-ms", "3600000");
        props.put("finledger.security.mode", "disabled");
        props.put("finledger.env", "local");
        props.put("spring.profiles.active", "prod");
        props.put("server.port", "0");
        props.put("management.server.port", "0");
        props.put("finledger.security.warn-interval-ms", "3600000");
        props.put("finledger.sandbox.dump-path",
                java.nio.file.Files.createTempFile("sandbox-ready-", ".txt").toString());

        SpringApplication app = new SpringApplication(FinledgerApplication.class);
        app.setDefaultProperties(props);
        app.setWebApplicationType(WebApplicationType.SERVLET);

        assertThatThrownBy(app::run)
                .hasRootCauseInstanceOf(SecurityModeViolationException.class);
    }
}
