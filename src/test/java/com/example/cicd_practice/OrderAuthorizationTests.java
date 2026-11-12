package com.example.cicd_practice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderAuthorizationTests {

    @Autowired
    MockMvc mockMvc;

    // 정상: 자기 주문은 볼 수 있다
    @Test
    void ownerCanReadOwnOrder() throws Exception {
        mockMvc.perform(get("/api/orders/1").with(httpBasic("alice", "alice-pw")))
                .andExpect(status().isOk());
    }

    // BOLA 방어: 남의 주문은 볼 수 없다
    @Test
    void otherUserCannotReadOrder() throws Exception {
        mockMvc.perform(get("/api/orders/2").with(httpBasic("alice", "alice-pw")))
                .andExpect(status().isForbidden());
    }
}