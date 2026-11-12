package com.example.cicd_practice.order;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import java.time.Instant;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderAuthorizationTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtEncoder jwtEncoder;

    // /login 으로 실제 토큰을 발급받는다
    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    // 정상: 자기 주문은 볼 수 있다
    @Test
    void ownerCanReadOwnOrder() throws Exception {
        mockMvc.perform(get("/api/orders/1").header("Authorization", "Bearer " + login("alice", "alice-pw")))
                .andExpect(status().isOk());
    }

    // BOLA 방어: 남의 주문은 볼 수 없다
    @Test
    void otherUserCannotReadOrder() throws Exception {
        mockMvc.perform(get("/api/orders/2").header("Authorization", "Bearer " + login("alice", "alice-pw")))
                .andExpect(status().isForbidden());
    }

    // 토큰이 없으면 401
    @Test
    void noTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isUnauthorized());
    }

    // 서명을 위조한 토큰은 401 (토큰 끝 글자를 바꿈)
    @Test
    void forgedTokenIsUnauthorized() throws Exception {
        String token = login("alice", "alice-pw");
        String forged = token.substring(0, token.length() - 2) + "xx";
        mockMvc.perform(get("/api/orders/1").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    // 비밀번호가 틀리면 토큰을 발급하지 않는다
    @Test
    void wrongPasswordIsUnauthorized() throws Exception {
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    // 서명 없는 토큰(alg=none)으로 bob 행세해도 401
    @Test
    void unsignedTokenIsUnauthorized() throws Exception {
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String header = b64.encodeToString("{\"alg\":\"none\"}".getBytes());
        String payload = b64.encodeToString("{\"sub\":\"bob\"}".getBytes());
        mockMvc.perform(get("/api/orders/2").header("Authorization", "Bearer " + header + "." + payload + "."))
                .andExpect(status().isUnauthorized());
    }

    // 정상 서명이어도 만료된 토큰은 401
    @Test
    void expiredTokenIsUnauthorized() throws Exception {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("alice")
                .issuedAt(now.minusSeconds(1800))
                .expiresAt(now.minusSeconds(900))
                .build();
        String token = jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        mockMvc.perform(get("/api/orders/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}