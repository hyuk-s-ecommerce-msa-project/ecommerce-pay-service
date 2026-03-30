package com.ecommerce.pay_service.message;

import com.ecommerce.pay_service.entity.OutboxEntity;
import com.ecommerce.pay_service.repository.OutboxRepository;
import com.ecommerce.pay_service.service.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OutboxMessageRelay {
    private final OutboxRepository outboxRepository;
    private final OutboxService outboxService;

    @Scheduled(fixedDelay = 1000)
    public void publishMessages() {
        List<OutboxEntity> messages = outboxRepository.findByProcessedFalse();
        for (OutboxEntity message : messages) {
            outboxService.processEachMessage(message);
        }
    }
}
