package com.example.cicd_practice.error;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

// MockMvc는 /error 경로를 거치지 않아 못 잡는 문제를 실제 서버(톰캣)를 띄워 확인
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealServerErrorTests {

    @LocalServerPort
    int port;

    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String token() throws Exception {
        HttpResponse<String> res = send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"alice\",\"password\":\"alice-pw\"}"))
                .build());
        return JsonPath.read(res.body(), "$.token");
    }

    // 로그인한 사용자가 깨진 JSON을 보내면 실서버에서도 400 (변경 전: Boot의 /error 경로가 denyAll에 막혀 403)
    @Test
    void brokenJsonIs400OnRealServer() throws Exception {
        HttpResponse<String> res = send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/orders"))
                .header("Authorization", "Bearer " + token())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"item\":"))
                .build());
        assertEquals(400, res.statusCode());
    }

    // 로그인한 사용자가 /api 아래 없는 경로를 부르면 404 (변경 전: 403)
    @Test
    void unknownApiPathIs404OnRealServer() throws Exception {
        HttpResponse<String> res = send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/nope"))
                .header("Authorization", "Bearer " + token())
                .GET()
                .build());
        assertEquals(404, res.statusCode());
    }
}