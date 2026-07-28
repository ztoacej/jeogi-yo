package com.georgia.jeogiyo.global.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.georgia.jeogiyo.global.response.CommonResponse;
import com.georgia.jeogiyo.global.security.UserDetailsImpl;
import com.georgia.jeogiyo.user.dto.request.UserLoginRequest;
import com.georgia.jeogiyo.user.dto.response.UserLoginResponse;
import com.georgia.jeogiyo.user.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

/**
 * ──────────────────────────────────────────────────────────────────────────────────────────────────
 * 작성자: 진혜림
 * 작성일: 2026-07-10
 * 설명: 로그인 시도, 로그인 성공 JWT 생성, 로그인 실패 처리
 * ──────────────────────────────────────────────────────────────────────────────────────────────────
 */
@Slf4j
public class JwtAuthenticationFilter extends UsernamePasswordAuthenticationFilter {
    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
        setFilterProcessesUrl("/api/v1/auth/login");
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
        log.info("로그인 시도");
        try {
            UserLoginRequest requestDto = new ObjectMapper().readValue(request.getInputStream(), UserLoginRequest.class);

            return getAuthenticationManager().authenticate( // Spring Security의 인증 매니저에게 위임
                    new UsernamePasswordAuthenticationToken(
                            requestDto.getLoginId(),
                            requestDto.getPassword(),
                            null
                    )
            );
        } catch (IOException e) {
            log.error(e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain, Authentication authResult) throws IOException, ServletException {
        log.info("로그인 성공 및 JWT 생성");
        User user = ((UserDetailsImpl) authResult.getPrincipal()).getUser();

        String token = jwtUtil.createToken(user.getLoginId(), user.getRole());
        //jwtUtil.addJwtToCookie(token, response);

        // JSON 응답
        UserLoginResponse loginResponse = UserLoginResponse.of(user, token);

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(new ObjectMapper().writeValueAsString(CommonResponse.success("로그인에 성공했습니다.", loginResponse)));
    }

    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, AuthenticationException failed) throws IOException, ServletException {
        log.info("로그인 실패");

        // JSON 응답
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                new ObjectMapper().writeValueAsString(CommonResponse.fail("아이디 또는 비밀번호가 일치하지 않습니다."))
        );
    }
}
