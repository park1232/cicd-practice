package com.example.cicd_practice.error;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 모든 오류가 같은 형식(application/problem+json)으로 나오는지 확인
@SpringBootTest
@AutoConfigureMockMvc
class ErrorResponseTests {

    @Autowired
    MockMvc mockMvc;

    private String bearer(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    // 빈 상품명은 저장되지 않고 400 + 틀린 필드 이름
    @Test
    void blankItemIs400WithFieldError() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer("alice", "alice-pw"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"item\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("item"));
    }

    // 100자를 넘는 상품명은 400
    @Test
    void tooLongItemIs400() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer("alice", "alice-pw"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"item\":\"" + "x".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    // 깨진 JSON은 400이고, 파서 내부 메시지를 그대로 내보내지 않음
    @Test
    void brokenJsonIs400WithoutParserDetail() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer("alice", "alice-pw"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"item\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Failed to read request"));
    }

    // 토큰 없음 → 401, 본문 형식 통일 + WWW-Authenticate 헤더 유지
    @Test
    void noTokenIs401Problem() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    // 로그인 실패 → 401, 아이디가 없는 경우와 비밀번호가 틀린 경우 응답이 같음
    @Test
    void loginFailureDoesNotRevealWhichPartIsWrong() throws Exception {
        String wrongPassword = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownUser = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String wrongPasswordDetail = JsonPath.read(wrongPassword, "$.detail");
        String unknownUserDetail = JsonPath.read(unknownUser, "$.detail");
        assertEquals(wrongPasswordDetail, unknownUserDetail);
    }

    // 필터 단계 403(USER → 관리자 API)도 같은 형식
    @Test
    void userToAdminIs403Problem() throws Exception {
        mockMvc.perform(get("/api/admin/orders").header("Authorization", bearer("alice", "alice-pw")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    // 메서드 보안 403(남의 주문)도 500이 아니라 403 + 같은 형식
    @Test
    void otherUsersOrderIs403Problem() throws Exception {
        mockMvc.perform(get("/api/orders/2").header("Authorization", bearer("alice", "alice-pw")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // 없는 주문 → 404 + 같은 형식
    @Test
    void missingOrderIs404Problem() throws Exception {
        mockMvc.perform(get("/api/orders/999").header("Authorization", bearer("alice", "alice-pw")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}