package com.georgia.jeogiyo.order.dto.response;

import com.georgia.jeogiyo.order.entity.Order;
import com.georgia.jeogiyo.orderitem.entity.OrderItem;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class OrderDetailResponse {

    private UUID orderId;
    private UUID storeId;
    private UUID addressId;
    private String orderStatus;
    private Integer totalPrice;
    private LocalDateTime createdAt;
    private List<OrderItemResponse> items;

    public static OrderDetailResponse of(Order order, List<OrderItem> orderItems) {
        OrderDetailResponse response = new OrderDetailResponse();
        response.setOrderId(order.getOrderId());
        response.setStoreId(order.getStore().getStoreId());
        response.setAddressId(order.getAddress().getAddressId());
        response.setOrderStatus(order.getOrderStatus().name());
        response.setTotalPrice(order.getTotalPrice());
        response.setCreatedAt(order.getCreatedAt());
        response.setItems(
                orderItems.stream()
                        .map(OrderItemResponse::of)
                        .toList()
        );
        return response;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class OrderItemResponse {
        private UUID productId;
        private String productName;
        private Integer quantity;
        private Integer unitPrice;
        private Integer itemTotalPrice;

        public static OrderItemResponse of(OrderItem item) {
            OrderItemResponse response = new OrderItemResponse();
            response.setProductId(item.getProductId());
            response.setProductName(item.getProductName());
            response.setQuantity(item.getQuantity());
            response.setUnitPrice(item.getUnitPrice());
            response.setItemTotalPrice(item.getItemTotalPrice());
            return response;
        }
    }
}