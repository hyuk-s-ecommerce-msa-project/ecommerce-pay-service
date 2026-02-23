package com.ecommerce.pay_service.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseOrder {
    private String orderId;
    private String userId;
    private Integer totalAmount;
    private Integer payAmount;
    private Integer usedPoint;
    private String orderStatus;
    private LocalDateTime createdAt;

    private List<ResponseOrderItem> orderItems;
}
