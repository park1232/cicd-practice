package com.example.cicd_practice;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// 모든 응답에 보안 헤더를 붙인다 (ZAP baseline 10020·10021·10038·10063 조치)
@Component
public class SecurityHeaderFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 10021: 브라우저가 응답 타입을 추측하지 못하게 함
        response.setHeader("X-Content-Type-Options", "nosniff");
        // 10020: 다른 사이트가 iframe으로 감싸는 것(클릭재킹) 차단
        response.setHeader("X-Frame-Options", "DENY");
        // 10038: 스크립트·이미지 등을 같은 출처에서만 불러오게 제한
        response.setHeader("Content-Security-Policy", "default-src 'self'; frame-ancestors 'none'");
        // 10063: 카메라·마이크·위치 같은 브라우저 기능 사용 금지
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        filterChain.doFilter(request, response);
    }
}