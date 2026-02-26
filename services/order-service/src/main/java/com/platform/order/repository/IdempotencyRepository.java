package com.platform.order.repository;

import com.platform.order.entity.IdempotencyKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRepository extends JpaRepository<IdempotencyKeyEntity, String> {
}
