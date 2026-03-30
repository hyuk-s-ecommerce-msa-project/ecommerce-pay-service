package com.ecommerce.pay_service.service;

import com.ecommerce.pay_service.client.KakaoPayClient;
import com.ecommerce.pay_service.dto.KakaoApproveResponse;
import com.ecommerce.pay_service.entity.OutboxEntity;
import com.ecommerce.pay_service.entity.PaymentEntity;
import com.ecommerce.pay_service.entity.enums.PaymentStatus;
import com.ecommerce.pay_service.entity.enums.PaymentType;
import com.ecommerce.pay_service.repository.OutboxRepository;
import com.ecommerce.pay_service.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"payment-topic"})
@TestPropertySource(properties = {
        "spring.rabbitmq.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
})
@EnableScheduling
class PaymentServiceImplRecoveryTest {
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private OutboxRepository outboxRepository;
    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private KakaoPayClient kakaoPayClient;

    private String receivedMessage;
    private CountDownLatch latch = new CountDownLatch(1);

    @KafkaListener(topics = "payment-topic", groupId = "test-group")
    public void receive(String message) {
        System.out.println("received message: " + message);
        this.receivedMessage = message;
        latch.countDown();
    }

    @Test
    @DisplayName("결제 후 예외 발생 시에도 Outbox Relay가 작동하는지 확인")
    void test_eventual_consistency() throws InterruptedException {
        String orderId = "RECOVERY-123";
        String userId = "user-hyuk";

        PaymentEntity payment = PaymentEntity.createPayment(
                100L,
                orderId,
                userId,
                PaymentType.KAKAO_PAY
        );
        payment.updateTid("T1234567890");
        paymentRepository.save(payment);

        KakaoApproveResponse mockResponse = new KakaoApproveResponse();
        KakaoApproveResponse.Amount amount = new KakaoApproveResponse.Amount();
        amount.setTotal(10000);
        mockResponse.setAmount(amount);

        lenient().when(kakaoPayClient.approve(anyString(), anyMap())).thenReturn(mockResponse);

        try {
            paymentService.completePayment("fake-token", orderId);
        } catch (Exception e) {
            System.out.println("예외 발생 확인 (의도됨): {}" + e.getMessage());
        }

        boolean messageSent = latch.await(10, TimeUnit.SECONDS);

        assertThat(messageSent).as("메인 로직 에러와 상관없이 카프카 메시지가 전송되어야 함").isTrue();

        OutboxEntity outbox = outboxRepository.findByAggregateId(orderId)
                .orElseThrow(() -> new AssertionError("Outbox 기록이 생성되지 않음"));

        assertThat(outbox.isProcessed()).isTrue();

        Thread.sleep(2000);

        transactionTemplate.executeWithoutResult(status -> {
            OutboxEntity outboxEntity = outboxRepository.findByAggregateId(orderId)
                    .orElseThrow(() -> new AssertionError("Outbox 기록이 생성되지 않음"));
            assertThat(outboxEntity.isProcessed()).isTrue();

            // [자가 치유 결과 확인]
            PaymentEntity finalPayment = paymentRepository.findByOrderId(orderId);
            System.out.println("최종 결제 상태 확인: {}" + finalPayment.getStatus());

            assertThat(finalPayment.getStatus())
                    .as("컨슈머에 의해 READY(혹은 FAIL)에서 COMPLETED로 복구되어야 함")
                    .isEqualTo(PaymentStatus.COMPLETED);
        });
    }
}