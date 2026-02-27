package com.ecommerce.pay_service.service;

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
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

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

    @Override
    @Transactional
    public KakaoApproveResponse completePayment(String pgToken, String orderId) {
        PaymentEntity paymentEntity = paymentRepository.findByOrderId(orderId);

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
                if (!paymentEntity.getTotalAmount().equals(response.getAmount().getTotal())) {
                    throw new RuntimeException("Total Amount Not Match");
                }

                paymentEntity.completePayment();

                try {
                    internalConnector.confirmKeyAndOrder(orderId, paymentEntity.getUserId());
                } catch (Exception e) {
                    Map<String, Object> cancelParams = new HashMap<>();

                    cancelParams.put("cid", response.getCid());
                    cancelParams.put("tid", response.getTid());
                    cancelParams.put("cancel_amount", response.getAmount().getTotal());
                    cancelParams.put("tax_free", response.getAmount().getTax_free());

                    kakaoPayClient.cancel(auth, cancelParams);
                    paymentEntity.cancelPayment();

                    throw new RuntimeException("후처리 실패로 자동 취소", e);
                }

                return response;
            } else {
                paymentEntity.failPayment();
                throw new RuntimeException("Payment failed");
            }
        } catch (Exception e) {
            paymentEntity.failPayment();
            RequestKey revokeRequestKey = new RequestKey();
            revokeRequestKey.setOrderId(orderId);

            keyInventoryClient.revokeKeys(revokeRequestKey, paymentEntity.getUserId());

            throw new RuntimeException("Payment approval failed : " + e.getMessage());
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
            paymentEntity.updateTid(kakaoResponse.getTid());
        }

        paymentRepository.save(paymentEntity);

        PaymentDto paymentDto = modelMapper.map(paymentEntity, PaymentDto.class);

        if (kakaoResponse != null) {
            paymentDto.setNextRedirectUrl(kakaoResponse.getNext_redirect_pc_url());
        }

        return paymentDto;
    }
}
