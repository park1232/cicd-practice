package com.example.cicd_practice;

import org.springframework.http.HttpStatus;
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
        // 조치: 요청한 사용자가 주문의 주인이 아니면 접근 거부
        if (!order.owner().equals(user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(order);
    }
}