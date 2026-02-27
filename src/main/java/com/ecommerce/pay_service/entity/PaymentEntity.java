package com.ecommerce.pay_service.entity;

import com.ecommerce.pay_service.entity.enums.PaymentStatus;
import com.ecommerce.pay_service.entity.enums.PaymentType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Table(name = "payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class PaymentEntity {
    @Id
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private Integer totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentItemEntity> paymentItems = new ArrayList<>();

    @Column(nullable = false)
    private String tid;

    @CreatedDate
    private LocalDateTime createdAt;

    public static PaymentEntity createPayment(Long id, String orderId, String userId, PaymentType paymentType) {
        PaymentEntity payment = new PaymentEntity();

        payment.id = id;
        payment.orderId = orderId;
        payment.userId = userId;
        payment.paymentType = paymentType;
        payment.status = PaymentStatus.READY;
        payment.totalAmount = 0;
        payment.createdAt = LocalDateTime.now();

        return payment;
    }

    public void addPaymentItem(Long id, String productId, Integer unitPrice, Integer qty) {
        PaymentItemEntity item = PaymentItemEntity.createPaymentItem(id, productId, unitPrice, qty, this);

        this.paymentItems.add(item);
        this.totalAmount += (unitPrice * qty); // 상품이 추가될 때마다 총액 갱신
    }

    public void updateTid(String tid) {
        this.tid = tid;
    }

    public void completePayment() {
        this.status = PaymentStatus.COMPLETED;
    }

    public void failPayment() {
        this.status = PaymentStatus.FAILED;
    }

    public void cancelPayment() {
        this.status = PaymentStatus.CANCELED;
    }

    public void updatePaymentInfo(PaymentType paymentType) {
        this.paymentType = paymentType;
        this.status = PaymentStatus.READY;
    }
}
