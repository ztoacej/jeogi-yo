package com.georgia.jeogiyo.address.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import com.georgia.jeogiyo.address.entity.Address;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.georgia.jeogiyo.address.dto.request.AddressCreateRequest;
import com.georgia.jeogiyo.address.dto.request.AddressUpdateRequest;
import com.georgia.jeogiyo.address.service.AddressService;
import com.georgia.jeogiyo.user.dto.request.UserLoginRequest;
import com.georgia.jeogiyo.user.dto.request.UserSignupRequest;
import com.georgia.jeogiyo.user.dto.response.UserLoginResponse;
import com.georgia.jeogiyo.user.entity.Role;
import com.georgia.jeogiyo.user.fixture.UserFix;
import com.georgia.jeogiyo.user.service.UserService;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class AddressApiTest {

	@Autowired
	private MockMvc mockMvc;
	
	private final ObjectMapper objectMapper = new ObjectMapper()
			.registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	
	@Autowired
	private EntityManager em;
	
	@Autowired
	private UserService userService;
	
	@Autowired
	private AddressService addressService;
	
	private UserSignupRequest userSignupRequest;
	
	private String loginId;
	
	@BeforeEach
	void setUp() {
		userSignupRequest = UserFix.getUserSignupRequest();
		loginId = userService.signup(userSignupRequest, Role.CUSTOMER).getLoginId();
		
		em.flush();
		em.clear();
	}
	
	@Test
	@DisplayName("API: 배송지 등록 API 테스트")
	void addressCreateApiTest() throws Exception {
		String url = "/api/v1/address";
		
		AddressCreateRequest request = createAddressRequest("서울특별시 종로구 새문안로 123", "101-1001", "06234", true);
		
		mockMvc
		.perform(post(url)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request))
		)
		.andExpect(status().isUnauthorized())
		;
		
		UserLoginResponse loginResponse = login();
		
		mockMvc
		.perform(post(url)
				.cookie(new Cookie("Authorization", loginResponse.getAccessToken()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request))
		)
		.andExpect(status().isOk())
		.andExpect(jsonPath("$.success").value(true))
		.andExpect(jsonPath("$.data.addressId").isNotEmpty())
		.andExpect(jsonPath("$.data.roadAddress").value(request.getRoadAddress()))
		.andExpect(jsonPath("$.data.detailAddress").value(request.getDetailAddress()))
		.andExpect(jsonPath("$.data.zipcode").value(request.getZipcode()))
		.andExpect(jsonPath("$.data.isDefault").value(true))
		;
	}
	
	@Test
	@DisplayName("API: 배송지 수정 API 테스트")
	void addressUpdateApiTest() throws Exception {
		UUID addressId = addressService.addressCreate(loginId,
				createAddressRequest("서울특별시 종로구 새문안로 123", "101-1001", "06234", true)
		).getAddressId();
		
		em.flush();
		em.clear();
		
		String url = "/api/v1/address/" + addressId;
		
		AddressUpdateRequest request = new AddressUpdateRequest(
				"서울특별시 종로구 새문안로 123",
				"102-1002",
				"06235",
				true
		);
		
		mockMvc
		.perform(patch(url)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request))
		)
		.andExpect(status().isUnauthorized())
		;
		
		UserLoginResponse loginResponse = login();
		
		mockMvc
		.perform(patch(url)
				.cookie(new Cookie("Authorization", loginResponse.getAccessToken()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request))
		)
		.andExpect(status().isOk())
		.andExpect(jsonPath("$.success").value(true))
		.andExpect(jsonPath("$.data.addressId").value(addressId.toString()))
		.andExpect(jsonPath("$.data.roadAddress").value(request.getRoadAddress()))
		.andExpect(jsonPath("$.data.detailAddress").value(request.getDetailAddress()))
		.andExpect(jsonPath("$.data.zipcode").value(request.getZipcode()))
		.andExpect(jsonPath("$.data.isDefault").value(true))
		;
	}
	
	@Test
	@DisplayName("API: 배송지 삭제 API 테스트")
	void addressDeleteApiTest() throws Exception {
		addressService.addressCreate(loginId,
				createAddressRequest("서울특별시 종로구 새문안로 123", "101-1001", "06234", true)
		);
		UUID addressId = addressService.addressCreate(loginId,
				createAddressRequest("서울특별시 종로구 새문안로 123", "102-1002", "06235", false)
		).getAddressId();
		
		em.flush();
		em.clear();
		
		String url = "/api/v1/address/" + addressId;
		
		mockMvc
		.perform(delete(url)
				.contentType(MediaType.APPLICATION_JSON)
		)
		.andExpect(status().isUnauthorized())
		;
		
		UserLoginResponse loginResponse = login();
		
		mockMvc
		.perform(delete(url)
				.cookie(new Cookie("Authorization", loginResponse.getAccessToken()))
				.contentType(MediaType.APPLICATION_JSON)
		)
		.andExpect(status().isOk())
		.andExpect(jsonPath("$.success").value(true))
		.andExpect(jsonPath("$.data.addressId").value(addressId.toString()))
		.andExpect(jsonPath("$.data.isDeleted").value(true))
		;
	}
	
	@Test
	@DisplayName("API: 배송지 한 건 조회 API 테스트")
	void addressInfoOneApiTest() throws Exception {
		AddressCreateRequest request = createAddressRequest("서울특별시 종로구 새문안로 123", "101-1001", "06234", true);
		UUID addressId = addressService.addressCreate(loginId, request).getAddressId();
		
		em.flush();
		em.clear();
		
		String url = "/api/v1/address/" + addressId;
		
		mockMvc
		.perform(get(url)
				.contentType(MediaType.APPLICATION_JSON)
		)
		.andExpect(status().isUnauthorized())
		;
		
		UserLoginResponse loginResponse = login();
		
		mockMvc
		.perform(get(url)
				.cookie(new Cookie("Authorization", loginResponse.getAccessToken()))
				.contentType(MediaType.APPLICATION_JSON)
		)
		.andExpect(status().isOk())
		.andExpect(jsonPath("$.success").value(true))
		.andExpect(jsonPath("$.data.addressId").value(addressId.toString()))
		.andExpect(jsonPath("$.data.roadAddress").value(request.getRoadAddress()))
		.andExpect(jsonPath("$.data.detailAddress").value(request.getDetailAddress()))
		.andExpect(jsonPath("$.data.zipcode").value(request.getZipcode()))
		.andExpect(jsonPath("$.data.isDefault").value(true))
		;
	}
	
	@Test
	@DisplayName("API: 배송지 목록 조회 API 테스트")
	void addressInfoAllApiTest() throws Exception {
		addressService.addressCreate(loginId,
				createAddressRequest("서울특별시 종로구 새문안로 123", "101-1001", "06234", true)
		);
		addressService.addressCreate(loginId,
				createAddressRequest("서울특별시 종로구 새문안로 123", "102-1002", "06235", false)
		);
		
		em.flush();
		em.clear();
		
		String url = "/api/v1/address";
		
		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("page", "0");
		params.add("size", "10");
		
		mockMvc
		.perform(get(url)
				.contentType(MediaType.APPLICATION_JSON)
				.params(params)
		)
		.andExpect(status().isUnauthorized())
		;
		
		UserLoginResponse loginResponse = login();
		
		mockMvc
		.perform(get(url)
				.cookie(new Cookie("Authorization", loginResponse.getAccessToken()))
				.contentType(MediaType.APPLICATION_JSON)
				.params(params)
		)
		.andExpect(status().isOk())
		.andExpect(jsonPath("$.success").value(true))
		.andExpect(jsonPath("$.data.content", hasSize(2)))
		.andExpect(jsonPath("$.data.page").value(0))
		.andExpect(jsonPath("$.data.size").value(10))
		.andExpect(jsonPath("$.data.totalElements").value(2))
		.andExpect(jsonPath("$.data.totalPages").value(1))
		;
	}
	
    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    @DisplayName("API: 등록 선택값을 생략해도 저장되고 기존 기본 배송지는 유지된다")
    void createAddress_optionalFields(boolean includeDetail, boolean includeDefault) throws Exception {
        UUID existingDefault = addressService.addressCreate(loginId,
                createAddressRequest("서울특별시 종로구 새문안로 123", "기존 상세주소", "03154", true)).getAddressId();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("roadAddress", "서울특별시 종로구 새문안로 123");
        body.put("zipcode", "03154");
        if (includeDetail) body.put("detailAddress", "202호");
        if (includeDefault) body.put("isDefault", false);
        var result = mockMvc.perform(post("/api/v1/address")
                        .header("Authorization", login().getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(false))
                .andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("addressId").asText());
        em.flush();
        em.clear();
        Address stored = em.find(Address.class, id);
        assertThat(stored.getDetailAddress()).isEqualTo(includeDetail ? "202호" : null);
        assertThat(stored.isDefault()).isFalse();
        assertThat(em.find(Address.class, existingDefault).isDefault()).isTrue();
    }

    @Test
    @DisplayName("API: 등록 선택값에 명시적인 null을 보내도 저장된다")
    void createAddress_explicitNulls() throws Exception {
        var result = mockMvc.perform(post("/api/v1/address")
                        .header("Authorization", login().getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roadAddress":"서울특별시 종로구 새문안로 123","zipcode":"03154",
                                 "detailAddress":null,"isDefault":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(false))
                .andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("addressId").asText());
        em.flush();
        em.clear();
        assertThat(em.find(Address.class, id).getDetailAddress()).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("API: 상세주소만 수정하면 생략된 값과 기존 기본 배송지 설정을 유지한다")
    void patchAddress_preservesOmittedFields(boolean wasDefault) throws Exception {
        UUID firstId = addressService.addressCreate(loginId,
                createAddressRequest("서울특별시 종로구 새문안로 123", "첫 주소", "03154", true)).getAddressId();
        UUID targetId = addressService.addressCreate(loginId,
                createAddressRequest("서울특별시 종로구 새문안로 123", "101호", "03154", wasDefault)).getAddressId();
        mockMvc.perform(patch("/api/v1/address/{id}", targetId)
                        .header("Authorization", login().getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"detailAddress\":\"202호\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(wasDefault));
        em.flush();
        em.clear();
        Address stored = em.find(Address.class, targetId);
        assertThat(stored.getDetailAddress()).isEqualTo("202호");
        assertThat(stored.getRoadAddress()).isEqualTo("서울특별시 종로구 새문안로 123");
        assertThat(stored.getZipcode()).isEqualTo("03154");
        assertThat(stored.isDefault()).isEqualTo(wasDefault);
        assertThat(em.find(Address.class, firstId).isDefault()).isEqualTo(!wasDefault);
    }

    @Test
    @DisplayName("API: 부분 수정의 null 필드는 기존 값을 지우지 않는다")
    void patchAddress_explicitNullsPreserveValues() throws Exception {
        UUID id = addressService.addressCreate(loginId,
                createAddressRequest("서울특별시 종로구 새문안로 123", "101호", "03154", false)).getAddressId();
        mockMvc.perform(patch("/api/v1/address/{id}", id)
                        .header("Authorization", login().getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roadAddress":null,"detailAddress":null,"zipcode":null,"isDefault":null}
                                """))
                .andExpect(status().isOk());
        em.flush();
        em.clear();
        Address stored = em.find(Address.class, id);
        assertThat(stored.getRoadAddress()).isEqualTo("서울특별시 종로구 새문안로 123");
        assertThat(stored.getDetailAddress()).isEqualTo("101호");
        assertThat(stored.getZipcode()).isEqualTo("03154");
        assertThat(stored.isDefault()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"roadAddress", "zipcode"})
    @DisplayName("API: 등록 필수값 생략은 계속 400으로 거절한다")
    void createAddress_missingRequiredField(String field) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("roadAddress", "서울특별시 종로구 새문안로 123");
        body.put("zipcode", "03154");
        body.remove(field);
        mockMvc.perform(post("/api/v1/address")
                        .header("Authorization", login().getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

	private UserLoginResponse login() {
		UserLoginRequest userLoginRequest = new UserLoginRequest(
				userSignupRequest.getLoginId(),
				userSignupRequest.getPassword()
		);
		
		return userService.login(userLoginRequest);
	}
	
	private AddressCreateRequest createAddressRequest(String roadAddress, String detailAddress, String zipcode, Boolean isDefault) {
		return new AddressCreateRequest(
				roadAddress,
				detailAddress,
				zipcode,
				isDefault
		);
	}
	
}
