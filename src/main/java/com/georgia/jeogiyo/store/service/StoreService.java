package com.georgia.jeogiyo.store.service;

import com.georgia.jeogiyo.global.response.PageResponse;
import com.georgia.jeogiyo.store.dto.request.StoreCreateRequest;
import com.georgia.jeogiyo.store.dto.request.StoreStatusUpdateRequest;
import com.georgia.jeogiyo.store.dto.request.StoreUpdateRequest;
import com.georgia.jeogiyo.store.dto.response.StoreResponse;
import com.georgia.jeogiyo.store.dto.response.StoreSearchResponse;

import java.util.UUID;

public interface StoreService {

    StoreResponse createStore(String loginId, StoreCreateRequest request);

    StoreResponse getStore(UUID storeId);

    PageResponse<StoreSearchResponse> searchStores(
            UUID categoryId,
            String keyword,
            int page,
            int size,
            String sort
    );

    StoreResponse updateStore(UUID storeId, String loginId, StoreUpdateRequest request);

    StoreResponse updateStoreStatus(UUID storeId, String loginId, StoreStatusUpdateRequest request);

    void deleteStore(UUID storeId, String loginId);

    boolean existsActiveStoreByOwnerId(UUID ownerId);

    boolean existsActiveOrderByOwnerId(UUID ownerId);
}
