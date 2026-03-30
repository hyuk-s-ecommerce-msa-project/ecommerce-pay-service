package com.ecommerce.pay_service.message;

import com.ecommerce.pay_service.service.PaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumer {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order-topic", groupId = "payment-service-group")
    public void consumeOrderEvent(String message) {
        try {
            log.info("주문 서비스로부터 이벤트 수신: {}", message);
            JsonNode jsonNode = objectMapper.readTree(message);

            String eventType = jsonNode.get("eventType").asText();
            String orderId = jsonNode.get("orderId").asText();

            if ("ORDER_CANCELED".equals(eventType)) {
                log.info("환불 프로세스 시작 - OrderId: {}", orderId);
                paymentService.cancelPayment(orderId, "사용자 요청에 의한 주문 취소");
            }
        } catch (Exception e) {
            log.error("이벤트 처리 중 오류 발생: {}", e.getMessage());
        }
    }
}
