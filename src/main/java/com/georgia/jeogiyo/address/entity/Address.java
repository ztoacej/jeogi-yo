package com.georgia.jeogiyo.address.entity;

import java.util.Objects;
import java.util.UUID;

import com.georgia.jeogiyo.address.dto.request.AddressCreateRequest;
import com.georgia.jeogiyo.address.dto.request.AddressUpdateRequest;
import com.georgia.jeogiyo.global.entity.BaseEntity;
import com.georgia.jeogiyo.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "p_address")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Address extends BaseEntity {

	@Id
	@Column(name = "address_id")
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID addressId;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false, updatable = false)
	private User user;
	
	@Column(name = "road_address", nullable = false, length = 100)
	private String roadAddress;
	
	@Column(name = "detail_address", length = 100)
	private String detailAddress;
	
	@Column(name = "zipcode", nullable = false, length = 10)
	private String zipcode;
	
	@Column(name = "is_default", nullable = false)
	private boolean isDefault = false;
	
	public static Address create(User user, AddressCreateRequest addressCreate) {
		Address address = new Address();
		
		address.user = Objects.requireNonNull(user);
		address.roadAddress = Objects.requireNonNull(addressCreate.getRoadAddress());
		address.detailAddress = addressCreate.getDetailAddress();
		address.zipcode = Objects.requireNonNull(addressCreate.getZipcode());
		
		if(addressCreate.getIsDefault() != null) {
			address.isDefault = Objects.requireNonNull(addressCreate.getIsDefault());
		}
		
		return address;
	}
	
	public void changeNotDefault() {
		if(this.isDefault == true) {
			this.isDefault = false;			
		}
	}
	
	public void changeDefault() {
		if(this.isDefault == false) {
			this.isDefault = true;
		}
	}
	
	public void changeAddressInfo(AddressUpdateRequest addressUpdate) {
		if(addressUpdate.getRoadAddress() != null) {
			this.changeRoadAddress(addressUpdate.getRoadAddress());
		}
		
		if(addressUpdate.getDetailAddress() != null) {
			this.changeDetailAddress(addressUpdate.getDetailAddress());
		}
		
		if(addressUpdate.getZipcode() != null) {
			this.changeZipcode(addressUpdate.getZipcode());
		}
		
		if(addressUpdate.getIsDefault() != null) {
			this.changeIsDefault(addressUpdate.getIsDefault());
		}
	}
	
	private void changeRoadAddress(String roadAddress) {
		this.roadAddress = Objects.requireNonNull(roadAddress);
	}
	
	private void changeDetailAddress(String detailAddress) {
		this.detailAddress = Objects.requireNonNull(detailAddress);
	}
	
	private void changeZipcode(String zipcode) {
		this.zipcode = Objects.requireNonNull(zipcode);
	}
	
	private void changeIsDefault(Boolean isDefault) {
		this.isDefault = Objects.requireNonNull(isDefault);
	}
	
}
