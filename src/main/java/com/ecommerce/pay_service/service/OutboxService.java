package com.ecommerce.pay_service.service;

import com.ecommerce.pay_service.entity.OutboxEntity;
import com.ecommerce.pay_service.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxService {
    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processEachMessage(OutboxEntity message) {
        try {
            kafkaTemplate.send("payment-topic", message.getPayload()).get(3, TimeUnit.SECONDS);
            message.markProcessed();
            outboxRepository.save(message);
        } catch (Exception e) {
            log.info("카프카 오류 : ", e);
        }
    }
}
