package com.platform.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.order.dto.CreateOrderRequest;
import com.platform.order.dto.OrderItemRequest;
import com.platform.order.dto.OrderResponse;
import com.platform.order.outbox.OutboxEvent;
import com.platform.order.outbox.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class OrderServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutboxRepository outboxRepository;

    @Test
    void shouldCreateOrderAndWriteOutboxEvent() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                UUID.randomUUID(),
                List.of(new OrderItemRequest(
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        2,
                        new BigDecimal("29.99")
                )),
                "EUR"
        );

        String idempotencyKey = "test-" + UUID.randomUUID();

        MvcResult result = mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(59.98))
                .andReturn();

        OrderResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), OrderResponse.class);

        // Verify order persisted
        mockMvc.perform(get("/api/orders/" + response.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(response.id().toString()));

        // Verify outbox event created
        List<OutboxEvent> outboxEvents = outboxRepository.findByPublishedFalseOrderByCreatedAtAsc();
        assertThat(outboxEvents).anyMatch(e ->
                e.getEventType().equals("OrderCreated") && e.getAggregateId().equals(response.id()));
    }

    @Test
    void shouldReturnCachedResponseForDuplicateIdempotencyKey() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                UUID.randomUUID(),
                List.of(new OrderItemRequest(
                        UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        1,
                        new BigDecimal("49.99")
                )),
                "EUR"
        );

        String idempotencyKey = "idempotent-" + UUID.randomUUID();

        // First call
        MvcResult first = mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        // Second call with same key and payload — returns 200 OK (cache hit)
        MvcResult second = mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        // Should return same response
        assertThat(first.getResponse().getContentAsString())
                .isEqualTo(second.getResponse().getContentAsString());
    }

    @Test
    void shouldReturn409ForSameKeyDifferentPayload() throws Exception {
        String idempotencyKey = "conflict-" + UUID.randomUUID();

        CreateOrderRequest request1 = new CreateOrderRequest(
                UUID.randomUUID(),
                List.of(new OrderItemRequest(
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        1,
                        new BigDecimal("10.00")
                )),
                "EUR"
        );

        CreateOrderRequest request2 = new CreateOrderRequest(
                UUID.randomUUID(),
                List.of(new OrderItemRequest(
                        UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        5,
                        new BigDecimal("99.99")
                )),
                "EUR"
        );

        // First call succeeds
        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        // Second call with different payload → 409
        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict());
    }
}
