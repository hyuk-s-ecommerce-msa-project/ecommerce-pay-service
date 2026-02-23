package com.ecommerce.pay_service.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseOrderItem {
    private String productId;
    private Integer unitPrice;
    private String deliveredKey;
}