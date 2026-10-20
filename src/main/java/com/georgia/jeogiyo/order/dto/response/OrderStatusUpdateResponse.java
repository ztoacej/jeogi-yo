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
public class OrderStatusUpdateResponse {
    private UUID orderId;
    private String orderStatus;
    private LocalDateTime updatedAt;

    public static OrderStatusUpdateResponse of(Order order) {
        OrderStatusUpdateResponse response = new OrderStatusUpdateResponse();
        response.setOrderId(order.getOrderId());
        response.setOrderStatus(order.getOrderStatus().name());
        response.setUpdatedAt(order.getUpdatedAt());
        return response;
    }
}