package com.georgia.jeogiyo.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.georgia.jeogiyo.global.jwt.JwtAuthenticationFilter;
import com.georgia.jeogiyo.global.jwt.JwtAuthorizationFilter;
import com.georgia.jeogiyo.global.jwt.JwtUtil;
import com.georgia.jeogiyo.global.response.CommonResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * ──────────────────────────────────────────────────────────────────────────────────────────────────
 * 작성자: 진혜림
 * 작성일: 2026-07-10
 * 설명: CSRF 사용 안함 / JWT 방식 사용 설정, 인증관련 접근 요청 허용 처리, 필터 순서 관리
 * ──────────────────────────────────────────────────────────────────────────────────────────────────
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;
    private final AuthenticationConfiguration authenticationConfiguration;

    // AuthenticationManager 빈으로 등록
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    // JwtAuthenticationFilter 빈으로 등록
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil);
        filter.setAuthenticationManager(authenticationManager(authenticationConfiguration));
        return filter;
    }

    // JwtAuthorizationFilter 빈으로 등록
    @Bean
    public JwtAuthorizationFilter jwtAuthorizationFilter() throws Exception {
        return new JwtAuthorizationFilter(jwtUtil, userDetailsService);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // CSRF 사용 안함
        http.csrf((csrf) -> csrf.disable());

        // Session 방식은 사용하지 않고 JWT 방식을 사용 설정
        http.sessionManagement((sessionManagement) ->
                sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // 접근 허가/불가 요청 처리
        http.authorizeHttpRequests(authorizeRequests ->
                authorizeRequests
                        .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll() // resources 접근 허용
                        .requestMatchers("/api/v1/auth/**").permitAll() // 'api/v1/auth/'로 시작하는 요청 모두 접근 허용 (회원가입, 로그인)
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll() // Swagger UI 허용
                        .anyRequest().authenticated() // 그 외 모든 요청 인증처리
        );

        // 필터 순서
        http.addFilterBefore(jwtAuthorizationFilter(), JwtAuthenticationFilter.class);
        http.addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        // 인증 실패(토큰 없음) 시 공통 응답 포맷으로 401 반환
        http.exceptionHandling(exceptionHandling -> exceptionHandling
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(
                            new ObjectMapper().writeValueAsString(CommonResponse.fail("인증이 필요합니다."))
                    );
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(
                            new ObjectMapper().writeValueAsString(CommonResponse.fail("해당 요청에 대한 권한이 없습니다."))
                    );
                })
        );

        return http.build();
    }
}