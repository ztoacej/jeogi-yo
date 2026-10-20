package com.georgia.jeogiyo.ai.controller;

import com.georgia.jeogiyo.ai.entity.AiHistory;
import com.georgia.jeogiyo.ai.entity.AiStatus;
import com.georgia.jeogiyo.ai.repository.AiHistoryRepository;
import com.georgia.jeogiyo.ai.service.AiGeminiService;
import com.georgia.jeogiyo.category.entity.Category;
import com.georgia.jeogiyo.category.repository.CategoryRepository;
import com.georgia.jeogiyo.global.exception.BusinessException;
import com.georgia.jeogiyo.global.exception.GlobalErrorCode;
import com.georgia.jeogiyo.global.jwt.JwtUtil;
import com.georgia.jeogiyo.product.entity.Product;
import com.georgia.jeogiyo.product.repository.ProductRepository;
import com.georgia.jeogiyo.product.service.ProductService;
import com.georgia.jeogiyo.store.entity.Store;
import com.georgia.jeogiyo.store.repository.StoreRepository;
import com.georgia.jeogiyo.support.DomainTestFixture;
import com.georgia.jeogiyo.user.dto.request.UserSignupRequest;
import com.georgia.jeogiyo.user.entity.Role;
import com.georgia.jeogiyo.user.entity.User;
import com.georgia.jeogiyo.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:ai-history-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "SECURITY_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "gemini.api.key=test"
})
@AutoConfigureMockMvc
class AiHistoryApiTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UserRepository userRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private AiHistoryRepository aiHistoryRepository;
    @Autowired private ProductService productService;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockitoBean private AiGeminiService aiGeminiService;

    private User owner;
    private User master;
    private Category category;
    private Store store;
    private AiHistory failure;
    private long productCount;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        owner = userRepository.save(user("o" + suffix, Role.OWNER));
        master = userRepository.save(user("m" + suffix, Role.MASTER));
        category = categoryRepository.save(new Category("ai" + suffix));
        store = storeRepository.save(new Store(owner, category, "AI 테스트", "서울특별시 종로구 세종대로 172", "02-0000-0000"));
        productCount = productRepository.count();
        when(aiGeminiService.generateDescription(anyString()))
                .thenThrow(new BusinessException(GlobalErrorCode.AI_GENERATION_FAILED));
        // 실제 상품 등록 트랜잭션은 롤백되고 REQUIRES_NEW 실패 이력만 커밋되어야 한다.
        assertThatThrownBy(() -> productService.createProduct(store.getStoreId(), owner.getLoginId(),
                DomainTestFixture.productCreateRequest(category.getCategoryId(), true)))
                .isInstanceOf(BusinessException.class).hasMessage(GlobalErrorCode.AI_GENERATION_FAILED.getMessage());
        failure = aiHistoryRepository.searchAiHistories(AiStatus.FAIL, null, owner.getUserId(),
                org.springframework.data.domain.PageRequest.of(0, 10)).getContent().get(0);
    }

    @Test
    @DisplayName("상품 등록 롤백 후에도 실패 이력을 상세 조회할 수 있고 상품 ID는 null이다")
    void failedRegistration_detailAvailable() throws Exception {
        assertThat(productRepository.count()).isEqualTo(productCount);
        assertThat(failure.getProduct()).isNull();
        mockMvc.perform(get("/api/v1/ai-histories/{id}", failure.getAiHistoryId())
                        .header("Authorization", token(master)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiHistoryId").value(failure.getAiHistoryId().toString()))
                .andExpect(jsonPath("$.data.productId").value(nullValue()))
                .andExpect(jsonPath("$.data.userId").value(owner.getUserId().toString()))
                .andExpect(jsonPath("$.data.aiStatus").value("FAIL"))
                .andExpect(jsonPath("$.data.requestText").value(failure.getRequestText()))
                .andExpect(jsonPath("$.data.errorMessage").value(GlobalErrorCode.AI_GENERATION_FAILED.getMessage()));
    }

    @Test
    @DisplayName("상품 없는 실패 이력과 성공 이력이 섞여도 목록·상태·상품 필터가 정상 동작한다")
    void mixedHistoryList_andFilters() throws Exception {
        Product product = productRepository.save(new Product(store, category, "상품", "설명", 12000, 10, false));
        AiHistory success = aiHistoryRepository.save(AiHistory.success(owner, product, "요청", "성공 응답", "test-model"));
        mockMvc.perform(get("/api/v1/ai-histories").param("userId", owner.getUserId().toString())
                        .header("Authorization", token(master)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[*].aiHistoryId",
                        containsInAnyOrder(failure.getAiHistoryId().toString(), success.getAiHistoryId().toString())));
        mockMvc.perform(get("/api/v1/ai-histories").param("userId", owner.getUserId().toString()).param("aiStatus", "FAIL")
                        .header("Authorization", token(master)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].productId").value(nullValue()))
                .andExpect(jsonPath("$.data.content[0].errorMessage").value(GlobalErrorCode.AI_GENERATION_FAILED.getMessage()));
        mockMvc.perform(get("/api/v1/ai-histories").param("productId", product.getProductId().toString())
                        .header("Authorization", token(master)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].productId").value(product.getProductId().toString()));
        mockMvc.perform(get("/api/v1/ai-histories/{id}", success.getAiHistoryId())
                        .header("Authorization", token(master)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(product.getProductId().toString()));
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"OWNER", "CUSTOMER"})
    @DisplayName("OWNER와 CUSTOMER는 상품 없는 실패 이력도 조회할 수 없다")
    void nonMaster_forbidden(Role role) throws Exception {
        User actor = role == Role.OWNER ? owner
                : userRepository.save(user("c" + UUID.randomUUID().toString().substring(0, 8), role));
        mockMvc.perform(get("/api/v1/ai-histories/{id}", failure.getAiHistoryId()).header("Authorization", token(actor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/ai-histories").header("Authorization", token(actor)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("삭제된 실패 이력은 상세·목록 조회에서 계속 제외된다")
    void deletedFailure_excluded() throws Exception {
        failure.softDelete(master.getLoginId());
        aiHistoryRepository.save(failure);
        mockMvc.perform(get("/api/v1/ai-histories/{id}", failure.getAiHistoryId()).header("Authorization", token(master)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/ai-histories").param("userId", owner.getUserId().toString())
                        .header("Authorization", token(master)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    private String token(User user) {
        return jwtUtil.createToken(user.getLoginId(), user.getRole());
    }

    private User user(String id, Role role) {
        User user = User.customerCreate(new UserSignupRequest(id, "Test1234!", id + "Nick",
                "010-0000-0000", id + "@test.com"), passwordEncoder);
        user.changeRole(role);
        return user;
    }
}
