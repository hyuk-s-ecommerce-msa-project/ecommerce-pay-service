package com.ecommerce.pay_service.vo;

import com.ecommerce.pay_service.entity.enums.PaymentType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponsePayment {
    private String orderId;
    private String userId;
    private Integer totalAmount;
    private PaymentType paymentType;
    private String status;
    private String nextRedirectUrl;
    private LocalDateTime createdAt;

    private List<ResponsePaymentItem> paymentItems;
}
