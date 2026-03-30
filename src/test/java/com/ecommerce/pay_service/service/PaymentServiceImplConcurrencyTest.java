package com.ecommerce.pay_service.service;

import com.ecommerce.pay_service.client.KakaoPayClient;
import com.ecommerce.pay_service.client.KeyInventoryClient;
import com.ecommerce.pay_service.dto.KakaoApproveResponse;
import com.ecommerce.pay_service.entity.PaymentEntity;
import com.ecommerce.pay_service.entity.enums.PaymentType;
import com.ecommerce.pay_service.repository.PaymentRepository;
import com.ecommerce.pay_service.service.connector.InternalServiceConnector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "kakao.secret=test_secret_key"
})
class PaymentServiceImplConcurrencyTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoBean
    private KakaoPayClient kakaoPayClient;
    @MockitoBean
    private InternalServiceConnector internalConnector;
    @MockitoBean
    private KeyInventoryClient keyInventoryClient;

    private final String orderId = "CONCUR_ORD_100";
    private final String userId = "user_777";

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        PaymentEntity payment = PaymentEntity.createPayment(1L, orderId, userId, PaymentType.KAKAO_PAY);
        payment.addPaymentItem(2L, "PROD-1", 10000, 1);
        payment.updateTid("TID-123");
        paymentRepository.save(payment);

        KakaoApproveResponse mockRes = new KakaoApproveResponse();
        KakaoApproveResponse.Amount amount = new KakaoApproveResponse.Amount();
        amount.setTotal(10000);
        mockRes.setAmount(amount);
        when(kakaoPayClient.approve(anyString(), anyMap())).thenReturn(mockRes);
    }

    @Test
    void completePayment_Concurrency_Test() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                try {
                    paymentService.completePayment("token", orderId);
                    successCount.getAndIncrement();
                } catch (Exception e) {
                    System.out.println("결제 시도 실패: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();

        assertThat(successCount.get()).isEqualTo(1);
    }
}