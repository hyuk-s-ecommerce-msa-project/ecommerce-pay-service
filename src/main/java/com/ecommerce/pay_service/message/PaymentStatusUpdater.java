package com.ecommerce.pay_service.message;

import com.ecommerce.pay_service.entity.PaymentEntity;
import com.ecommerce.pay_service.entity.enums.PaymentStatus;
import com.ecommerce.pay_service.repository.PaymentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentStatusUpdater {

    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment-topic", groupId = "pay-service-group")
    @Transactional
    public void synchronizePaymentStatus(String message) {
        try {
            log.info("받은 메시지: {}", message);
            JsonNode jsonNode = objectMapper.readTree(message);

            if (jsonNode.isTextual()) {
                jsonNode = objectMapper.readTree(jsonNode.asText());
            }

            JsonNode orderIdNode = jsonNode.get("orderId");

            if (orderIdNode == null) {
                log.error("메시지에서 orderId를 찾을 수 없습니다: {}", message);
                return;
            }

            String orderId = orderIdNode.asText();

            PaymentEntity payment = paymentRepository.findByOrderId(orderId);

            if (payment != null && payment.getStatus() != PaymentStatus.COMPLETED) {
                payment.completePayment();
                paymentRepository.save(payment);
                log.info("자체 동기화 완료 결제 상태를 COMPLETED로 변경: OrderId={}", orderId);
            }
        } catch (Exception e) {
            log.error("메시지 동기화 중 에러 발생: ", e);
        }
    }
}
