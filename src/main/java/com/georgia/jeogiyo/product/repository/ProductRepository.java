package com.georgia.jeogiyo.product.repository;

import com.georgia.jeogiyo.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/*
 QueryDSL 적용: storeId, categoryId, keyword, isDeleted=false 조건 검색 구현
*/
public interface ProductRepository extends JpaRepository<Product, UUID>, ProductRepositoryCustom {
    Optional<Product> findByProductIdAndIsDeletedFalse(UUID productId);
    boolean existsByCategory_CategoryIdAndIsDeletedFalse(UUID categoryId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Product p
            set p.stock = p.stock - :quantity
            where p.productId = :productId
              and p.store.storeId = :storeId
              and p.isDeleted = false
              and p.isHidden = false
              and p.stock >= :quantity
              and :quantity > 0
            """)
    int decreaseStockIfEnough( // DB에서 stock >= quantity 조건을 만족할 때만 재고 차감
            @Param("productId") UUID productId,
            @Param("storeId") UUID storeId,
            @Param("quantity") int quantity
    );
}


