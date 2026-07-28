package com.georgia.jeogiyo.global.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.georgia.jeogiyo.global.response.CommonResponse;
import com.georgia.jeogiyo.global.security.UserDetailsServiceImpl;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * ──────────────────────────────────────────────────────────────────────────────────────────────────
 * 작성자: 진혜림
 * 작성일: 2026-07-10
 * 설명: JWT 검증 및 인가, 인증 객체 생성 -> SecurityContext, Authentication, SecurityContextHolder 생성
 * ──────────────────────────────────────────────────────────────────────────────────────────────────
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthorizationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
    		
        String tokenValue = jwtUtil.getTokenFromRequest(request);

        if (StringUtils.hasText(tokenValue)) {
            tokenValue = jwtUtil.subStringToken(tokenValue);

            if (!StringUtils.hasText(tokenValue)) {
                writeUnauthorized(response, "Authorization 헤더 형식이 올바르지 않습니다.");
                return;
            }

            if (!jwtUtil.validateToken(tokenValue)) {
                log.warn("Token validation failed");
                writeUnauthorized(response, "유효하지 않은 토큰입니다.");
                return;
            }

            try {
                Claims info = jwtUtil.getUserInfoFromToken(tokenValue);
                setAuthentication(info.getSubject());
            } catch (Exception e) {
                log.warn("Authentication Error: {}", e.getMessage());
                writeUnauthorized(response, "인증에 실패했습니다.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    // JSON 응답
    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                new ObjectMapper().writeValueAsString(CommonResponse.fail(message))
        );
    }

    // 인증 처리
    public void setAuthentication(String LoginId) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        Authentication authentication = createAuthentication(LoginId);
        context.setAuthentication(authentication);

        SecurityContextHolder.setContext(context);
    }

    // 인증 객체 생성
    private Authentication createAuthentication(String LoginId) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(LoginId);
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }
}
