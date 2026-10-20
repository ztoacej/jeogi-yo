package com.georgia.jeogiyo.order.dto.response;

import com.georgia.jeogiyo.order.entity.Order;
import com.georgia.jeogiyo.order.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class OrderCreateResponse {

    private UUID orderId;
    private UUID storeId;
    private String address;
    private String orderStatus;
    private Integer totalPrice;
    private LocalDateTime createdAt;

    public static OrderCreateResponse of(Order order) {
        OrderCreateResponse response = new OrderCreateResponse();
        response.setOrderId(order.getOrderId());
        response.setStoreId(order.getStore().getStoreId());
        response.setAddress(order.getRoadAddress() + " " + order.getDetailAddress());
        response.setOrderStatus(order.getOrderStatus().name());
        response.setTotalPrice(order.getTotalPrice());
        response.setCreatedAt(order.getCreatedAt());
        return response;
    }
}