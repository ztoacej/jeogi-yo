package com.georgia.jeogiyo.user.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.georgia.jeogiyo.global.exception.BusinessException;
import com.georgia.jeogiyo.global.exception.GlobalErrorCode;
import com.georgia.jeogiyo.global.jwt.JwtUtil;
import com.georgia.jeogiyo.store.service.StoreService;
import com.georgia.jeogiyo.user.dto.request.UserDeleteRequest;
import com.georgia.jeogiyo.user.dto.request.UserLoginRequest;
import com.georgia.jeogiyo.user.dto.request.UserSignupRequest;
import com.georgia.jeogiyo.user.dto.request.UserUpdateRequest;
import com.georgia.jeogiyo.user.dto.response.UserDeleteResponse;
import com.georgia.jeogiyo.user.dto.response.UserInfoResponse;
import com.georgia.jeogiyo.user.dto.response.UserLoginResponse;
import com.georgia.jeogiyo.user.dto.response.UserSignupResponse;
import com.georgia.jeogiyo.user.entity.Role;
import com.georgia.jeogiyo.user.entity.User;
import com.georgia.jeogiyo.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	
	private final UserFinder userFinder;
	
	private final JwtUtil jwtUtil;
	
	private final PasswordEncoder passwordEncoder;
	
	private final UserInfoUpdateService userInfoUpdateService;
	
	private final StoreService storeService;
	
	@Transactional(rollbackFor = Exception.class)
	public UserLoginResponse login(UserLoginRequest userLogin) {
		User user = userFinder.getUserByLoginId(userLogin.getLoginId());
		
		if(!passwordEncoder.matches(userLogin.getPassword(), user.getPassword())) {
			throw new BusinessException(GlobalErrorCode.INVALID_LOGIN_INFO);
		}
		
		String accessToken = jwtUtil.createToken(user.getLoginId(), user.getRole());
		
		return UserLoginResponse.of(user, accessToken);
	}
	
	@Transactional(rollbackFor = Exception.class)
	public UserSignupResponse signup(UserSignupRequest signupUser, Role role) {
		if(userRepository.existsByEmail(signupUser.getEmail())) {
			// TODO: 이미 사용중인 이메일입니다. 409
			log.warn("SIGNUP_FAILED: Duplicate email = {}", signupUser.getEmail());
			throw new BusinessException(GlobalErrorCode.DUPLICATE_EMAIL);
		}
		
		if(userRepository.existsByNickname(signupUser.getNickname())) {
			// TODO: 이미 사용중인 닉네임입니다. 409
			log.warn("SIGNUP_FAILED: Duplicate nickname = {}", signupUser.getNickname());
			throw new BusinessException(GlobalErrorCode.DUPLICATE_NICKNAME);
		}
		
		if(userRepository.existsByLoginId(signupUser.getLoginId())) {
			// TODO: 이미 사용중인 아이디입니다. 409
			log.warn("SIGNUP_FAILED: Duplicate loginId = {}", signupUser.getLoginId());
			throw new BusinessException(GlobalErrorCode.DUPLICATE_LOGIN_ID);
		}
		
		User user;
		
		if(role == Role.CUSTOMER) {
			user = User.customerCreate(signupUser, passwordEncoder);
		} else if(role == Role.OWNER) {
			user = User.ownerCreate(signupUser, passwordEncoder);
		} else {
			throw new BusinessException(GlobalErrorCode.FORBIDDEN);
		}
		
		User saved = userRepository.save(user);
		
		return UserSignupResponse.of(saved);
	}
	
	@Transactional(rollbackFor = Exception.class)
	public UserInfoResponse update(String loginId, UserUpdateRequest updateUser) {
		User user = userFinder.getUserByLoginId(loginId);
		
		List<String> updateFields = updateUser.getUpdateFields();
		
		updateFields.stream().forEach(field -> {
			switch (field) {
				case "nickname" -> userInfoUpdateService.changeNickname(user, updateUser.getNickname());
				case "phone" -> userInfoUpdateService.changePhone(user, updateUser.getPhone());
				case "email" -> userInfoUpdateService.changeEmail(user, updateUser.getEmail());
				case "password" -> userInfoUpdateService.changePassword(user, updateUser.getPassword(), passwordEncoder);
				
				default -> throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
			}
		});
		
		log.info("USER_UPDATE_SUCCESS: loginId = {}, updatedFields = {}",
				user.getLoginId(),
				updateFields);
		
		return UserInfoResponse.of(user);
	}
	
	@Transactional(rollbackFor = Exception.class)
	public UserDeleteResponse delete(String loginId, UserDeleteRequest deleteUser) {
		User user = userFinder.getUserByLoginId(loginId);
		
		if(user.verifyCredentialsForDelete(deleteUser, passwordEncoder)) {
			
			if(user.isMaster()) {
				long masterUserCount = userRepository.countByRoleAndIsDeletedFalse(Role.MASTER);
				log.info("MASTER 권한 탈퇴 요청 검증 시작: masterUserCount={}", masterUserCount);
				
				if(masterUserCount <= 1) {
					throw new BusinessException(GlobalErrorCode.DELETE_FAILURE_LAST_MASTER);
				}
			} else if(user.isOwner()) {
				if(storeService.existsActiveOrderByOwnerId(user.getUserId())) {
					throw new BusinessException(GlobalErrorCode.DELETE_FAILURE_ACTIVE_ORDERS);
				}
				if(storeService.existsActiveStoreByOwnerId(user.getUserId())) {
					throw new BusinessException(GlobalErrorCode.DELETE_FAILURE_OPEN_STORES);
				}
			}
			
			user.softDelete(user.getLoginId());
			
			log.info("USER_DELETE_SUCCESS: loginId = {}", user.getLoginId());
		} else {
			// 3. 실패 시
			// TODO: 회원탈퇴를 진행할 수 없습니다.
			log.warn("USER_DELETE_FAILED: Password mismatch loginId = {}", user.getLoginId());
			throw new BusinessException(GlobalErrorCode.INVALID_LOGIN_INFO);
		}
		
		User deleted = userRepository.save(user);
		
		return UserDeleteResponse.of(deleted);
	}
	
}
