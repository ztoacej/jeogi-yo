package com.georgia.jeogiyo.product.service;

import com.georgia.jeogiyo.order.service.OrderService;

import com.georgia.jeogiyo.address.dto.request.AddressCreateRequest;
import com.georgia.jeogiyo.address.entity.Address;
import com.georgia.jeogiyo.address.repository.AddressRepository;
import com.georgia.jeogiyo.category.entity.Category;
import com.georgia.jeogiyo.category.repository.CategoryRepository;
import com.georgia.jeogiyo.global.exception.BusinessException;
import com.georgia.jeogiyo.global.exception.GlobalErrorCode;
import com.georgia.jeogiyo.order.dto.request.OrderCreateRequest;
import com.georgia.jeogiyo.order.repository.OrderRepository;
import com.georgia.jeogiyo.orderitem.repository.OrderItemRepository;
import com.georgia.jeogiyo.product.entity.Product;
import com.georgia.jeogiyo.product.repository.ProductRepository;
import com.georgia.jeogiyo.store.entity.Store;
import com.georgia.jeogiyo.store.entity.StoreStatus;
import com.georgia.jeogiyo.store.repository.StoreRepository;
import com.georgia.jeogiyo.user.dto.request.UserSignupRequest;
import com.georgia.jeogiyo.user.entity.Role;
import com.georgia.jeogiyo.user.entity.User;
import com.georgia.jeogiyo.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.georgia.jeogiyo.ai.service.AiService;
import com.georgia.jeogiyo.ai.service.AiGeminiService;
import com.georgia.jeogiyo.ai.repository.AiHistoryRepository;
import com.georgia.jeogiyo.product.dto.request.ProductUpdateRequest;
import com.georgia.jeogiyo.order.dto.request.OrderCancelRequest;
import com.georgia.jeogiyo.order.entity.OrderStatus;
import com.georgia.jeogiyo.support.DomainTestFixture;
import com.georgia.jeogiyo.global.jwt.JwtUtil;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.dao.OptimisticLockingFailureException;
import jakarta.persistence.OptimisticLockException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:product-stock;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "SECURITY_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "gemini.api.key=test"
})
@AutoConfigureMockMvc
class ProductStockConcurrencyTest {
    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Autowired private ProductService productService;
    @Autowired private AiService aiService;
    @Autowired private AiHistoryRepository aiHistoryRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @MockitoBean private AiGeminiService aiGeminiService;
    private User owner;
    private User customer;
    private Store store;
    private Category category;
    private Address address;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        owner = userRepository.save(user("o" + suffix, Role.OWNER));
        customer = userRepository.save(user("c" + suffix, Role.CUSTOMER));
        category = categoryRepository.save(new Category("amount" + suffix));
        Store newStore = new Store(owner, category, "금액 테스트", "서울특별시 종로구 세종대로 172", "02-0000-0000");
        newStore.changeStatus(StoreStatus.OPEN);
        store = storeRepository.save(newStore);
        address = addressRepository.save(Address.create(customer,
                new AddressCreateRequest("서울특별시 종로구 세종대로 172", "101호", "03154", true)));
    }

    @ParameterizedTest
    @CsvSource({
            "UPDATE,ORDER", "STOCK,ORDER", "DELETE,ORDER", "AI,ORDER",
            "UPDATE,CANCEL", "STOCK,CANCEL", "DELETE,CANCEL", "AI,CANCEL"
    })
    @DisplayName("상품을 읽은 뒤 주문·취소로 재고가 바뀌면 오래된 수정·삭제·AI 변경을 거절한다")
    void staleMutation_doesNotOverwriteStock(String mutation, String stockAction) {
        Product product = product(12000, 10);
        UUID pendingOrder = stockAction.equals("CANCEL")
                ? orderService.createOrder(customer.getLoginId(), request(List.of(item(product, 1)))).getOrderId()
                : null;
        if (mutation.equals("AI")) {
            when(aiGeminiService.generateDescription(anyString())).thenReturn("새로운 AI 상품 설명");
        }
        long historyCount = aiHistoryRepository.count();
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            // 수정 트랜잭션이 상품을 읽은 뒤, 별도 트랜잭션의 재고 변경이 먼저 커밋된다.
            productRepository.findById(product.getProductId()).orElseThrow();
            CompletableFuture.runAsync(() -> {
                if (pendingOrder == null) {
                    orderService.createOrder(customer.getLoginId(), request(List.of(item(product, 1))));
                } else {
                    OrderCancelRequest cancel = new OrderCancelRequest();
                    cancel.setCancelReason("재고 복구 경합 검증");
                    orderService.cancelOrder(customer.getLoginId(), pendingOrder, cancel);
                }
            }).orTimeout(10, TimeUnit.SECONDS).join();
            mutate(product.getProductId(), mutation);
        })).isInstanceOfAny(OptimisticLockingFailureException.class, OptimisticLockException.class);

        Product stored = productRepository.findById(product.getProductId()).orElseThrow();
        assertThat(stored.getStock()).isEqualTo(pendingOrder == null ? 9 : 10);
        assertThat(stored.getProductName()).isEqualTo("테스트 상품");
        assertThat(stored.getDescription()).isEqualTo("설명");
        assertThat(stored.isDeleted()).isFalse();
        assertThat(aiHistoryRepository.count()).isEqualTo(historyCount);
        if (pendingOrder != null) {
            assertThat(orderRepository.findById(pendingOrder).orElseThrow().getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        }
    }

    @ParameterizedTest
    @CsvSource({"UPDATE", "STOCK", "DELETE", "AI"})
    @DisplayName("동시 재고 변경이 없으면 상품 수정·삭제·AI 변경은 정상 처리한다")
    void uncontendedMutation_succeeds(String mutation) {
        Product product = product(12000, 10);
        if (mutation.equals("AI")) {
            when(aiGeminiService.generateDescription(anyString())).thenReturn("새로운 AI 상품 설명");
        }
        mutate(product.getProductId(), mutation);
        Product stored = productRepository.findById(product.getProductId()).orElseThrow();
        assertThat(stored.getStock()).isEqualTo(mutation.equals("STOCK") ? 25 : 10);
        assertThat(stored.isDeleted()).isEqualTo(mutation.equals("DELETE"));
        if (mutation.equals("UPDATE")) {
            assertThat(stored.getProductName()).isEqualTo("이름만 수정");
        } else if (mutation.equals("AI")) {
            assertThat(stored.getDescription()).isEqualTo("새로운 AI 상품 설명");
        }
    }

    @Test
    @DisplayName("AI 설명 생성 도중 주문이 완료되면 API는 409를 반환하고 재고와 이력을 보호한다")
    void conflictingAiRequest_returns409() throws Exception {
        Product product = product(12000, 10);
        long historyCount = aiHistoryRepository.count();
        when(aiGeminiService.generateDescription(anyString())).thenAnswer(invocation -> {
            CompletableFuture.runAsync(() -> orderService.createOrder(customer.getLoginId(),
                    request(List.of(item(product, 1))))).orTimeout(10, TimeUnit.SECONDS).join();
            return "새로운 AI 상품 설명";
        });
        mockMvc.perform(post("/api/v1/products/{id}/ai-description", product.getProductId())
                        .header("Authorization", jwtUtil.createToken(owner.getLoginId(), Role.OWNER))
                        .contentType("application/json")
                        .content("{\"requestText\":\"상품 설명을 작성해줘\"}"))
                .andExpect(status().isConflict());
        Product stored = productRepository.findById(product.getProductId()).orElseThrow();
        assertThat(stored.getStock()).isEqualTo(9);
        assertThat(stored.getDescription()).isEqualTo("설명");
        assertThat(aiHistoryRepository.count()).isEqualTo(historyCount);
    }

    private void mutate(UUID productId, String mutation) {
        switch (mutation) {
            case "DELETE" -> productService.deleteProduct(productId, owner.getLoginId());
            case "AI" -> aiService.createAiDescription(productId, owner.getLoginId(),
                    DomainTestFixture.aiDescriptionRequest("상품 설명을 작성해줘"));
            default -> {
                ProductUpdateRequest update = new ProductUpdateRequest();
                if (mutation.equals("STOCK")) {
                    ReflectionTestUtils.setField(update, "stock", 25);
                } else {
                    ReflectionTestUtils.setField(update, "productName", "이름만 수정");
                }
                productService.updateProduct(productId, owner.getLoginId(), update);
            }
        }
    }

    private Product product(int price, int stock) {
        return productRepository.save(new Product(store, category, "테스트 상품", "설명", price, stock, false));
    }

    private OrderCreateRequest.OrderItemRequest item(Product product, int quantity) {
        OrderCreateRequest.OrderItemRequest item = new OrderCreateRequest.OrderItemRequest();
        item.setProductId(product.getProductId());
        item.setQuantity(quantity);
        return item;
    }

    private OrderCreateRequest request(List<OrderCreateRequest.OrderItemRequest> items) {
        OrderCreateRequest request = new OrderCreateRequest();
        request.setStoreId(store.getStoreId());
        request.setAddressId(address.getAddressId());
        request.setItems(items);
        return request;
    }

    private User user(String id, Role role) {
        User user = User.customerCreate(new UserSignupRequest(id, "Test1234!", id + "Nick",
                "010-0000-0000", id + "@test.com"), passwordEncoder);
        user.changeRole(role);
        return user;
    }
}
