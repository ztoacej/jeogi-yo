package com.georgia.jeogiyo.address.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.georgia.jeogiyo.address.dto.request.AddressCreateRequest;
import com.georgia.jeogiyo.address.dto.request.AddressUpdateRequest;
import com.georgia.jeogiyo.address.dto.response.AddressCreateResponse;
import com.georgia.jeogiyo.address.dto.response.AddressDeleteResponse;
import com.georgia.jeogiyo.address.dto.response.AddressUpdateResponse;
import com.georgia.jeogiyo.address.entity.Address;
import com.georgia.jeogiyo.address.repository.AddressRepository;
import com.georgia.jeogiyo.global.exception.BusinessException;
import com.georgia.jeogiyo.global.exception.GlobalErrorCode;
import com.georgia.jeogiyo.global.util.DeliveryAreaValidator;
import com.georgia.jeogiyo.user.entity.User;
import com.georgia.jeogiyo.user.service.UserFinder;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class AddressServiceImpl implements AddressService {

	private final AddressRepository addressRepo;
	
	private final AddressFinder addressFinder;
	
	private final UserFinder userFinder;

	private final DeliveryAreaValidator deliveryAreaValidator;
	
	// 배송지 등록
	@Override
	public AddressCreateResponse addressCreate(String loginId, AddressCreateRequest addressCreate) {
		User user = userFinder.getUserByLoginId(loginId);

		deliveryAreaValidator.validate(addressCreate.getRoadAddress());
		
		if(addressCreate.getIsDefault() == true) {
			Address defaultAddress = addressFinder.findByUserAndDefault(user)
					.orElse(null);
			
			
			if(defaultAddress != null) {
				defaultAddress.changeNotDefault();
			}
		}
		
		Address newAddress = Address.create(user, addressCreate);
		
		Address saved = addressRepo.save(newAddress);
		
		return AddressCreateResponse.of(saved);
	}
	
	// 배송지 수정
	@Override
	public AddressUpdateResponse addressUpdate(String loginId, String addressId, AddressUpdateRequest addressUpdate) {
		User user = userFinder.getUserByLoginId(loginId);
		
		Address address = addressFinder.findByUserAndAddressId(user, UUID.fromString(addressId));
		
		if(addressUpdate.getRoadAddress() != null) {
			deliveryAreaValidator.validate(addressUpdate.getRoadAddress());
		}
		
		if(!address.isDefault() && addressUpdate.getIsDefault()) {
			Address defaultAddress = addressFinder.findByUserAndDefault(user)
					.orElse(null);
			
			if(defaultAddress != null) {
				defaultAddress.changeNotDefault();
			}
		}
		
		address.changeAddressInfo(addressUpdate);
		
		return AddressUpdateResponse.of(address);
	}
	
	// 배송지 삭제
	@Override
	public AddressDeleteResponse addressDelete(String loginId, String addressId) {
		User user = userFinder.getUserByLoginId(loginId);
		
		Address address = addressFinder.findByUserAndAddressId(user, UUID.fromString(addressId));
		
		if(address.isDefault()) {
			Address latestAddress = addressFinder.findFirstByUserOrderByCreatedAtDesc(user)
					.orElseThrow(() -> new BusinessException(GlobalErrorCode.ALREADY_DELETED_LAST_ADDRESS));
			
			latestAddress.changeDefault();
		}
		
		address.softDelete(user.getLoginId());
		
		return AddressDeleteResponse.of(address);
	}
	
}
