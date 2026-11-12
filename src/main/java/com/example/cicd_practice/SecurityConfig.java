package com.example.cicd_practice;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    // 요청이 컨트롤러에 닿기 전에 거치는 보안 필터 체인
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 세션을 만들지 않음 (요청마다 인증 정보를 직접 보냄)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 브라우저 폼이 없는 API라 CSRF 토큰 사용 안 함
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // 점검용 화면·응답은 로그인 없이 허용 (ZAP baseline이 접근)
                        .requestMatchers("/", "/index.html", "/hello").permitAll()
                        // 주문 API는 로그인 필수
                        .requestMatchers("/api/**").authenticated()
                        // 그 외 모든 경로는 거부
                        .anyRequest().denyAll())
                // 요청 헤더 Authorization: Basic ... 로 인증 (3단계에서 JWT로 교체)
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    // 비밀번호는 평문이 아니라 BCrypt 해시로 저장·비교
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 실습용 메모리 사용자 (alice, bob)
    @Bean
    UserDetailsService userDetailsService(PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername("alice").password(encoder.encode("alice-pw")).roles("USER").build(),
                User.withUsername("bob").password(encoder.encode("bob-pw")).roles("USER").build());
    }
}