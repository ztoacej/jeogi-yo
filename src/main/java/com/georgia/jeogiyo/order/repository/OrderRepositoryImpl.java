package com.georgia.jeogiyo.order.repository;

import com.georgia.jeogiyo.order.dto.response.OrderSearchResponse;
import com.georgia.jeogiyo.order.dto.response.OrderStoreSearchResponse;
import com.georgia.jeogiyo.order.entity.OrderStatus;
import com.georgia.jeogiyo.order.entity.QOrder;
import com.georgia.jeogiyo.store.entity.QStore;
import com.georgia.jeogiyo.user.entity.QUser;
import com.georgia.jeogiyo.user.entity.Role;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<OrderSearchResponse> searchOrders(
            OrderStatus orderStatus,
            Role role,
            UUID userId,
            List<UUID> storeIds,
            Pageable pageable
    ) {
        QOrder order = QOrder.order;
        QStore store = QStore.store;

        BooleanBuilder condition = new BooleanBuilder();
        condition.and(order.isDeleted.isFalse());

        if (orderStatus != null) {
            condition.and(order.orderStatus.eq(orderStatus));
        }

        applyVisibilityCondition(condition, order, role, userId, storeIds);

        List<OrderSearchResponse> content = queryFactory
                .select(Projections.bean(
                        OrderSearchResponse.class,
                        order.orderId.as("orderId"),
                        order.store.storeId.as("storeId"),
                        store.storeName.as("storeName"),
                        order.orderStatus.stringValue().as("orderStatus"),
                        order.totalPrice.as("totalPrice"),
                        order.createdAt.as("createdAt")
                ))
                .from(order)
                .leftJoin(order.store, store).on(store.isDeleted.isFalse())
                .where(condition)
                .orderBy(createdAtOrder(pageable, order))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(order.count())
                .from(order)
                .where(condition);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    @Override
    public Page<OrderStoreSearchResponse> searchOrdersByStore(
            UUID storeId,
            OrderStatus orderStatus,
            Pageable pageable
    ) {
        QOrder order = QOrder.order;
        QUser user = QUser.user;

        BooleanBuilder condition = new BooleanBuilder();
        condition.and(order.isDeleted.isFalse());
        condition.and(order.store.storeId.eq(storeId));

        if (orderStatus != null) {
            condition.and(order.orderStatus.eq(orderStatus));
        }

        List<OrderStoreSearchResponse> content = queryFactory
                .select(Projections.bean(
                        OrderStoreSearchResponse.class,
                        order.orderId.as("orderId"),
                        new CaseBuilder()
                                .when(user.isDeleted.isFalse())
                                .then(user.nickname)
                                .otherwise("탈퇴한 회원")
                                .as("customerName"),
                        order.orderStatus.stringValue().as("orderStatus"),
                        order.totalPrice.as("totalPrice"),
                        order.createdAt.as("createdAt")
                ))
                .from(order)
                .leftJoin(order.user, user)
                .where(condition)
                .orderBy(createdAtOrder(pageable, order))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(order.count())
                .from(order)
                .where(condition);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private void applyVisibilityCondition(
            BooleanBuilder condition, QOrder order, Role role, UUID userId, List<UUID> storeIds
    ) {
        if (role == Role.MASTER) {
            return;
        }
        if (role == Role.CUSTOMER) {
            condition.and(order.user.userId.eq(userId));
            return;
        }
        if (storeIds == null || storeIds.isEmpty()) {
            condition.and(order.orderId.isNull());
        } else {
            condition.and(order.store.storeId.in(storeIds));
        }
    }

    private OrderSpecifier<?> createdAtOrder(Pageable pageable, QOrder order) {
        boolean ascending = pageable.getSort().getOrderFor("createdAt") != null
                && pageable.getSort().getOrderFor("createdAt").isAscending();
        return new OrderSpecifier<>(ascending ? com.querydsl.core.types.Order.ASC : com.querydsl.core.types.Order.DESC, order.createdAt);
    }
}