package com.ecommerce.pay_service.service;

import com.ecommerce.pay_service.client.KakaoPayClient;
import com.ecommerce.pay_service.client.KeyInventoryClient;
import com.ecommerce.pay_service.dto.KakaoApproveResponse;
import com.ecommerce.pay_service.dto.KakaoReadyResponse;
import com.ecommerce.pay_service.dto.PaymentDto;
import com.ecommerce.pay_service.entity.PaymentEntity;
import com.ecommerce.pay_service.entity.enums.PaymentStatus;
import com.ecommerce.pay_service.entity.enums.PaymentType;
import com.ecommerce.pay_service.repository.PaymentRepository;
import com.ecommerce.pay_service.service.connector.InternalServiceConnector;
import com.ecommerce.pay_service.vo.RequestPayment;
import com.ecommerce.pay_service.vo.ResponseOrder;
import com.ecommerce.snowflake.util.SnowflakeIdGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.core.env.Environment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private KakaoPayClient kakaoPayClient;
    @Mock
    private InternalServiceConnector internalConnector;
    @Mock
    private KeyInventoryClient keyInventoryClient;
    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;
    @Mock
    private Environment env;

    @Spy
    private ModelMapper modelMapper = new ModelMapper();

    private final String orderId = "ORD-2026-0330";
    private final String userId = "user-777";

    @Test
    @DisplayName("결제 준비: 주문 금액 검증 성공 및 카카오 Ready 응답 반환")
    void processPayment_Success() {
        RequestPayment request = new RequestPayment();
        request.setOrderId(orderId);
        request.setPaymentType(PaymentType.KAKAO_PAY);

        RequestPayment.OrderItem item = new RequestPayment.OrderItem();
        item.setProductId("PROD-01");
        item.setUnitPrice(15000);
        item.setQty(1);
        request.setItems(List.of(item));

        ResponseOrder orderDetail = new ResponseOrder();
        orderDetail.setTotalAmount(15000);

        when(internalConnector.getOrderDetails(orderId, userId)).thenReturn(orderDetail);
        when(snowflakeIdGenerator.nextId()).thenReturn(100L, 200L);
        when(env.getProperty("kakao.secret")).thenReturn("test_secret_key");

        KakaoReadyResponse kakaoResponse = new KakaoReadyResponse();
        kakaoResponse.setTid("TID-99999");
        kakaoResponse.setNext_redirect_pc_url("http://kakao-pay.com/next");
        when(kakaoPayClient.ready(anyString(), anyMap())).thenReturn(kakaoResponse);

        PaymentDto result = paymentService.processPayment(request, userId);

        assertThat(result.getTid()).isEqualTo("TID-99999");
        assertThat(result.getNextRedirectUrl()).isEqualTo("http://kakao-pay.com/next");
        verify(paymentRepository, times(1)).save(any(PaymentEntity.class));
    }

    @Test
    @DisplayName("예외: 결제 요청 금액과 실제 주문 금액이 다를 경우 예외 발생")
    void processPayment_AmountMismatch_Fail() {
        RequestPayment request = new RequestPayment();
        request.setOrderId(orderId);
        RequestPayment.OrderItem item = new RequestPayment.OrderItem();
        item.setUnitPrice(5000);
        request.setItems(List.of(item));

        ResponseOrder orderDetail = new ResponseOrder();
        orderDetail.setTotalAmount(10000);

        when(internalConnector.getOrderDetails(orderId, userId)).thenReturn(orderDetail);

        assertThrows(RuntimeException.class, () -> {
            paymentService.processPayment(request, userId);
        });
    }

    @Test
    @DisplayName("결제 승인: 성공 시 상태 변경 및 후처리 서비스 호출")
    void completePayment_Success() {
        PaymentEntity paymentEntity = PaymentEntity.createPayment(1L, orderId, userId, PaymentType.KAKAO_PAY);
        paymentEntity.addPaymentItem(2L, "PROD-01", 10000, 1);
        paymentEntity.updateTid("TID-TEST");

        when(paymentRepository.findByOrderId(orderId)).thenReturn(paymentEntity);
        when(env.getProperty("kakao.secret")).thenReturn("test_secret_key");

        KakaoApproveResponse approveResponse = new KakaoApproveResponse();
        KakaoApproveResponse.Amount amount = new KakaoApproveResponse.Amount();
        amount.setTotal(10000);
        approveResponse.setAmount(amount);
        when(kakaoPayClient.approve(anyString(), anyMap())).thenReturn(approveResponse);

        paymentService.completePayment("pg-token", orderId);

        assertThat(paymentEntity.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(internalConnector, times(1)).confirmKeyAndOrder(orderId, userId);
    }

    @Test
    @DisplayName("보상 트랜잭션: 승인 후 내부 서비스 호출 실패 시 결제 취소 API 호출")
    void completePayment_InternalProcessFail_AutoCancel() {
        PaymentEntity paymentEntity = PaymentEntity.createPayment(1L, orderId, userId, PaymentType.KAKAO_PAY);
        paymentEntity.addPaymentItem(2L, "PROD-01", 10000, 1);
        paymentEntity.updateTid("TID-TEST");

        when(paymentRepository.findByOrderId(orderId)).thenReturn(paymentEntity);
        when(env.getProperty("kakao.secret")).thenReturn("test_secret_key");

        KakaoApproveResponse approveResponse = new KakaoApproveResponse();
        KakaoApproveResponse.Amount amount = new KakaoApproveResponse.Amount();
        amount.setTotal(10000);
        approveResponse.setAmount(amount);
        when(kakaoPayClient.approve(anyString(), anyMap())).thenReturn(approveResponse);

        doThrow(new RuntimeException("Server Error"))
                .when(internalConnector).confirmKeyAndOrder(orderId, userId);

        assertThrows(RuntimeException.class, () -> {
            paymentService.completePayment("pg-token", orderId);
        });

        verify(kakaoPayClient, times(1)).cancel(anyString(), anyMap()); // 결제 취소 호출 확인
        assertThat(paymentEntity.getStatus()).isEqualTo(PaymentStatus.CANCELED); // 엔티티 상태 변경 확인
    }

    @Test
    @DisplayName("예외: 이미 결제가 완료된 주문에 대해 다시 결제 준비를 요청할 경우")
    void processPayment_AlreadyPaid_Fail() {
        RequestPayment request = new RequestPayment();
        request.setOrderId(orderId);
        request.setPaymentType(PaymentType.KAKAO_PAY);

        RequestPayment.OrderItem item = new RequestPayment.OrderItem();
        item.setUnitPrice(10000);
        item.setQty(1);
        request.setItems(List.of(item));

        PaymentEntity existingEntity = PaymentEntity.createPayment(1L, orderId, userId, PaymentType.KAKAO_PAY);
        existingEntity.completePayment();

        ResponseOrder orderDetail = new ResponseOrder();
        orderDetail.setTotalAmount(10000);

        when(internalConnector.getOrderDetails(anyString(), anyString())).thenReturn(orderDetail);
        when(paymentRepository.findByOrderId(orderId)).thenReturn(existingEntity);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            paymentService.processPayment(request, userId);
        });

        assertThat(exception.getMessage()).isEqualTo("This order is already paid");
    }

    @Test
    @DisplayName("예외: 카카오페이 Ready API가 실패(null 반환)할 경우 예외 발생")
    void processPayment_KakaoReadyFail_ThrowException() {
        RequestPayment request = new RequestPayment();
        request.setOrderId(orderId);
        request.setPaymentType(PaymentType.KAKAO_PAY);

        RequestPayment.OrderItem item = new RequestPayment.OrderItem();
        item.setUnitPrice(10000);
        item.setQty(1);
        request.setItems(List.of(item));

        ResponseOrder orderDetail = new ResponseOrder();
        orderDetail.setTotalAmount(10000);

        when(internalConnector.getOrderDetails(orderId, userId)).thenReturn(orderDetail);
        when(snowflakeIdGenerator.nextId()).thenReturn(1L);
        when(env.getProperty("kakao.secret")).thenReturn("test_key");

        when(kakaoPayClient.ready(anyString(), anyMap())).thenReturn(null);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            paymentService.processPayment(request, userId);
        });

        assertThat(exception.getMessage()).contains("카카오페이 준비 중 오류가 발생했습니다");

        verify(paymentRepository, times(0)).save(any(PaymentEntity.class));
    }

    @Test
    @DisplayName("증명: 카카오 결제는 성공(External OK)했으나 DB는 롤백(Internal Fail)된 불일치 상황")
    void prove_data_inconsistency() {
        String orderId = "PROVE-ERR-001";
        KakaoApproveResponse mockResponse = new KakaoApproveResponse();

        lenient().when(kakaoPayClient.approve(anyString(), anyMap())).thenReturn(mockResponse);

        assertThrows(RuntimeException.class, () -> {
            paymentService.completePayment("fake-token", orderId);
        });

        PaymentEntity payment = paymentRepository.findByOrderId(orderId);

        assertThat(payment).as("DB는 롤백되어 데이터가 없어야 함").isNull();

        System.out.println("카카오 승인은 호출되었지만, DB 트랜잭션은 성공적으로 롤백됨.");
        System.out.println("사용자는 결제 완료 문자를 받았으나, 우리 시스템엔 주문 기록이 없는 '데이터 불일치' 발생.");
    }
}