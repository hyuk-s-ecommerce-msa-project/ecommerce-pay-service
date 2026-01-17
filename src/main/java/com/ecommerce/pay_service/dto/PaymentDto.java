package com.ecommerce.pay_service.dto;

import com.ecommerce.pay_service.entity.enums.PaymentType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PaymentDto {
    private String orderId;
    private String userId;
    private Integer totalAmount;
    private Integer payAmount;
    private Integer usedPoint;
    private String status;

    private PaymentType paymentType;
    private LocalDateTime createdAt;

    private String nextRedirectUrl;
    private String tid;

    private List<PaymentItemDto> paymentItems;

    @Data
    public static class PaymentItemDto {
        private String productId;
        private Integer unitPrice;
        private Integer qty;
    }
}