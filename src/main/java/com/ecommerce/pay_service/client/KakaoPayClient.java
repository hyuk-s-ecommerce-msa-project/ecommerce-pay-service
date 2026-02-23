package com.ecommerce.pay_service.client;

import com.ecommerce.pay_service.dto.KakaoApproveResponse;
import com.ecommerce.pay_service.dto.KakaoCancelResponse;
import com.ecommerce.pay_service.dto.KakaoReadyResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@FeignClient(name = "kakaoPayClient", url = "https://open-api.kakaopay.com/online/v1/payment")
public interface KakaoPayClient {
    @PostMapping("/ready")
    KakaoReadyResponse ready(@RequestHeader("Authorization") String auth, @RequestBody Map<String, Object> params);

    @PostMapping("/approve")
    KakaoApproveResponse approve(@RequestHeader("Authorization") String auth, @RequestBody Map<String, Object> params);

    @PostMapping("/cancel")
    KakaoCancelResponse cancel(@RequestHeader("Authorization") String auth, @RequestBody Map<String, Object> params);

}
