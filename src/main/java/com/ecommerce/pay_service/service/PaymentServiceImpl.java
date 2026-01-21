package com.ecommerce.pay_service.service;

import com.ecommerce.pay_service.dto.KakaoApproveResponse;
import com.ecommerce.pay_service.dto.KakaoReadyResponse;
import com.ecommerce.pay_service.dto.PaymentDto;
import com.ecommerce.pay_service.entity.PaymentEntity;
import com.ecommerce.pay_service.entity.enums.PaymentType;
import com.ecommerce.pay_service.repository.PaymentRepository;
import com.ecommerce.pay_service.vo.RequestPayment;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {
    private final PaymentRepository paymentRepository;
    private final ModelMapper modelMapper;
    private final RestTemplate restTemplate;

    @Value("${kakao.secret}")
    private String kakaoSecretKey;

    @Override
    @Transactional
    public void updatePaymentToCanceled(String orderId) {
        PaymentEntity paymentEntity = paymentRepository.findByOrderId(orderId);

        if (paymentEntity != null) {
            paymentEntity.cancelPayment();
            log.info("Payment status updated to CANCELED for Order ID: {}", orderId);
        }
    }

    @Override
    @Transactional
    public void updatePaymentToFailed(String orderId) {
        PaymentEntity paymentEntity = paymentRepository.findByOrderId(orderId);

        if (paymentEntity != null) {
            paymentEntity.failPayment();
            log.info("Payment status updated to FAILED for Order ID: {}", orderId);
        }
    }

    @Override
    @Transactional
    public KakaoApproveResponse completePayment(String pgToken, String orderId) {
        PaymentEntity paymentEntity = paymentRepository.findByOrderId(orderId);

        if (paymentEntity == null) {
            throw new RuntimeException("Cannot find Payment Info");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "SECRET_KEY " + kakaoSecretKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> params = new HashMap<>();
        params.put("cid", "TC0ONETIME");
        params.put("tid", paymentEntity.getTid());
        params.put("partner_order_id", orderId);
        params.put("partner_user_id", paymentEntity.getUserId());
        params.put("pg_token", pgToken);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(params, headers);

        try {
            KakaoApproveResponse response = restTemplate.postForObject(
                    "https://open-api.kakaopay.com/online/v1/payment/approve",
                    entity,
                    KakaoApproveResponse.class
            );

            if (response != null) {
                paymentEntity.completePayment();
                return response;
            } else {
                paymentEntity.failPayment();
                throw new RuntimeException("Payment Failed");
            }
        } catch (Exception e) {
            paymentEntity.failPayment();
            log.error("Payment Failed for orderId = {}", orderId, e);

            throw new RuntimeException("Payment approval failed : " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public PaymentDto processPayment(RequestPayment request, String userId) {
        PaymentEntity paymentEntity = paymentRepository.findByOrderId(request.getOrderId());

        if (paymentEntity == null) {
            paymentEntity = PaymentEntity.createPayment(
                    request.getOrderId(),
                    userId,
                    request.getPaymentType()
            );
        } else {
            paymentEntity.getPaymentItems().clear();
            paymentEntity.updatePaymentInfo(request.getPaymentType());
        }

        final PaymentEntity finalEntity = paymentEntity;

        request.getItems().forEach(item ->
                finalEntity.addPaymentItem(item.getProductId(), item.getUnitPrice(), item.getQty())
        );

        KakaoReadyResponse kakaoResponse = null;

        if (request.getPaymentType() == PaymentType.KAKAO_PAY) {
            kakaoResponse = callKakaoReady(request, userId, paymentEntity.getTotalAmount());

            paymentEntity.updateTid(kakaoResponse.getTid());
        }

        paymentRepository.save(paymentEntity);

        PaymentDto paymentDto = modelMapper.map(paymentEntity, PaymentDto.class);

        if (kakaoResponse != null) {
            paymentDto.setNextRedirectUrl(kakaoResponse.getNext_redirect_pc_url());
        }

        return paymentDto;
    }

    private KakaoReadyResponse callKakaoReady(RequestPayment request, String userId, Integer totalAmount) {
        String itemName = request.getItems().get(0).getProductId();
        if (request.getItems().size() > 1) {
            itemName += " 외 " + (request.getItems().size() - 1) + "건";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "SECRET_KEY " + kakaoSecretKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> params = new HashMap<>();
        params.put("cid", "TC0ONETIME");
        params.put("partner_order_id", request.getOrderId());
        params.put("partner_user_id", userId);
        params.put("item_name", itemName);
        params.put("quantity", request.getItems().size());
        params.put("total_amount", totalAmount);
        params.put("tax_free_amount", 0);
        params.put("approval_url", "http://localhost:8000/payment-service/payment/success?order_id=" + request.getOrderId());
        params.put("cancel_url", "http://localhost:8000/payment-service/payment/cancel?order_id=" + request.getOrderId());
        params.put("fail_url", "http://localhost:8000/payment-service/payment/fail?order_id=" + request.getOrderId());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(params, headers);

        return restTemplate.postForObject(
                "https://open-api.kakaopay.com/online/v1/payment/ready",
                entity,
                KakaoReadyResponse.class
        );
    }
}
