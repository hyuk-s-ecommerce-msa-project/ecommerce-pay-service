package com.ecommerce.pay_service.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.joda.time.DateTime;

@Getter
@Setter
@ToString
public class KakaoCancelResponse {
    private String tid;
    private Integer canceled_amount;
    private String item_name;
    private String item_code;
    private DateTime approved_at;
    private DateTime canceled_at;
}
