package com.ecommerce.pay_service.entity.enums;

import lombok.Getter;

@Getter
public enum PaymentType {
    KAKAO_PAY("카카오페이");

    private final String description;

    PaymentType(String description) {
        this.description = description;
    }
}
