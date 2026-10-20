package com.georgia.jeogiyo.product.entity;

import com.georgia.jeogiyo.category.entity.Category;
import com.georgia.jeogiyo.global.entity.BaseEntity;
import com.georgia.jeogiyo.store.entity.Store;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.georgia.jeogiyo.global.exception.BusinessException;
import com.georgia.jeogiyo.global.exception.GlobalErrorCode;

import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "p_product")
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    // 재고의 bulk UPDATE도 버전을 올려 오래된 상품 정보가 재고를 덮어쓰지 못하게 한다.
    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;

    // N(Product) : 1(Store)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    // N(Product) : 1(Category)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "price", nullable = false)
    private Integer price;

    @Column(name = "stock", nullable = false)
    private Integer stock;

    @Column(name = "is_hidden", nullable = false)
    private Boolean isHidden = false;

    /**
     * 상품 생성
     */
    public Product(Store store,
                   Category category,
                   String productName,
                   String description,
                   Integer price,
                   Integer stock,
                   Boolean isHidden) {

        this.store = store;
        this.category = category;
        this.productName = productName;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.isHidden = Boolean.TRUE.equals(isHidden);
    }

    /**
     * 상품 정보 수정 (PATCH)
     * 전달된 값만 수정한다.
     */
    public void update(Category category,
                       String productName,
                       String description,
                       Integer price,
                       Integer stock,
                       Boolean isHidden) {

        if (category != null) {
            this.category = category;
        }

        if (productName != null) {
            this.productName = productName;
        }

        if (description != null) {
            this.description = description;
        }

        if (price != null) {
            this.price = price;
        }

        if (stock != null) {
            this.stock = stock;
        }

        if (isHidden != null) {
            this.isHidden = isHidden;
        }
    }

    /**
     * AI가 생성한 상품 설명으로 변경
     */
    public void updateDescription(String description) {
        this.description = description;
    }

    /**
     * 주문 가능 여부 확인
     * 숨김 상품이 아니고 재고가 1개 이상이어야 주문 가능
     * Order 쪽에서 실수로 삭제된 상품을 findById()로 가져와도 주문을 막도록 추가
     */
    public boolean isOrderable() {
        return !isDeleted()
                && !store.isDeleted()
                && !Boolean.TRUE.equals(isHidden)
                && stock > 0;
    }

    /**
     * 재고 차감
     * TODO 주문 기능 구현 후 예외 정책 재검토
     */
    public void decreaseStock(int quantity) {
        validateQuantity(quantity);

        if (stock < quantity) {
            throw new BusinessException(GlobalErrorCode.INSUFFICIENT_STOCK);
        }

        stock -= quantity;
    }

    /**
     * 재고 복구
     * TODO 주문 취소/환불 정책 확정 후 재검토
     */
    public void restoreStock(int quantity) {
        validateQuantity(quantity);
        stock += quantity;
    }

    /**
     * 수량 유효성 검증
     */
    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
    }
}