package com.ecommerce.pay_service.service;

import com.ecommerce.pay_service.entity.OutboxEntity;
import com.ecommerce.pay_service.message.KafkaProducer;
import com.ecommerce.pay_service.repository.OutboxRepository;
import com.ecommerce.pay_service.service.connector.InternalServiceConnector;
import com.ecommerce.pay_service.client.KakaoPayClient;
import com.ecommerce.pay_service.client.KeyInventoryClient;
import com.ecommerce.pay_service.dto.KakaoApproveResponse;
import com.ecommerce.pay_service.dto.KakaoReadyResponse;
import com.ecommerce.pay_service.dto.PaymentDto;
import com.ecommerce.pay_service.entity.PaymentEntity;
import com.ecommerce.pay_service.entity.enums.PaymentStatus;
import com.ecommerce.pay_service.entity.enums.PaymentType;
import com.ecommerce.pay_service.repository.PaymentRepository;
import com.ecommerce.pay_service.vo.RequestKey;
import com.ecommerce.pay_service.vo.RequestPayment;
import com.ecommerce.pay_service.vo.ResponseOrder;
import com.ecommerce.snowflake.util.SnowflakeIdGenerator;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {
    private final PaymentRepository paymentRepository;
    private final ModelMapper modelMapper;
    private final Environment env;
    private final KeyInventoryClient keyInventoryClient;
    private final KakaoPayClient kakaoPayClient;
    private final InternalServiceConnector internalConnector;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final OutboxRepository outboxRepository;
    private final KafkaProducer kafkaProducer;

    @Override
    @Transactional
    public KakaoApproveResponse completePayment(String pgToken, String orderId) {
        PaymentEntity paymentEntity = paymentRepository.findByOrderId(orderId);

        if (paymentEntity.getStatus() != PaymentStatus.READY) {
            throw new RuntimeException("이미 처리된 결제입니다.");
        }

        if (paymentEntity == null) {
            throw new RuntimeException("Cannot find Payment Info");
        }

        String auth = "SECRET_KEY " + env.getProperty("kakao.secret");

        Map<String, Object> params = new HashMap<>();

        params.put("cid", "TC0ONETIME");
        params.put("tid", paymentEntity.getTid());
        params.put("partner_order_id", orderId);
        params.put("partner_user_id", paymentEntity.getUserId());
        params.put("pg_token", pgToken);

        try {
            KakaoApproveResponse response = kakaoPayClient.approve(auth, params);

            if (response != null) {
                paymentEntity.completePayment();

                String messagePayload = String.format(
                        "{\"orderId\":\"%s\", \"userId\":\"%s\", \"amount\":%d}",
                        orderId, paymentEntity.getUserId(), response.getAmount().getTotal()
                );

                OutboxEntity outbox = new OutboxEntity(
                        snowflakeIdGenerator.nextId(),
                        orderId,
                        "ORDER",
                        "PAYMENT_COMPLETED",
                        messagePayload,
                        false,
                        LocalDateTime.now()
                );

                outboxRepository.save(outbox);

                log.info("결제 완료 및 Outbox 저장 완료: {}", orderId);

                return response;
            } else {
                throw new RuntimeException("카카오 승인 응답이 비어있습니다.");
            }
        } catch (Exception e) {
            log.error("결제 처리 중 예외 발생: {}", e.getMessage());
            throw new RuntimeException("결제 프로세스 실패", e);
        }
    }

    @Override
    @Transactional
    public PaymentDto processPayment(RequestPayment request, String userId) {
        ResponseOrder orderDetail = internalConnector.getOrderDetails(request.getOrderId(), userId);

        int calculateTotalAmount = request.getItems().stream()
                .mapToInt(RequestPayment.OrderItem::getUnitPrice)
                .sum();

        if (!orderDetail.getTotalAmount().equals(calculateTotalAmount)) {
            log.error("Total Amount Not Match");

            throw new RuntimeException("결제 요청 금액이 실제 주문 금액과 일치하지 않습니다");
        }

        PaymentEntity paymentEntity = paymentRepository.findByOrderId(request.getOrderId());

        Long snowflakeId = snowflakeIdGenerator.nextId();

        if (paymentEntity == null) {
            paymentEntity = PaymentEntity.createPayment(
                    snowflakeId,
                    request.getOrderId(),
                    userId,
                    request.getPaymentType()
            );
        } else {
            if (paymentEntity.getStatus() == PaymentStatus.COMPLETED) {
                throw new RuntimeException("This order is already paid");
            }

            paymentEntity.getPaymentItems().clear();
            paymentEntity.updatePaymentInfo(request.getPaymentType());
        }

        final PaymentEntity finalEntity = paymentEntity;

        request.getItems().forEach(item -> {
                    Long itemSnowflakeId = snowflakeIdGenerator.nextId();
                    finalEntity.addPaymentItem(itemSnowflakeId, item.getProductId(), item.getUnitPrice(), item.getQty());
                }
        );

        KakaoReadyResponse kakaoResponse = null;

        if (request.getPaymentType() == PaymentType.KAKAO_PAY) {
            String auth = "SECRET_KEY " + env.getProperty("kakao.secret");

            Map<String, Object> readyParams = new HashMap<>();

            readyParams.put("cid", "TC0ONETIME");
            readyParams.put("partner_order_id", request.getOrderId());
            readyParams.put("partner_user_id", userId);
            readyParams.put("item_name", "Order_" + request.getOrderId());
            readyParams.put("quantity", request.getItems().size());
            readyParams.put("total_amount", paymentEntity.getTotalAmount());
            readyParams.put("tax_free_amount", 0);
            readyParams.put("approval_url", "http://localhost:8000/payment-service/payment/success?order_id=" + request.getOrderId());
            readyParams.put("cancel_url", "http://localhost:8000/payment-service/payment/cancel?order_id=" + request.getOrderId());
            readyParams.put("fail_url", "http://localhost:8000/payment-service/payment/fail?order_id=" + request.getOrderId());

            kakaoResponse = kakaoPayClient.ready(auth, readyParams);

            if (kakaoResponse != null) {
                paymentEntity.updateTid(kakaoResponse.getTid());
            } else {
                throw new RuntimeException("카카오페이 준비 중 오류가 발생했습니다.");
            }
        }

        paymentRepository.save(paymentEntity);

        PaymentDto paymentDto = modelMapper.map(paymentEntity, PaymentDto.class);

        if (kakaoResponse != null) {
            paymentDto.setNextRedirectUrl(kakaoResponse.getNext_redirect_pc_url());
        }

        return paymentDto;
    }

    @Override
    @Transactional
    public void cancelPayment(String orderId, String reason) {
        PaymentEntity paymentEntity = paymentRepository.findByOrderId(orderId);

        if (paymentEntity == null) {
            throw new RuntimeException("취소할 결제 정보를 찾을 수 없습니다.");
        }

        if (paymentEntity.getStatus() == PaymentStatus.CANCELED) {
            log.info("이미 취소된 주문입니다: {}", orderId);
            return;
        }

        String auth = "SECRET_KEY " + env.getProperty("kakao.secret");
        Map<String, Object> params = new HashMap<>();
        params.put("cid", "TC0ONETIME");
        params.put("tid", paymentEntity.getTid());
        params.put("cancel_amount", paymentEntity.getTotalAmount()); // 전액 환불 기준
        params.put("cancel_tax_free_amount", 0);

        try {
            kakaoPayClient.cancel(auth, params);

            paymentEntity.cancelPayment();

            String messagePayload = String.format(
                    "{\"orderId\":\"%s\", \"status\":\"CANCELLED\", \"reason\":\"%s\"}",
                    orderId, reason
            );

            OutboxEntity outbox = new OutboxEntity(
                    snowflakeIdGenerator.nextId(),
                    orderId,
                    "ORDER",
                    "PAYMENT_CANCELLED",
                    messagePayload,
                    false,
                    LocalDateTime.now()
            );
            outboxRepository.save(outbox);

            log.info("결제 취소 완료 및 Outbox 저장: {}", orderId);

        } catch (Exception e) {
            log.error("결제 취소 중 오류 발생: {}", e.getMessage());
            throw new RuntimeException("환불 처리 실패", e);
        }
    }

    @Transactional
    @Override
    public void updatePaymentToFailed(String orderId) {
        PaymentEntity payment = paymentRepository.findByOrderId(orderId);

        if (payment == null) {
            throw new IllegalArgumentException("해당 주문의 결제 내역이 존재하지 않습니다. orderId: " + orderId);
        }

        payment.failPayment();
        paymentRepository.save(payment);

        String messagePayload = String.format(
                "{\"orderId\":\"%s\", \"status\":\"FAILED\"}",
                orderId
        );

        OutboxEntity outbox = new OutboxEntity(
                snowflakeIdGenerator.nextId(),
                orderId,
                "ORDER", // 목적지 서비스 또는 도메인
                "PAYMENT_FAILED", // 이벤트 타입
                messagePayload,
                false,
                LocalDateTime.now()
        );

        outboxRepository.save(outbox);

        kafkaProducer.send("pay-failed-topic", orderId);

        log.info("결제 실패 처리 완료 - OrderID: {}, Status: {}", payment.getOrderId(), payment.getStatus());
    }
}
