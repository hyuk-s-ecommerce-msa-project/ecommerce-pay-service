package com.ecommerce.pay_service.dto;

import lombok.Data;

@Data
public class KakaoReadyResponse {
    private String tid;
    private String next_redirect_pc_url;
    private String created_at;
}
