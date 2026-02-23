package com.ecommerce.pay_service.service;

import com.ecommerce.pay_service.dto.KakaoApproveResponse;
import com.ecommerce.pay_service.dto.PaymentDto;
import com.ecommerce.pay_service.entity.PaymentEntity;
import com.ecommerce.pay_service.vo.RequestPayment;

public interface PaymentService {
    PaymentDto processPayment(RequestPayment request, String userId);
    KakaoApproveResponse completePayment(String pgToken, String orderId);
}
