package com.georgia.jeogiyo.order.dto.response;

import com.georgia.jeogiyo.order.entity.Order;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class OrderCancelResponse {
    private UUID orderId;
    private String orderStatus;
    private LocalDateTime canceledAt;
    private String cancelReason;

    public static OrderCancelResponse of(Order order, String cancelReason) {
        OrderCancelResponse response = new OrderCancelResponse();
        response.setOrderId(order.getOrderId());
        response.setOrderStatus(order.getOrderStatus().name());
        response.setCanceledAt(order.getUpdatedAt());
        response.setCancelReason(cancelReason);
        return response;
    }
}