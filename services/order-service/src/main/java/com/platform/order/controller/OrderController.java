package com.platform.order.controller;

import com.platform.order.dto.CreateOrderRequest;
import com.platform.order.dto.CreateOrderResult;
import com.platform.order.dto.OrderResponse;
import com.platform.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required");
        }
        CreateOrderResult result = orderService.createOrder(request, idempotencyKey);
        HttpStatus status = result.fromCache() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result.response());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.getOrder(id));
    }
}
