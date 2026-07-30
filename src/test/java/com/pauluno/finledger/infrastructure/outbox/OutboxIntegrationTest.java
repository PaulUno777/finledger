package com.pauluno.finledger.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pauluno.finledger.application.event.TransactionPosted;
import com.pauluno.finledger.application.port.out.EventPublisher;
import com.pauluno.finledger.application.port.out.OutboxEventRepository;
import com.pauluno.finledger.application.tenant.TenantContext;
import com.pauluno.finledger.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import com.pauluno.finledger.infrastructure.persistence.jpa.repository.SpringDataOutboxEventRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Tag("integration")
@SuppressWarnings("resource")
class OutboxIntegrationTest {

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

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "finledger_app");
        registry.add("spring.datasource.password", () -> "finledger");
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379).toString());
        registry.add("finledger.outbox.poll-interval-ms", () -> "3600000");
        registry.add("spring.autoconfigure.exclude",
                () -> "org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration");
    }

    @TestConfiguration
    static class RecordingPublisherConfig {
        @Bean
        @Primary
        EventPublisher recordingEventPublisher() {
            return new RecordingEventPublisher();
        }
    }

    static final class RecordingEventPublisher implements EventPublisher {
        private final List<PublishedEvent> published = new CopyOnWriteArrayList<>();

        @Override
        public void publish(PublishedEvent event) {
            published.add(event);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private SpringDataOutboxEventRepository springDataOutboxEventRepository;

    @Autowired
    private OutboxPoller outboxPoller;

    @Autowired
    private EventPublisher eventPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        if (eventPublisher instanceof RecordingEventPublisher recording) {
            recording.published.clear();
        }
    }

    @Test
    void should_write_outbox_row_with_journal_and_publish_via_poller() throws Exception {
        UUID tenantId = createTenant("outbox-shop");
        UUID fromId = createAccount(tenantId, "merchant-a");
        UUID toId = createAccount(tenantId, "merchant-b");

        String body = """
                {
                  "transactionReference": "tx-outbox-1",
                  "postings": [
                    {"accountId": "%s", "amount": "-12.00", "currencyCode": "USD", "settlementStatus": "SETTLED"},
                    {"accountId": "%s", "amount": "12.00", "currencyCode": "USD", "settlementStatus": "SETTLED"}
                  ]
                }
                """.formatted(fromId, toId);

        MvcResult created = mockMvc.perform(post("/api/v1/tenants/{tenantId}/journal-entries", tenantId)
                        .header("Idempotency-Key", "outbox-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.journalEntryId").isNotEmpty())
                .andReturn();

        UUID journalEntryId = UUID.fromString(
                objectMapper.readTree(created.getResponse().getContentAsString())
                        .get("journalEntryId").asText());

        List<OutboxEventEntity> rows;
        TenantContext.set(tenantId);
        try {
            rows = springDataOutboxEventRepository.findAll();
        } finally {
            TenantContext.clear();
        }
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getAggregateId()).isEqualTo(journalEntryId);
        assertThat(rows.getFirst().getEventType()).isEqualTo(TransactionPosted.EVENT_TYPE);
        assertThat(rows.getFirst().getStatus()).isEqualTo(OutboxEventRepository.OutboxStatus.PENDING.name());
        assertThat(rows.getFirst().getPayload()).contains(journalEntryId.toString());

        TenantContext.enableBypass();
        try {
            outboxPoller.poll();
        } finally {
            TenantContext.clear();
        }

        RecordingEventPublisher recording = (RecordingEventPublisher) eventPublisher;
        assertThat(recording.published).hasSize(1);
        assertThat(recording.published.getFirst().eventType()).isEqualTo(TransactionPosted.EVENT_TYPE);
        assertThat(recording.published.getFirst().aggregateId()).isEqualTo(journalEntryId);

        TenantContext.enableBypass();
        try {
            OutboxEventEntity published = springDataOutboxEventRepository.findById(rows.getFirst().getId()).orElseThrow();
            assertThat(published.getStatus()).isEqualTo(OutboxEventRepository.OutboxStatus.PUBLISHED.name());
            assertThat(published.getPublishedAt()).isNotNull();
            assertThat(outboxEventRepository.claimPending(10)).isEmpty();
        } finally {
            TenantContext.clear();
        }
    }

    private UUID createTenant(String name) throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "name", name,
                "type", "STANDALONE"
        ));
        MvcResult result = mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("tenantId").asText());
    }

    private UUID createAccount(UUID tenantId, String ownerRef) throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "ownerRef", ownerRef,
                "currencyCode", "USD",
                "type", "MERCHANT_WALLET",
                "allowsOverdraft", true
        ));
        MvcResult result = mockMvc.perform(post("/api/v1/tenants/{tenantId}/accounts", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(json.get("accountId").asText());
    }
}
