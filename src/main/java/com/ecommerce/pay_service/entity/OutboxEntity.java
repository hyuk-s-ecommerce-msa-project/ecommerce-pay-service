package com.ecommerce.pay_service.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbox")
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class OutboxEntity {
    @Id
    private Long id;

    @Column(name = "aggregateid")
    private String aggregateId;
    @Column(name = "aggregatetype")
    private String aggregateType;
    @Column(name = "type")
    private String eventType;

    @Column(columnDefinition = "json", nullable = false)
    private String payload;

    private boolean processed = false;

    @CreatedDate
    private LocalDateTime createdAt;

    public void markProcessed() { this.processed = true; }
}
