package com.georgia.jeogiyo.store.repository;

import com.georgia.jeogiyo.store.entity.Store;
import com.georgia.jeogiyo.store.entity.StoreStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/*
* QueryDSL 적용: categoryId, keyword, isDeleted=false 조건 검색 구현 */

public interface StoreRepository extends JpaRepository<Store, UUID>, StoreRepositoryCustom {
    Optional<Store> findByStoreIdAndIsDeletedFalse(UUID storeId);
    // 주문 생성·가게 수정·상태 변경·삭제는 같은 행 잠금으로 검사와 변경 사이의 경합을 막는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Store s where s.storeId = :storeId and s.isDeleted = false")
    Optional<Store> findByStoreIdForUpdate(@Param("storeId") UUID storeId);

    boolean existsByOwner_UserIdAndIsDeletedFalse(UUID ownerId);

    boolean existsByOwner_UserIdAndStoreStatusInAndIsDeletedFalse(
            UUID ownerId,
            Collection<StoreStatus> storeStatuses
    );

    boolean existsByCategory_CategoryIdAndIsDeletedFalse(UUID categoryId);
}
