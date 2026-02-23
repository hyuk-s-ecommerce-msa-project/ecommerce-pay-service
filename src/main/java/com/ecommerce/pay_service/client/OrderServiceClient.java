package com.ecommerce.pay_service.client;

import com.ecommerce.pay_service.vo.ResponseOrder;
import com.ecommerce.pay_service.vo.ResponsePayment;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "order-service")
public interface OrderServiceClient {
    @PostMapping("/order-service/orders/{orderId}/complete")
    ResponseEntity<ResponsePayment> completePayment(@PathVariable("orderId") String orderId, @RequestHeader("userId") String userId);

    @GetMapping("/order-service/orders/{orderId}/list")
    ResponseEntity<ResponseOrder> getOrderByOrderId(@PathVariable("orderId") String orderId, @RequestHeader("userId") String userId);
}
