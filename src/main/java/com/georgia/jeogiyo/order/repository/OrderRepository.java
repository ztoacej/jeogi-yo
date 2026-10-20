package com.georgia.jeogiyo.order.repository;

import com.georgia.jeogiyo.order.entity.Order;
import com.georgia.jeogiyo.order.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID>, OrderRepositoryCustom {
    Optional<Order> findByOrderIdAndIsDeletedFalse(UUID orderId);

    boolean existsByStore_StoreIdAndOrderStatusNotInAndIsDeletedFalse(
            UUID storeId, Collection<OrderStatus> terminalStatuses
    );

    // 가게의 폐업·삭제 여부와 무관하게 소유자의 미종료 주문을 확인한다.
    boolean existsByStore_Owner_UserIdAndOrderStatusNotInAndIsDeletedFalse(
            UUID ownerId, Collection<OrderStatus> terminalStatuses
    );

    // 결제 생성의 상태 검증부터 저장까지 주문 취소 UPDATE와 직렬화한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.orderId = :orderId and o.isDeleted = false")
    Optional<Order> findByOrderIdForUpdate(@Param("orderId") UUID orderId);

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