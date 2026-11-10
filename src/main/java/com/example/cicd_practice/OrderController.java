package com.example.cicd_practice;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class OrderController {

    // 실습용 메모리 데이터: 1번은 alice, 2번은 bob의 주문
    private final Map<Long, Order> orders = Map.of(
            1L, new Order(1L, "alice", "노트북"),
            2L, new Order(2L, "bob", "키보드"));

    // X-User 헤더 = 로그인한 사용자(실습용 단순화)
    @GetMapping("/api/orders/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable long id,
                                          @RequestHeader("X-User") String user) {
        Order order = orders.get(id);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        // 일부러 취약하게: 누구의 주문인지 확인하지 않고 그대로 돌려준다
        return ResponseEntity.ok(order);
    }
}