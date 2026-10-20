package com.georgia.jeogiyo.global.jwt;

import com.georgia.jeogiyo.user.entity.Role;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.Key;
import java.util.Base64;
import java.util.Date;

/**
 * ──────────────────────────────────────────────────────────────────────────────────────────────────
 * 작성자: 진혜림
 * 작성일: 2026-07-09
 * 설명: JWT관련 설정
 * (토큰 생성, Cookie에 저장, 토큰 Substring, 토큰 만료 & 위변조 검증, 토큰 정보 가져오기, Encode & Decode)
 * ──────────────────────────────────────────────────────────────────────────────────────────────────
 */
@Slf4j
@Component
public class JwtUtil {

    // Header KEY
    public static final String AUTHORIZATION_HEADER = "Authorization";
    // 사용자 권한 값 KEY
    public static final String AUTHORIZATION_KEY = "auth";
    // Token 식별자
    public static final String BEARER = "Bearer ";
    // 토큰 만료시간 todo 토큰 시간 상세 정책 정하기
    private final long TOKEN_TIME = 60 * 60 * 1000L; // 60분

    @Value("${SECURITY_JWT_SECRET}")
    private String secretKey;
    private Key key; // 디코딩해서 담을 키
    private final SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;

    // 시크릿키 디코딩
    @PostConstruct
    public void init() {
        byte[] keyBytes = Base64.getDecoder().decode(secretKey);
        key = Keys.hmacShaKeyFor(keyBytes);
    }

    // 토큰 생성
    public String createToken(String loginId, Role role) {
        Date now = new Date();

        return BEARER +
                Jwts.builder()
                        .subject(loginId)
                        .claim(AUTHORIZATION_KEY, role) // 사용자 권한
                        .expiration(new Date(now.getTime() + TOKEN_TIME)) // 만료 시간
                        .issuedAt(now) // 발급일
                        .signWith(key, signatureAlgorithm) // 시크릿키, 알고리즘
                        .compact();
    }

    // 생성된 JWT를 Cookie에 저장
    public void addJwtToCookie(String token, HttpServletResponse res) {
        try {
            token = URLEncoder.encode(token, "utf-8").replaceAll("\\+", "%20");

            Cookie cookie = new Cookie(AUTHORIZATION_HEADER, token); // Name:Value
            cookie.setPath("/");

            // Response 객체에 Cookie 추가
            res.addCookie(cookie);
        } catch (UnsupportedEncodingException e) {
            log.error(e.getMessage());
        }
    }

    // Cookie에 들어있던 JWT 토큰을 Substring
    public String subStringToken(String tokenValue) {
        if (StringUtils.hasText(tokenValue) && tokenValue.startsWith(BEARER)) {
            return tokenValue.substring(BEARER.length());
        }

        log.warn("Invalid Authorization header format");
        return null;
    }

    // JWT 토큰 검증 (위변조 & 만료 검증)
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith((SecretKey) key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (SecurityException | MalformedJwtException | SignatureException e) {
            log.error("Invalid JWT signature, 유효하지 않는 JWT 서명입니다.");
        } catch (ExpiredJwtException e) {
            log.error("Expired JWT token, 만료된 JWT token 입니다.");
        } catch (UnsupportedJwtException e) {
            log.error("Unsupported JWT token, 지원되지 않는 JWT 토큰 입니다.");
        } catch (IllegalArgumentException e) {
            log.error("JWT claims is empty, 잘못된 JWT 토큰 입니다.");
        }
        return false;
    }

    // 토큰에서 사용자 정보 가져오기(body)
    public Claims getUserInfoFromToken(String token) {
        return Jwts.parser().verifyWith((SecretKey) key).build().parseSignedClaims(token).getPayload();
    }

    // HttpServletRequest 에서 Cookie Value : JWT 가져오기
    public String getTokenFromRequest(HttpServletRequest req) {
        // Authorization 헤더 확인 (Swagger Authorize 버튼 등에서 사용)
        String headerValue = req.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(headerValue)) {
            return headerValue; // 헤더 값은 URL 인코딩되어 오지 않으므로 그대로 반환
        }

        // 쿠키 확인
        Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(AUTHORIZATION_HEADER)) {
                    try {
                        return URLDecoder.decode(cookie.getValue(), "utf-8"); // Encode 되어 넘어간 Value 다시 Decode
                    } catch (UnsupportedEncodingException e) {
                        return null;
                    }
                }
            }
        }
        return null;
    }
}
