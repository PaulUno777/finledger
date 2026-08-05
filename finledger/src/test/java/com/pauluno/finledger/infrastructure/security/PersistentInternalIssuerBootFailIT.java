package com.pauluno.finledger.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

@Tag("integration")
@Testcontainers
@SuppressWarnings("resource")
class PersistentInternalIssuerBootFailIT {

    private static final String[] SYS_PROPS = {
            "spring.profiles.active",
            "finledger.security.issuer",
            "finledger.env",
            "finledger.security.internal.signing-key-pem",
            "finledger.security.internal.signing-key-path",
            "finledger.security.internal.clients[0].client-id",
            "finledger.security.internal.clients[0].client-secret",
            "finledger.security.internal.clients[0].tenant-id",
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
    void should_fail_startup_when_normal_internal_missing_signing_key() {
        Map<String, Object> props = baseProps();
        System.setProperty("spring.profiles.active", "normal");
        System.setProperty("finledger.security.issuer", "internal");
        System.setProperty("finledger.security.internal.signing-key-pem", "");
        System.setProperty("finledger.security.internal.signing-key-path", "");
        System.setProperty("finledger.security.internal.clients[0].client-id", "ci");
        System.setProperty("finledger.security.internal.clients[0].client-secret", "secret");
        System.setProperty(
                "finledger.security.internal.clients[0].tenant-id",
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa").toString());

        SpringApplication app = new SpringApplication(FinledgerApplication.class);
        app.setDefaultProperties(props);
        app.setWebApplicationType(WebApplicationType.SERVLET);

        assertThatThrownBy(app::run)
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("signing-key");
    }

    @Test
    void should_fail_startup_when_normal_internal_missing_clients() {
        Map<String, Object> props = baseProps();
        System.setProperty("spring.profiles.active", "normal");
        System.setProperty("finledger.security.issuer", "internal");
        System.setProperty("finledger.security.internal.signing-key-pem", minimalPkcs8Pem());

        SpringApplication app = new SpringApplication(FinledgerApplication.class);
        app.setDefaultProperties(props);
        app.setWebApplicationType(WebApplicationType.SERVLET);

        assertThatThrownBy(app::run)
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clients");
    }

    private static Map<String, Object> baseProps() {
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
        props.put("finledger.env", "local");
        props.put("server.port", "0");
        props.put("management.server.port", "0");
        return props;
    }

    private static String minimalPkcs8Pem() {
        try {
            var key = new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048).generate();
            String encoded = java.util.Base64.getMimeEncoder(64, new byte[]{'\n'})
                    .encodeToString(key.toRSAPrivateKey().getEncoded());
            return "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n";
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
