package com.platform.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.inventory.InventoryServiceApplication;
import com.platform.inventory.entity.Product;
import com.platform.inventory.repository.ProductRepository;
import com.platform.order.OrderServiceApplication;
import com.platform.payment.PaymentServiceApplication;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end saga test that boots order-service, inventory-service, and
 * payment-service in the same JVM sharing Testcontainers for Kafka and
 * PostgreSQL. Validates the full choreography for both the happy path
 * (order confirmed) and the failure path (payment rejected, order cancelled).
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SagaEndToEndTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("platform")
            .withUsername("platform")
            .withPassword("platform");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    static ConfigurableApplicationContext orderCtx;
    static ConfigurableApplicationContext inventoryCtx;
    static ConfigurableApplicationContext paymentCtx;

    static RestClient restClient;
    static final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void startServices() throws Exception {
        createDatabases();

        String kafkaServers = kafka.getBootstrapServers();
        String jdbcBase = String.format("jdbc:postgresql://%s:%d",
                postgres.getHost(), postgres.getMappedPort(5432));

        // Start inventory first (needs seed data)
        inventoryCtx = startService(
                InventoryServiceApplication.class, "inventory-service",
                jdbcBase + "/inventorydb", kafkaServers, "inventory-service", "");

        // Seed product catalog (Flyway is disabled, so no V2__seed_products runs)
        ProductRepository productRepo = inventoryCtx.getBean(ProductRepository.class);
        productRepo.save(new Product(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "Wireless Mouse", 100));

        // Start payment with forced success for happy-path test
        paymentCtx = startService(
                PaymentServiceApplication.class, "payment-service",
                jdbcBase + "/paymentdb", kafkaServers, "payment-service", "success");

        // Start order service last
        orderCtx = startService(
                OrderServiceApplication.class, "order-service",
                jdbcBase + "/orderdb", kafkaServers, "order-service", "");

        int orderPort = orderCtx.getEnvironment()
                .getProperty("local.server.port", Integer.class);
        restClient = RestClient.builder()
                .baseUrl("http://localhost:" + orderPort)
                .build();
    }

    @AfterAll
    static void stopServices() {
        if (orderCtx != null) orderCtx.close();
        if (paymentCtx != null) paymentCtx.close();
        if (inventoryCtx != null) inventoryCtx.close();
    }

    @Test
    @Order(1)
    void happyPath_orderIsConfirmed() throws Exception {
        String body = """
                {
                    "customerId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                    "items": [{
                        "productId": "11111111-1111-1111-1111-111111111111",
                        "quantity": 2,
                        "unitPrice": 29.99
                    }],
                    "currency": "EUR"
                }
                """;

        String response = restClient.post()
                .uri("/api/orders")
                .header("Idempotency-Key", "e2e-happy-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        JsonNode created = objectMapper.readTree(response);
        String orderId = created.get("id").asText();
        assertThat(created.get("status").asText()).isEqualTo("PENDING");

        // Wait for the full saga: OrderCreated -> StockReserved -> PaymentSucceeded -> CONFIRMED
        await().atMost(30, SECONDS).pollInterval(1, SECONDS).untilAsserted(() -> {
            String orderJson = restClient.get()
                    .uri("/api/orders/" + orderId)
                    .retrieve()
                    .body(String.class);
            JsonNode order = objectMapper.readTree(orderJson);
            assertThat(order.get("status").asText()).isEqualTo("CONFIRMED");
        });
    }

    @Test
    @Order(2)
    void failurePath_paymentFails_orderIsCancelled() throws Exception {
        // Restart payment service with forced failure
        paymentCtx.close();
        String kafkaServers = kafka.getBootstrapServers();
        String jdbcBase = String.format("jdbc:postgresql://%s:%d",
                postgres.getHost(), postgres.getMappedPort(5432));
        paymentCtx = startService(
                PaymentServiceApplication.class, "payment-service",
                jdbcBase + "/paymentdb", kafkaServers, "payment-service", "failure");

        String body = """
                {
                    "customerId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                    "items": [{
                        "productId": "11111111-1111-1111-1111-111111111111",
                        "quantity": 1,
                        "unitPrice": 9.99
                    }],
                    "currency": "EUR"
                }
                """;

        String response = restClient.post()
                .uri("/api/orders")
                .header("Idempotency-Key", "e2e-fail-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        JsonNode created = objectMapper.readTree(response);
        String orderId = created.get("id").asText();
        assertThat(created.get("status").asText()).isEqualTo("PENDING");

        // Wait for saga failure: OrderCreated -> StockReserved -> PaymentFailed -> CANCELLED
        await().atMost(30, SECONDS).pollInterval(1, SECONDS).untilAsserted(() -> {
            String orderJson = restClient.get()
                    .uri("/api/orders/" + orderId)
                    .retrieve()
                    .body(String.class);
            JsonNode order = objectMapper.readTree(orderJson);
            assertThat(order.get("status").asText()).isEqualTo("CANCELLED");
        });
    }

    /**
     * Create per-service databases in the shared PostgreSQL container.
     * Must use autocommit because CREATE DATABASE cannot run inside a transaction.
     */
    private static void createDatabases() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            conn.setAutoCommit(true);
            conn.createStatement().execute("CREATE DATABASE orderdb");
            conn.createStatement().execute("CREATE DATABASE paymentdb");
            conn.createStatement().execute("CREATE DATABASE inventorydb");
        }
    }

    /**
     * Start a Spring Boot service with explicit configuration.
     * Uses command-line args (highest precedence) to override application.yml.
     */
    private static ConfigurableApplicationContext startService(
            Class<?> appClass, String appName, String dbUrl,
            String kafkaServers, String consumerGroup, String forceOutcome) {

        return new SpringApplicationBuilder(appClass)
                .run(
                        "--server.port=0",
                        "--spring.application.name=" + appName,
                        "--spring.datasource.url=" + dbUrl,
                        "--spring.datasource.username=platform",
                        "--spring.datasource.password=platform",
                        "--spring.jpa.hibernate.ddl-auto=update",
                        "--spring.flyway.enabled=false",
                        "--spring.kafka.bootstrap-servers=" + kafkaServers,
                        "--spring.kafka.consumer.group-id=" + consumerGroup,
                        "--spring.kafka.consumer.auto-offset-reset=earliest",
                        "--spring.kafka.consumer.enable-auto-commit=false",
                        "--spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
                        "--spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer",
                        "--spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
                        "--spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
                        "--spring.kafka.listener.ack-mode=record",
                        "--management.otlp.tracing.export.enabled=false",
                        "--management.tracing.sampling.probability=0.0",
                        "--payment.simulate.force-outcome=" + forceOutcome
                );
    }
}
