package com.example.cicd_practice.order;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderAuthorizationTests {

    @Autowired
    MockMvc mockMvc;

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
}