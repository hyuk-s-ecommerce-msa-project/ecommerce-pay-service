package com.ecommerce.pay_service.vo;

import com.ecommerce.pay_service.entity.enums.PaymentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class RequestPayment {
    @NotBlank(message = "주문 번호는 필수입니다.")
    private String orderId;

    @NotNull(message = "결제 수단은 필수입니다.")
    private PaymentType paymentType; // KAKAO_PAY

    @NotEmpty(message = "결제할 상품이 최소 하나 이상 있어야 합니다.")
    private List<OrderItem> items;

    @Data
    public static class OrderItem {
        private String productId;
        private Integer qty;
        private Integer unitPrice;
    }
}
