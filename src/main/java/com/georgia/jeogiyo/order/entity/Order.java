package com.georgia.jeogiyo.order.entity;

import com.georgia.jeogiyo.address.entity.Address;
import com.georgia.jeogiyo.global.entity.BaseEntity;
import com.georgia.jeogiyo.store.entity.Store;
import com.georgia.jeogiyo.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "p_order")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id", nullable = false)
    private Address address;

    @Column(name = "road_address", length = 100, nullable = false)
    private String roadAddress;

    @Column(name = "detail_address", length = 100)
    private String detailAddress;

    @Column(name = "zipcode", length = 10, nullable = false)
    private String zipcode;

    @Column(name = "total_price", nullable = false)
    private Integer totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false)
    private OrderStatus orderStatus;

    public Order(User user, Store store, Address address, String roadAddress,
                 String detailAddress, String zipcode, Integer totalPrice, OrderStatus orderStatus) {
        this.user = user;
        this.store = store;
        this.address = address;
        this.roadAddress = roadAddress;
        this.detailAddress = detailAddress;
        this.zipcode = zipcode;
        this.totalPrice = totalPrice;
        this.orderStatus = orderStatus;
    }

    public void changeStatus(OrderStatus orderStatus) {
        this.orderStatus = orderStatus;
    }

    public void cancel() {
        if (this.orderStatus != OrderStatus.ORDER_REQUESTED) {
            throw new IllegalArgumentException("주문 요청 상태에서만 취소할 수 있습니다.");
        }
        this.orderStatus = OrderStatus.CANCELLED;
    }
    // 연관 엔티티의 식별자 접근 편의 메서드
    public UUID getUserId() {
        return this.user.getUserId();
    }

    public UUID getStoreId() {
        return this.store.getStoreId();
    }

    public UUID getAddressId() {
        return this.address.getAddressId();
    }
}