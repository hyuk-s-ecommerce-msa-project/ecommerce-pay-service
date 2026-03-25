package com.ecommerce.pay_service.client;

import com.ecommerce.pay_service.vo.RequestKey;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "key-inventory-service",
        url = "http://key-inventory-service:8085"
)
public interface KeyInventoryClient {
    @PostMapping("/key-inventory/confirm")
    void confirmKeys(@RequestBody RequestKey requestKey, @RequestHeader("userId") String userId);

    @PostMapping("/key-inventory/revoke")
    void revokeKeys(@RequestBody RequestKey requestKey, @RequestHeader("userId") String userId);
}
