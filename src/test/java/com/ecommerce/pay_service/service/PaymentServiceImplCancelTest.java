package com.ecommerce.pay_service.service;

import com.ecommerce.pay_service.client.KakaoPayClient;
import com.ecommerce.pay_service.dto.KakaoCancelResponse;
import com.ecommerce.pay_service.entity.OutboxEntity;
import com.ecommerce.pay_service.entity.PaymentEntity;
import com.ecommerce.pay_service.entity.enums.PaymentStatus;
import com.ecommerce.pay_service.entity.enums.PaymentType;
import com.ecommerce.pay_service.message.KafkaProducer;
import com.ecommerce.pay_service.repository.OutboxRepository;
import com.ecommerce.pay_service.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.*;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"payment-topic"})
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.acks=all",
        "spring.rabbitmq.enabled=false"
})
class PaymentServiceImplCancelTest {

    @Autowired
    private PaymentService paymentService;

    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @MockitoBean
    private KafkaProducer kafkaProducer;

    @MockitoBean
    private KakaoPayClient kakaoPayClient;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    private static final String TEST_ORDER_ID = "20260331-ORDER-999";
    private static final String TEST_USER_ID = "user-01";
    private static final String TEST_TID = "T1234567890";

    @BeforeEach
    void setUp() {
        // 1. 기존 데이터 삭제 (테스트 간 간섭 방지)
        paymentRepository.deleteAll();

        // 2. 반드시 TEST_ORDER_ID를 사용하여 데이터 생성
        PaymentEntity payment = PaymentEntity.createPayment(
                100L,
                TEST_ORDER_ID, // 여기!!!
                TEST_USER_ID,
                PaymentType.KAKAO_PAY
        );
        payment.updateTid(TEST_TID);
        payment.completePayment();

        paymentRepository.save(payment);

        // 디버깅용: 실제로 DB에 들어갔는지 확인
        System.out.println("Saved OrderID: " + TEST_ORDER_ID);
    }

    @Test
    @Transactional
    @DisplayName("결제 실패 처리 시 상태 변경 및 Outbox 기록 확인")
    void updatePaymentToFailedTest() {
        PaymentEntity payment = PaymentEntity.createPayment(100L, TEST_ORDER_ID, "user", PaymentType.KAKAO_PAY);
        payment.updateTid("T123456789");
        paymentRepository.save(payment);

        paymentService.updatePaymentToFailed(TEST_ORDER_ID);

        PaymentEntity result = paymentRepository.findByOrderId(TEST_ORDER_ID);
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);

        OutboxEntity outbox = outboxRepository.findByAggregateId(TEST_ORDER_ID)
                .orElseThrow(() -> new AssertionError("Outbox record not found"));
        assertThat(outbox.getEventType()).isEqualTo("PAYMENT_FAILED");
    }

    @Test
    @Transactional
    @DisplayName("결제 취소 성공 테스트")
    void cancelPaymentSuccessTest() {
        given(kakaoPayClient.cancel(anyString(), anyMap())).willReturn(new KakaoCancelResponse());

        paymentService.cancelPayment(TEST_ORDER_ID, "테스트 취소");

        PaymentEntity updated = paymentRepository.findByOrderId(TEST_ORDER_ID);
        assertThat(updated).isNotNull();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.CANCELED);
    }
}