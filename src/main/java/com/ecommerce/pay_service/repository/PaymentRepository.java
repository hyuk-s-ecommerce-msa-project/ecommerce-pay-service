package com.ecommerce.pay_service.repository;

import com.ecommerce.pay_service.entity.PaymentEntity;
import org.springframework.data.repository.CrudRepository;

public interface PaymentRepository extends CrudRepository<PaymentEntity, Long> {
    PaymentEntity findByOrderId(String orderId);
}
