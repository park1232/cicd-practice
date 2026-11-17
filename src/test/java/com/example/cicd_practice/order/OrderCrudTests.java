package com.example.cicd_practice.order;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderCrudTests {

    @Autowired
    MockMvc mockMvc;

    // /login 으로 실제 토큰을 발급받아 Authorization 헤더 값으로 돌려준다
    private String bearer(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    // 목록에는 내 주문만 나온다 (남의 주문이 하나라도 섞이면 실패)
    @Test
    void listContainsOnlyMyOrders() throws Exception {
        mockMvc.perform(get("/api/orders").header("Authorization", bearer("alice", "alice-pw")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.owner != 'alice')]").isEmpty());
    }

    // 본문에 owner를 보내도 무시하고 토큰의 사용자가 소유자가 된다
    @Test
    void createIgnoresOwnerInBody() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer("bob", "bob-pw"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"item\":\"mouse\",\"owner\":\"alice\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.owner").value("bob"));
    }

    // 자기 주문은 수정할 수 있다
    @Test
    void ownerCanUpdate() throws Exception {
        mockMvc.perform(put("/api/orders/1")
                        .header("Authorization", bearer("alice", "alice-pw"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"item\":\"monitor\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item").value("monitor"));
    }

    // 남의 주문은 수정할 수 없다 (BOLA 방어)
    @Test
    void otherUserCannotUpdate() throws Exception {
        mockMvc.perform(put("/api/orders/2")
                        .header("Authorization", bearer("alice", "alice-pw"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"item\":\"hacked\"}"))
                .andExpect(status().isForbidden());
    }

    // 남의 주문은 삭제할 수 없고, 실제로 지워지지도 않는다
    @Test
    void otherUserCannotDelete() throws Exception {
        mockMvc.perform(delete("/api/orders/2").header("Authorization", bearer("alice", "alice-pw")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/orders/2").header("Authorization", bearer("bob", "bob-pw")))
                .andExpect(status().isOk());
    }

    // 자기 주문은 삭제할 수 있다 (직접 만든 주문으로 확인)
    @Test
    void ownerCanDelete() throws Exception {
        String auth = bearer("alice", "alice-pw");
        String created = mockMvc.perform(post("/api/orders")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"item\":\"temp\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(created, "$.id");
        mockMvc.perform(delete("/api/orders/" + id).header("Authorization", auth))
                .andExpect(status().isNoContent());
    }
}
