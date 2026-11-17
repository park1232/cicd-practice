package com.example.cicd_practice.order;

import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class OrderService {

    // 실습용 메모리 데이터: 1번은 alice, 2번은 bob의 주문
    private final Map<Long, Order> orders = new ConcurrentHashMap<>(Map.of(
            1L, new Order(1L, "alice", "노트북"),
            2L, new Order(2L, "bob", "키보드")));

    // 새 주문 번호 (1, 2번은 기본 데이터)
    private final AtomicLong sequence = new AtomicLong(2);

    // 전체 주문 (관리자 API 전용 — 호출 쪽에서 ADMIN 역할을 보장)
    public List<Order> findAll() {
        return List.copyOf(orders.values());
    }

    // 로그인 사용자 본인의 주문만 조회 (조회 단계에서 소유자로 걸러냄)
    public List<Order> findByOwner(String owner) {
        return orders.values().stream().filter(o -> o.owner().equals(owner)).toList();
    }

    // 반환된 주문의 주인이 로그인 사용자가 아니면 AccessDeniedException (없는 주문은 통과)
    @PostAuthorize("returnObject.isEmpty() or returnObject.get().owner() == authentication.name")
    public Optional<Order> findById(long id) {
        return Optional.ofNullable(orders.get(id));
    }

    // 소유자는 호출한 쪽이 넘긴 로그인 사용자 이름으로만 정해짐
    public Order create(String owner, String item) {
        Order order = new Order(sequence.incrementAndGet(), owner, item);
        orders.put(order.id(), order);
        return order;
    }

    // 실행 전에 소유자 확인 (@PostAuthorize는 이미 수정된 뒤라서 쓰기에는 부적합)
    @PreAuthorize("@orderService.isOwner(#id, authentication.name)")
    public Order update(long id, String item) {
        Order updated = new Order(id, orders.get(id).owner(), item);
        orders.put(id, updated);
        return updated;
    }

    @PreAuthorize("@orderService.isOwner(#id, authentication.name)")
    public void delete(long id) {
        orders.remove(id);
    }

    // 주문이 있고 주인이 맞을 때만 true (없는 주문도 false)
    public boolean isOwner(long id, String username) {
        Order order = orders.get(id);
        return order != null && order.owner().equals(username);
    }
}