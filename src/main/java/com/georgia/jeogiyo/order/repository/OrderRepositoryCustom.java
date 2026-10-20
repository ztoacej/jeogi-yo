package com.georgia.jeogiyo.order.repository;

import com.georgia.jeogiyo.order.dto.response.OrderSearchResponse;
import com.georgia.jeogiyo.order.dto.response.OrderStoreSearchResponse;
import com.georgia.jeogiyo.order.entity.OrderStatus;
import com.georgia.jeogiyo.user.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface OrderRepositoryCustom {

    Page<OrderSearchResponse> searchOrders(
            OrderStatus orderStatus,
            Role role,
            UUID userId,
            List<UUID> storeIds,
            Pageable pageable
    );

    Page<OrderStoreSearchResponse> searchOrdersByStore(
            UUID storeId,
            OrderStatus orderStatus,
            Pageable pageable
    );
}