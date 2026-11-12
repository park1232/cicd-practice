package com.example.cicd_practice.order;

import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
public class OrderService {

    // 실습용 메모리 데이터: 1번은 alice, 2번은 bob의 주문
    private final Map<Long, Order> orders = Map.of(
            1L, new Order(1L, "alice", "노트북"),
            2L, new Order(2L, "bob", "키보드"));

    // 반환된 주문의 주인이 로그인 사용자가 아니면 AccessDeniedException (없는 주문은 통과)
    @PostAuthorize("returnObject.isEmpty() or returnObject.get().owner() == authentication.name")
    public Optional<Order> findById(long id) {
        return Optional.ofNullable(orders.get(id));
    }
}