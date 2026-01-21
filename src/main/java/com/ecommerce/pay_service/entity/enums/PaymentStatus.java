package com.ecommerce.pay_service.entity.enums;

import lombok.Getter;

@Getter
public enum PaymentStatus {
    READY,
    COMPLETED,
    CANCELED,
    FAILED
}
