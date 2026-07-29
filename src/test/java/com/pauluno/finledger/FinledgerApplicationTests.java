package com.pauluno.finledger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@SuppressWarnings("resource") // containers closed by Testcontainers @Container / Ryuk
class FinledgerApplicationTests {

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
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
		registry.add("spring.flyway.enabled", () -> "true");
		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379).toString());
		registry.add("spring.autoconfigure.exclude",
				() -> "org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration");
	}

	@Test
	void contextLoads() {
	}

}
