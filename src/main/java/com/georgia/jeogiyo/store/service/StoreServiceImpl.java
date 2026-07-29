package com.georgia.jeogiyo.store.service;

import com.georgia.jeogiyo.category.entity.Category;
import com.georgia.jeogiyo.category.repository.CategoryRepository;
import com.georgia.jeogiyo.global.exception.BusinessException;
import com.georgia.jeogiyo.global.exception.GlobalErrorCode;
import com.georgia.jeogiyo.global.response.PageResponse;
import com.georgia.jeogiyo.global.util.PageUtil;
import com.georgia.jeogiyo.review.service.ReviewSummary;
import com.georgia.jeogiyo.review.service.ReviewSummaryReader;
import com.georgia.jeogiyo.store.dto.request.StoreCreateRequest;
import com.georgia.jeogiyo.store.dto.request.StoreStatusUpdateRequest;
import com.georgia.jeogiyo.store.dto.request.StoreUpdateRequest;
import com.georgia.jeogiyo.store.dto.response.StoreResponse;
import com.georgia.jeogiyo.store.dto.response.StoreSearchResponse;
import com.georgia.jeogiyo.store.entity.Store;
import com.georgia.jeogiyo.store.entity.StoreStatus;
import com.georgia.jeogiyo.store.repository.StoreRepository;
import com.georgia.jeogiyo.user.entity.Role;
import com.georgia.jeogiyo.user.entity.User;
import com.georgia.jeogiyo.user.service.UserFinder;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class StoreServiceImpl implements StoreService {
    // loginId는 Controller에서 JWT 인증 객체(Authentication)로부터 전달받는다.

    private final StoreRepository storeRepository;
    private final UserFinder userFinder;
    private final CategoryRepository categoryRepository;
    private final ReviewSummaryReader reviewSummaryReader;
    private final EntityManager entityManager;

    @Override
    public StoreResponse createStore(String loginId, StoreCreateRequest request) {
        User owner = userFinder.getOwnerUserByLoginId(loginId);

        Category category = categoryRepository.findByCategoryIdAndIsDeletedFalse(request.getCategoryId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND_CATEGORY));

        Store store = new Store(
                owner,
                category,
                request.getStoreName(),
                request.getAddress(),
                request.getPhone()
        );

        Store savedStore = storeRepository.save(store);

        log.info("Store created. storeId={}, ownerId={}",
                savedStore.getStoreId(),
                savedStore.getOwner().getUserId());

        return toResponse(savedStore);
    }

    @Override
    @Transactional(readOnly = true)
    public StoreResponse getStore(UUID storeId) {
        Store store = findStore(storeId);
        return toResponse(store);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StoreSearchResponse> searchStores(
            UUID categoryId,
            String keyword,
            int page,
            int size,
            String sort
    ) {
        if (categoryId != null) {
            categoryRepository.findByCategoryIdAndIsDeletedFalse(categoryId)
                    .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND_CATEGORY));
        }

        Pageable pageable = PageUtil.toPageable(page, size, sort);
        Page<StoreSearchResponse> storePage = storeRepository.searchStores(categoryId, keyword, pageable);

        return PageResponse.from(storePage, this::normalizeSearchResponse);
    }

    @Override
    public StoreResponse updateStore(UUID storeId, String loginId, StoreUpdateRequest request) {
        User user = userFinder.getUserByLoginId(loginId);
        Store store = findStore(storeId);

        validateOwnerOrMaster(user, store);

        Category category = request.getCategoryId() != null
                ? categoryRepository.findByCategoryIdAndIsDeletedFalse(request.getCategoryId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND_CATEGORY))
                : null;

        store.update(
                category,
                request.getStoreName(),
                request.getAddress(),
                request.getPhone()
        );

        /*
         * updatedAt은 JPA Auditing의 @LastModifiedDate로 들어가는데, 이 값은 보통 트랜잭션이 flush 될 때 채워집니다.
         * DTO 필드에만 updatedAt을 추가하면 수정 직후 응답에서 updatedAt이 null이거나 이전 값일 수 있으므로 flush 합니다.
         * */
        // 변경 감지 flush 확인
        entityManager.flush();

        log.info("Store updated. storeId={}", store.getStoreId());

        return toResponse(store);
    }

    @Override
    public StoreResponse updateStoreStatus(UUID storeId, String loginId, StoreStatusUpdateRequest request) {
        User user = userFinder.getUserByLoginId(loginId);
        Store store = findStore(storeId);

        validateOwnerOrMaster(user, store);

        store.changeStatus(request.getStoreStatus());

        // 변경 감지 flush 확인
        entityManager.flush();

        return toResponse(store);
    }

    @Override
    public void deleteStore(UUID storeId, String loginId) {
        User user = userFinder.getUserByLoginId(loginId);
        Store store = findStore(storeId);

        validateOwnerOrMaster(user, store);

        store.softDelete(loginId);

        log.info("Store soft deleted. storeId={}, deletedBy={}", store.getStoreId(), loginId);
    }

    // USER 탈퇴 로직에서 OWNER가 활성 가게를 가지고 있는지 확인합니다.
    // 활성 가게 기준은 isDeleted=false AND storeStatus IN (OPEN, CLOSED)입니다.
    // OUT_OF_BUSINESS 상태의 가게는 탈퇴 가능한 상태로 봅니다.
    @Override
    @Transactional(readOnly = true)
    public boolean existsActiveStoreByOwnerId(UUID ownerId) {
        return storeRepository.existsByOwner_UserIdAndStoreStatusInAndIsDeletedFalse(
                ownerId,
                List.of(StoreStatus.OPEN, StoreStatus.CLOSED)
        );
    }

    private Store findStore(UUID storeId) {
        return storeRepository.findByStoreIdAndIsDeletedFalse(storeId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND_STORE));
    }

    private void validateOwnerOrMaster(User user, Store store) {
        boolean isMaster = user.getRole() == Role.MASTER;
        boolean isOwnerOfStore = user.getRole() == Role.OWNER
                && store.getOwner().getUserId().equals(user.getUserId());

        if (!isMaster && !isOwnerOfStore) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN_STORE);
        }
    }

    private StoreResponse toResponse(Store store) {
        ReviewSummary reviewSummary = reviewSummaryReader.getSummary(store.getStoreId());

        return StoreResponse.builder()
                .storeId(store.getStoreId())
                .ownerId(store.getOwner().getUserId())
                .categoryId(store.getCategory().getCategoryId())
                .categoryName(store.getCategory().getCategoryName())
                .storeName(store.getStoreName())
                .address(store.getAddress())
                .phone(store.getPhone())
                .storeStatus(store.getStoreStatus())
                .reviewCount(reviewSummary.reviewCount())
                .averageRating(reviewSummary.averageRating())
                .build();
    }

    private StoreSearchResponse normalizeSearchResponse(StoreSearchResponse response) {
        return StoreSearchResponse.builder()
                .storeId(response.getStoreId())
                .categoryId(response.getCategoryId())
                .categoryName(response.getCategoryName())
                .storeName(response.getStoreName())
                .address(response.getAddress())
                .storeStatus(response.getStoreStatus())
                .reviewCount(response.getReviewCount() == null ? 0L : response.getReviewCount())
                .averageRating(response.getAverageRating() == null ? 0.0 : response.getAverageRating())
                .build();
    }

}
