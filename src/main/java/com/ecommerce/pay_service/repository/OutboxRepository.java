package com.ecommerce.pay_service.repository;

import com.ecommerce.pay_service.entity.OutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OutboxRepository extends JpaRepository<OutboxEntity, Long> {
    List<OutboxEntity> findByProcessedFalse();
    Optional<OutboxEntity> findByAggregateId(String aggregateId);
}
