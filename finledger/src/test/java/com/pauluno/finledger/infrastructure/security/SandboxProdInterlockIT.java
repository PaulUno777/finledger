package com.pauluno.finledger.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.pauluno.finledger.FinledgerApplication;
import com.pauluno.finledger.security.policy.RuntimeSecurityViolationException;

@Tag("integration")
@Testcontainers
@SuppressWarnings("resource")
class SandboxProdInterlockIT {

    private static final String[] SYS_PROPS = {
            "spring.profiles.active",
            "finledger.security.issuer",
            "finledger.env",
            "finledger.sandbox.dump-path",
            "finledger.sandbox.client-secret",
            "server.port",
            "management.server.port"
    };

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("finledger")
            .withUsername("finledger")
            .withPassword("finledger");

    @AfterEach
    void clearSystemProperties() {
        for (String key : SYS_PROPS) {
            System.clearProperty(key);
        }
    }

    @Test
    void should_fail_startup_when_sandbox_with_production_env() throws Exception {
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
        props.put("finledger.outbox.poll-interval-ms", "3600000");

        System.setProperty("spring.profiles.active", "sandbox");
        System.setProperty("finledger.security.issuer", "internal");
        System.setProperty("finledger.env", "production");
        System.setProperty("server.port", "0");
        System.setProperty("management.server.port", "0");
        System.setProperty(
                "finledger.sandbox.dump-path",
                java.nio.file.Files.createTempFile("sandbox-ready-", ".txt").toString());
        System.setProperty("finledger.sandbox.client-secret", "it-secret");

        SpringApplication app = new SpringApplication(FinledgerApplication.class);
        app.setDefaultProperties(props);
        app.setWebApplicationType(WebApplicationType.SERVLET);

        assertThatThrownBy(app::run)
                .isInstanceOf(RuntimeSecurityViolationException.class);
    }
}
