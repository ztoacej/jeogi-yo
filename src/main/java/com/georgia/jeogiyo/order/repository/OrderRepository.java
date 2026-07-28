package com.georgia.jeogiyo.order.repository;

import com.georgia.jeogiyo.order.entity.Order;
import com.georgia.jeogiyo.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID>, OrderRepositoryCustom {
    Optional<Order> findByOrderIdAndIsDeletedFalse(UUID orderId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Order o
            set o.orderStatus = :nextStatus
            where o.orderId = :orderId
              and o.orderStatus = :currentStatus
              and o.isDeleted = false
            """)
    int updateStatusIfCurrent(
            @Param("orderId") UUID orderId,
            @Param("currentStatus") OrderStatus currentStatus,
            @Param("nextStatus") OrderStatus nextStatus
    );
}