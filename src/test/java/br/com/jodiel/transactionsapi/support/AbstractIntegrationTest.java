package br.com.jodiel.transactionsapi.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Boots the whole application against real Postgres, Redis and RabbitMQ instances. Anything that
 * depends on the actual database dialect — the uuid column mapping, the Flyway migrations, the
 * SELECT ... FOR UPDATE lock — can only be verified here, not with mocks.
 *
 * <p>Uses the singleton container pattern: the containers are started once in a static initialiser
 * and live for the whole JVM, with Testcontainers' Ryuk reaping them at the end.
 *
 * <p>Deliberately <em>not</em> annotated with {@code @Testcontainers}. That extension stops
 * {@code @Container} fields when each test class finishes, but Spring caches application contexts
 * across classes — so the second class would keep running against containers that had already been
 * torn down, and Lettuce's reconnect watchdog would spin forever on non-daemon threads, leaving the
 * test JVM unable to exit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);

    static final GenericContainer<?> RABBITMQ =
            new GenericContainer<>(DockerImageName.parse("rabbitmq:4-alpine")).withExposedPorts(5672);

    static {
        POSTGRES.start();
        REDIS.start();
        RABBITMQ.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        registry.add("spring.rabbitmq.addresses",
                () -> "amqp://" + RABBITMQ.getHost() + ":" + RABBITMQ.getMappedPort(5672));

        registry.add("app.jwt.secret", () -> "integration-test-secret-at-least-32-chars");
    }
}
