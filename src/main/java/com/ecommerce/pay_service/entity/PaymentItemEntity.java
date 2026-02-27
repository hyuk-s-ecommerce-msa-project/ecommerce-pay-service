package com.ecommerce.pay_service.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentItemEntity {
    @Id
    private Long id;

    @Column(nullable = false)
    private String productId;

    @Column(nullable = false)
    private Integer unitPrice;
    @Column(nullable = false)
    private Integer qty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private PaymentEntity payment;

    public static PaymentItemEntity createPaymentItem(Long id, String productId, Integer unitPrice, Integer qty, PaymentEntity payment) {
        PaymentItemEntity item = new PaymentItemEntity();

        item.id = id;
        item.productId = productId;
        item.unitPrice = unitPrice;
        item.qty = qty;
        item.payment = payment;

        return item;
    }
}
