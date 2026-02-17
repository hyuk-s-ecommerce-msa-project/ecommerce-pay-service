package com.ecommerce.pay_service.service;

import com.ecommerce.pay_service.client.KeyInventoryClient;
import com.ecommerce.pay_service.client.OrderServiceClient;
import com.ecommerce.pay_service.dto.KakaoApproveResponse;
import com.ecommerce.pay_service.dto.KakaoCancelResponse;
import com.ecommerce.pay_service.dto.KakaoReadyResponse;
import com.ecommerce.pay_service.dto.PaymentDto;
import com.ecommerce.pay_service.entity.PaymentEntity;
import com.ecommerce.pay_service.entity.enums.PaymentStatus;
import com.ecommerce.pay_service.entity.enums.PaymentType;
import com.ecommerce.pay_service.repository.PaymentRepository;
import com.ecommerce.pay_service.vo.RequestKey;
import com.ecommerce.pay_service.vo.RequestPayment;
import com.ecommerce.pay_service.vo.ResponsePayment;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final Environment env;
    private final KeyInventoryClient keyInventoryClient;
    private final OrderServiceClient orderServiceClient;

    @Override
    @Transactional
    public KakaoApproveResponse completePayment(String pgToken, String orderId) {
        PaymentEntity paymentEntity = paymentRepository.findByOrderId(orderId);

        if (paymentEntity == null) {
            throw new RuntimeException("Cannot find Payment Info");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "SECRET_KEY " + env.getProperty("kakao.secret"));
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
                if (!paymentEntity.getTotalAmount().equals(response.getAmount().getTotal())) {
                    log.error("주문 ID : {}에서 기대 금액 : {}, 실제 결제액 : {}이 다릅니다", orderId, paymentEntity.getTotalAmount(), response.getAmount().getTotal());
                    throw new RuntimeException("Total Amount Not Match");
                }

                paymentEntity.completePayment();

                try {
                    RequestKey confirmRequestKey = new RequestKey();
                    confirmRequestKey.setOrderId(orderId);

                    keyInventoryClient.confirmKeys(confirmRequestKey, paymentEntity.getUserId());

                    ResponseEntity<ResponsePayment> orderResponse = orderServiceClient.completePayment(orderId, paymentEntity.getUserId());

                    if (orderResponse.getStatusCode().is2xxSuccessful() && orderResponse.getBody() != null) {
                        log.info("주문 서비스 응답 성공: {}", orderResponse.getBody().getOrderId());
                    } else {
                        throw new RuntimeException("Order Service Error");
                    }
                } catch (Exception e) {
                    log.error("후속 처리 실패로 인한 결제 취소 진행 : {}", e.getMessage());

                    KakaoCancelResponse cancelResponse = callKakaoCancel(response.getCid(), response.getTid(), response.getAmount().getTotal(),
                                                                        response.getAmount().getTax_free());

                    paymentEntity.cancelPayment();

                    log.info("결제 취소 내역 : {}", cancelResponse);

                    throw new RuntimeException("결제 후처리 실패로 인해 자동 취소 되었습니다.", e);
                }

                return response;
            } else {
                paymentEntity.failPayment();
                throw new RuntimeException("Payment Failed");
            }
        } catch (Exception e) {
            paymentEntity.failPayment();

            RequestKey revokeRequestKey = new RequestKey();
            revokeRequestKey.setOrderId(orderId);
            keyInventoryClient.revokeKeys(revokeRequestKey, paymentEntity.getUserId());

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
            if (paymentEntity.getStatus() == PaymentStatus.COMPLETED) {
                throw new RuntimeException("This order is already paid");
            }

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

    private KakaoCancelResponse callKakaoCancel(String cid, String tid, Integer cancelAmount, Integer taxFree) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "SECRET_KEY " + env.getProperty("kakao.secret"));
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> params = new HashMap<>();
        params.put("cid", cid);
        params.put("tid", tid);
        params.put("cancel_amount", cancelAmount);
        params.put("tax_free", taxFree);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(params, headers);

        return restTemplate.postForObject(
                "https://open-api.kakaopay.com/online/v1/payment/cancel",
                entity,
                KakaoCancelResponse.class
        );
    }

    private KakaoReadyResponse callKakaoReady(RequestPayment request, String userId, Integer totalAmount) {
        String itemName = request.getItems().get(0).getProductId();
        if (request.getItems().size() > 1) {
            itemName += " 외 " + (request.getItems().size() - 1) + "건";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "SECRET_KEY " + env.getProperty("kakao.secret"));
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
