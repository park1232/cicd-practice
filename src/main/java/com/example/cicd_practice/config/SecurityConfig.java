package com.example.cicd_practice.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    // 요청이 컨트롤러에 닿기 전에 거치는 보안 필터 체인
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 세션을 만들지 않음 (요청마다 토큰으로 인증)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 브라우저 폼이 없는 API라 CSRF 토큰 사용 안 함
                .csrf(AbstractHttpConfigurer::disable)
                // 보안 헤더: nosniff·X-Frame-Options는 기본값, CSP·Permissions-Policy는 직접 지정 (ZAP 10020·10021·10038·10063)
                .headers(h -> h
                        .frameOptions(f -> f.deny())
                        .contentSecurityPolicy(c -> c.policyDirectives("default-src 'self'; frame-ancestors 'none'"))
                        .permissionsPolicyHeader(p -> p.policy("camera=(), microphone=(), geolocation=()")))
                .authorizeHttpRequests(auth -> auth
                        // 점검용 화면·응답은 로그인 없이 허용 (ZAP baseline이 접근)
                        .requestMatchers("/", "/index.html", "/hello").permitAll()
                        // 로그인 API는 토큰이 없는 상태에서 호출
                        .requestMatchers(HttpMethod.POST, "/login").permitAll()
                        // 주문 API는 유효한 토큰 필수
                        .requestMatchers("/api/**").authenticated()
                        // 그 외 모든 경로는 거부
                        .anyRequest().denyAll())
                // Authorization: Bearer <JWT> 를 읽어 서명·만료를 검증 (실패 시 401)
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()));
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

    // /login 에서 아이디·비밀번호를 검증하는 인증 관리자
    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    // 토큰 서명 키: 환경변수 APP_JWT_SECRET(Base64, 32바이트 이상)이 있으면 사용, 없으면 기동 시 무작위 생성
    @Bean
    SecretKey jwtSigningKey(@Value("${app.jwt.secret:}") String secret) throws NoSuchAlgorithmException {
        if (secret.isBlank()) {
            return KeyGenerator.getInstance("HmacSHA256").generateKey();
        }
        return new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256");
    }

    // 토큰 발급용 (HS256 서명)
    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    // 토큰 검증용 (같은 키, HS256만 허용)
    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
    }
}