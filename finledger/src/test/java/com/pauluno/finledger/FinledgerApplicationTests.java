package com.pauluno.finledger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.pauluno.finledger.support.IntegrationTestSecurityConfig;

@SpringBootTest
@Import(IntegrationTestSecurityConfig.class)
@Testcontainers
@SuppressWarnings("resource") // containers closed by Testcontainers @Container / Ryuk
class FinledgerApplicationTests {

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
			DockerImageName.parse("postgres:17-alpine"))
			.withDatabaseName("finledger")
			.withUsername("finledger")
			.withPassword("finledger");

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", () -> "finledger_app");
		registry.add("spring.datasource.password", () -> "finledger");
		registry.add("spring.flyway.user", POSTGRES::getUsername);
		registry.add("spring.flyway.password", POSTGRES::getPassword);
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
		registry.add("spring.flyway.enabled", () -> "true");
		registry.add("finledger.outbox.poll-interval-ms", () -> "3600000");
	}

	@Test
	void contextLoads() {
	}

}
