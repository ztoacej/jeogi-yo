package com.georgia.jeogiyo.user.dto.response;

import com.georgia.jeogiyo.user.entity.Role;
import com.georgia.jeogiyo.user.entity.User;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserLoginResponse {

	private final String accessToken;
	
	private final UUID userId;
	
	private final String loginId;
	
	private final String nickname;
	
	private final Role role;
	
	public static UserLoginResponse of(User user, String accessToken) {
		return new UserLoginResponse(
				accessToken,
				user.getUserId(),
				user.getLoginId(),
				user.getNickname(),
				user.getRole()
		);
	}
}
