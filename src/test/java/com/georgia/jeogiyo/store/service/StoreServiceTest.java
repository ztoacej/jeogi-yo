package com.georgia.jeogiyo.store.service;

import com.georgia.jeogiyo.category.entity.Category;
import com.georgia.jeogiyo.category.repository.CategoryRepository;
import com.georgia.jeogiyo.global.exception.BusinessException;
import com.georgia.jeogiyo.global.exception.GlobalErrorCode;
import com.georgia.jeogiyo.global.response.PageResponse;
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
import com.georgia.jeogiyo.support.DomainTestFixture;
import com.georgia.jeogiyo.user.entity.User;
import com.georgia.jeogiyo.user.service.UserFinder;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Optional;

import static com.georgia.jeogiyo.support.DomainTestFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

/**
 * StoreServiceImpl 단위 테스트입니다.
 *
 * - 가게 도메인의 핵심 규칙을 DB 없이 검증합니다.
 * - OWNER만 가게를 등록할 수 있습니다.
 * - OWNER 본인 가게 또는 MASTER만 수정/상태변경/삭제할 수 있습니다.
 * - soft delete된 가게는 조회/수정 대상에서 제외되는지 검증합니다.
 * - OWNER 탈퇴 검증에 사용하는 활성 가게 기준을 검증합니다.
 * - 공통 예외 처리 적용에 따라 BusinessException과 GlobalErrorCode 기준으로 검증합니다.
 * - Service 단위 테스트에서는 loginId를 직접 전달해 비즈니스 로직을 검증하고,
 *   인증 사용자 추출 흐름은 Controller/통합 테스트에서 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class StoreServiceTest {

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private UserFinder userFinder;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ReviewSummaryReader reviewSummaryReader;

    @Mock
    private EntityManager entityManager;

    private StoreServiceImpl storeService;

    @BeforeEach
    void setUp() {
        storeService = new StoreServiceImpl(
                storeRepository,
                userFinder,
                categoryRepository,
                reviewSummaryReader,
                entityManager
        );
    }

    @Test
    @DisplayName("OWNER는 가게를 등록할 수 있다")
    void createStore_owner_success() {
        // given: OWNER 사용자와 존재하는 카테고리가 준비되어 있다.
        User owner = DomainTestFixture.owner();
        Category category = DomainTestFixture.category();
        StoreCreateRequest request = DomainTestFixture.storeCreateRequest(CATEGORY_ID);

        given(userFinder.getOwnerUserByLoginId(OWNER_LOGIN_ID)).willReturn(owner);
        given(categoryRepository.findByCategoryIdAndIsDeletedFalse(CATEGORY_ID)).willReturn(Optional.of(category));
        given(storeRepository.save(any(Store.class))).willAnswer(invocation -> {
            Store store = invocation.getArgument(0);
            DomainTestFixture.markPersisted(store, STORE_ID);
            return store;
        });

        given(reviewSummaryReader.getSummary(STORE_ID))
                .willReturn(new ReviewSummary(0, 0.0));

        // when: OWNER가 가게 등록을 요청한다.
        StoreResponse response = storeService.createStore(OWNER_LOGIN_ID, request);

        // then: 가게가 저장되고 기본 CLOSED 상태로 응답된다.
        assertThat(response.getStoreId()).isEqualTo(STORE_ID);
        assertThat(response.getOwnerId()).isEqualTo(owner.getUserId());
        assertThat(response.getCategoryId()).isEqualTo(CATEGORY_ID);
        assertThat(response.getStoreName()).isEqualTo("테스트 신규 가게");
        assertThat(response.getStoreStatus()).isEqualTo(StoreStatus.CLOSED);
//        assertThat(response.getIsDeleted()).isFalse();
    }

    @Test
    @DisplayName("CUSTOMER는 가게를 등록할 수 없다")
    void createStore_customer_fail() {
        // given: CUSTOMER 사용자가 가게 등록을 시도한다.
        User customer = DomainTestFixture.customer();
        StoreCreateRequest request = DomainTestFixture.storeCreateRequest(CATEGORY_ID);

        given(userFinder.getOwnerUserByLoginId(CUSTOMER_LOGIN_ID))
                .willThrow(new BusinessException(GlobalErrorCode.FORBIDDEN));

        // when & then: OWNER 권한이 아니므로 카테고리 조회나 저장까지 가지 않고 실패한다.
        assertThatThrownBy(() -> storeService.createStore(CUSTOMER_LOGIN_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(GlobalErrorCode.FORBIDDEN.getMessage());

        verifyNoInteractions(categoryRepository, storeRepository);
    }

    @Test
    @DisplayName("존재하지 않는 categoryId로 가게를 등록할 수 없다")
    void createStore_missingCategory_fail() {
        // given: OWNER는 맞지만 요청 categoryId가 존재하지 않는다.
        User owner = DomainTestFixture.owner();
        StoreCreateRequest request = DomainTestFixture.storeCreateRequest(CATEGORY_ID);

        given(userFinder.getOwnerUserByLoginId(OWNER_LOGIN_ID)).willReturn(owner);
        given(categoryRepository.findByCategoryIdAndIsDeletedFalse(CATEGORY_ID)).willReturn(Optional.empty());

        // when & then: 잘못된 카테고리로는 가게를 저장하지 않는다.
        assertThatThrownBy(() -> storeService.createStore(OWNER_LOGIN_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(GlobalErrorCode.NOT_FOUND_CATEGORY.getMessage());

        then(storeRepository).should(never()).save(any(Store.class));
    }

    @Test
    @DisplayName("OWNER는 본인 가게를 수정할 수 있다")
    void updateStore_owner_success() {
        // given: OWNER 본인 소유의 가게와 수정 요청이 있다.
        User owner = DomainTestFixture.owner();
        Category category = DomainTestFixture.category();
        Store store = DomainTestFixture.store(owner, category);
        StoreUpdateRequest request = DomainTestFixture.storeUpdateRequest(CATEGORY_ID);

        given(userFinder.getUserByLoginId(OWNER_LOGIN_ID)).willReturn(owner);
        given(storeRepository.findByStoreIdAndIsDeletedFalse(STORE_ID)).willReturn(Optional.of(store));
        given(categoryRepository.findByCategoryIdAndIsDeletedFalse(CATEGORY_ID)).willReturn(Optional.of(category));

        given(reviewSummaryReader.getSummary(STORE_ID))
                .willReturn(new ReviewSummary(0, 0.0));

        // when: OWNER가 본인 가게를 수정한다.
        StoreResponse response = storeService.updateStore(STORE_ID, OWNER_LOGIN_ID, request);

        // then: 수정값이 응답에 반영되고 updatedAt 반영을 위해 flush가 호출된다.
        assertThat(response.getStoreName()).isEqualTo("테스트 가게 수정");
        assertThat(response.getAddress()).isEqualTo("서울시 테스트구 수정로 20");
        assertThat(response.getPhone()).isEqualTo("02-2222-2222");
        then(entityManager).should().flush();
    }

    @Test
    @DisplayName("OWNER는 타인 가게를 수정할 수 없다")
    void updateStore_otherOwner_fail() {
        // given: owner01이 owner02 소유 가게를 수정하려고 한다.
        User owner = DomainTestFixture.owner();
        User otherOwner = DomainTestFixture.otherOwner();
        Category category = DomainTestFixture.category();
        Store otherOwnerStore = DomainTestFixture.otherOwnerStore(otherOwner, category);
        StoreUpdateRequest request = DomainTestFixture.storeUpdateRequest(null);

        given(userFinder.getUserByLoginId(OWNER_LOGIN_ID)).willReturn(owner);
        given(storeRepository.findByStoreIdAndIsDeletedFalse(DomainTestFixture.OTHER_OWNER_STORE_ID))
                .willReturn(Optional.of(otherOwnerStore));

        // when & then: OWNER는 본인 가게가 아니면 수정할 수 없다.
        assertThatThrownBy(() -> storeService.updateStore(
                DomainTestFixture.OTHER_OWNER_STORE_ID,
                OWNER_LOGIN_ID,
                request
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage(GlobalErrorCode.FORBIDDEN_STORE.getMessage());

        verify(entityManager, never()).flush();
    }

    @Test
    @DisplayName("MASTER는 다른 OWNER의 가게도 수정할 수 있다")
    void updateStore_master_success() {
        // given: MASTER와 다른 OWNER의 가게가 있다.
        User master = DomainTestFixture.master();
        User otherOwner = DomainTestFixture.otherOwner();
        Category category = DomainTestFixture.category();
        Store otherOwnerStore = DomainTestFixture.otherOwnerStore(otherOwner, category);
        StoreUpdateRequest request = DomainTestFixture.storeUpdateRequest(null);

        given(userFinder.getUserByLoginId(MASTER_LOGIN_ID)).willReturn(master);
        given(storeRepository.findByStoreIdAndIsDeletedFalse(DomainTestFixture.OTHER_OWNER_STORE_ID))
                .willReturn(Optional.of(otherOwnerStore));

        given(reviewSummaryReader.getSummary(DomainTestFixture.OTHER_OWNER_STORE_ID))
                .willReturn(new ReviewSummary(0, 0.0));

        // when: MASTER가 가게를 수정한다.
        StoreResponse response = storeService.updateStore(
                DomainTestFixture.OTHER_OWNER_STORE_ID,
                MASTER_LOGIN_ID,
                request
        );

        // then: 소유자와 무관하게 수정 가능하다.
        assertThat(response.getStoreName()).isEqualTo("테스트 가게 수정");
        then(entityManager).should().flush();
    }

    @Test
    @DisplayName("OUT_OF_BUSINESS 상태의 가게는 다시 OPEN으로 바꿀 수 없다")
    void updateStoreStatus_outOfBusinessToOpen_fail() {
        // given: 이미 폐업 처리된 가게가 있다.
        User owner = DomainTestFixture.owner();
        Category category = DomainTestFixture.category();
        Store store = DomainTestFixture.store(owner, category);
        store.changeStatus(StoreStatus.OUT_OF_BUSINESS);
        StoreStatusUpdateRequest request = DomainTestFixture.storeStatusRequest(StoreStatus.OPEN);

        given(userFinder.getUserByLoginId(OWNER_LOGIN_ID)).willReturn(owner);
        given(storeRepository.findByStoreIdAndIsDeletedFalse(STORE_ID)).willReturn(Optional.of(store));

        // when & then: 폐업 가게는 상태 변경을 다시 허용하지 않는다.
        assertThatThrownBy(() -> storeService.updateStoreStatus(STORE_ID, OWNER_LOGIN_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(GlobalErrorCode.INVALID_STORE_STATUS.getMessage());
        verify(entityManager, never()).flush();
    }

    @Test
    @DisplayName("삭제된 가게는 수정 대상에서 제외된다")
    void updateStore_deletedStore_fail() {
        // given: repository 메서드가 isDeleted=false 조건으로 조회하므로 삭제된 가게는 Optional.empty로 표현한다.
        User owner = DomainTestFixture.owner();
        StoreUpdateRequest request = DomainTestFixture.storeUpdateRequest(null);

        given(userFinder.getUserByLoginId(OWNER_LOGIN_ID)).willReturn(owner);
        given(storeRepository.findByStoreIdAndIsDeletedFalse(STORE_ID)).willReturn(Optional.empty());

        // when & then: 삭제된 가게는 없는 가게처럼 처리한다.
        assertThatThrownBy(() -> storeService.updateStore(STORE_ID, OWNER_LOGIN_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(GlobalErrorCode.NOT_FOUND_STORE.getMessage());
    }

    @Test
    @DisplayName("가게 검색은 page 음수와 허용되지 않는 size를 보정한다")
    void searchStores_pageAndSize_normalized() {
        // given: 카테고리 조건이 존재하고, 검색 repository가 리뷰 집계가 포함된 목록 응답을 반환한다.
        Category category = DomainTestFixture.category();

        StoreSearchResponse storeSearchResponse = StoreSearchResponse.builder()
                .storeId(STORE_ID)
                .categoryId(CATEGORY_ID)
                .categoryName("한식")
                .storeName("테스트 가게")
                .address("서울시 테스트구")
                .storeStatus(StoreStatus.OPEN)
                .reviewCount(2L)
                .averageRating(4.333333)
                .build();

        given(categoryRepository.findByCategoryIdAndIsDeletedFalse(CATEGORY_ID))
                .willReturn(Optional.of(category));

        given(storeRepository.searchStores(eq(CATEGORY_ID), eq("테스트"), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(storeSearchResponse)));

        // when: page=-1, size=20처럼 정책 밖의 요청이 들어온다.
        PageResponse<StoreSearchResponse> response =
                storeService.searchStores(CATEGORY_ID, "테스트", -1, 20, "desc");

        // then: page는 0, size는 10으로 보정되어 repository에 전달된다.
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        then(storeRepository).should().searchStores(eq(CATEGORY_ID), eq("테스트"), pageableCaptor.capture());

        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
        assertThat(response.getPage()).isZero();

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getReviewCount()).isEqualTo(2L);
        assertThat(response.getContent().get(0).getAverageRating())
                .isCloseTo(4.333333, within(0.000001));
    }

    @Test
    @DisplayName("OWNER 탈퇴 검증은 OPEN/CLOSED 상태의 가게만 활성 가게로 본다")
    void existsActiveStoreByOwnerId_openOrClosed_true() {
        given(storeRepository.existsByOwner_UserIdAndStoreStatusInAndIsDeletedFalse(
                OWNER_ID,
                List.of(StoreStatus.OPEN, StoreStatus.CLOSED)
        )).willReturn(true);

        boolean result = storeService.existsActiveStoreByOwnerId(OWNER_ID);

        assertThat(result).isTrue();

        then(storeRepository).should()
                .existsByOwner_UserIdAndStoreStatusInAndIsDeletedFalse(
                        OWNER_ID,
                        List.of(StoreStatus.OPEN, StoreStatus.CLOSED)
                );
    }

    @Test
    @DisplayName("OWNER 탈퇴 검증에서 OUT_OF_BUSINESS 상태의 가게는 활성 가게로 보지 않는다")
    void existsActiveStoreByOwnerId_outOfBusiness_false() {
        given(storeRepository.existsByOwner_UserIdAndStoreStatusInAndIsDeletedFalse(
                OWNER_ID,
                List.of(StoreStatus.OPEN, StoreStatus.CLOSED)
        )).willReturn(false);

        boolean result = storeService.existsActiveStoreByOwnerId(OWNER_ID);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("가게 상세 조회는 리뷰 수와 평균 평점을 함께 반환한다")
    void getStore_reviewSummary_success() {
        User owner = DomainTestFixture.owner();
        Category category = DomainTestFixture.category();
        Store store = DomainTestFixture.store(owner, category);

        given(storeRepository.findByStoreIdAndIsDeletedFalse(STORE_ID)).willReturn(Optional.of(store));
        given(reviewSummaryReader.getSummary(STORE_ID))
                .willReturn(new ReviewSummary(3, 4.3));

        StoreResponse response = storeService.getStore(STORE_ID);

        assertThat(response.getReviewCount()).isEqualTo(3);
        assertThat(response.getAverageRating()).isEqualTo(4.3);
    }
}
