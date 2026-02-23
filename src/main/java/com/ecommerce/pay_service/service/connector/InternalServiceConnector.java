package com.ecommerce.pay_service.service.connector;

import com.ecommerce.pay_service.client.KeyInventoryClient;
import com.ecommerce.pay_service.client.OrderServiceClient;
import com.ecommerce.pay_service.vo.RequestKey;
import com.ecommerce.pay_service.vo.ResponseOrder;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestHeader;

@Component
@RequiredArgsConstructor
@Slf4j
public class InternalServiceConnector {
    private final KeyInventoryClient keyInventoryClient;
    private final OrderServiceClient orderServiceClient;

    @CircuitBreaker(name = "orderService_circuitBreaker", fallbackMethod = "fallbackOrderCheck")
    public ResponseOrder getOrderDetails(String orderId, @RequestHeader("userId") String userId) {
        log.info("주문 서비스 정보 조회 : orderId = {}", orderId);

        ResponseEntity<ResponseOrder> response = orderServiceClient.getOrderByOrderId(orderId, userId);

        ResponseOrder orderDetail = response.getBody();

        if (orderDetail == null) {
            throw new RuntimeException("주문 정보를 찾을 수 없습니다");
        }

        if (!orderDetail.getOrderId().equals(orderId)) {
            throw new RuntimeException("요청한 주문 번호와 조회된 주문 번호가 일치하지 않습니다.");
        }

        return response.getBody();
    }

    @CircuitBreaker(name = "internalService", fallbackMethod = "handleInternalFailure")
    public void confirmKeyAndOrder(String orderId, String userId) {
        log.info("후속 서비스 확정 프로세스 시작 : orderId = {}", orderId);

        // KeyInventory 서비스에 키 사용 확정 요청
        RequestKey confirmRequestKey = new RequestKey();
        confirmRequestKey.setOrderId(orderId);
        keyInventoryClient.confirmKeys(confirmRequestKey, userId);

        // Order 서비스에 주문 상태를 결제 완료로 변경 요청
        orderServiceClient.completePayment(orderId, userId);

        log.info("후속 서비스 확정 프로세스 완료 : orderId = {}", orderId);
    }

    private void handleInternalFailure(String orderId, String userId, Throwable throwable) {
        log.error("내부 서비스(Key/Order) 호출 중 서킷 브레이커 오픈 혹은 에러 발생 : {}", throwable.getMessage());

        throw new RuntimeException("내부 서비스(Key/Order) 호출 중 서킷 브레이커 오픈 혹은 에러 발생", throwable);
    }

    private ResponseOrder fallbackOrderCheck(String orderId, String userId, Throwable throwable) {
        log.error("주문 서비스 조회 실패 (서킷 오픈 혹은 에러) : {}", throwable.getMessage());

        throw new RuntimeException("현재 주문 확인이 불가능하여 결제를 진행할 수 없습니다.");
    }
}
