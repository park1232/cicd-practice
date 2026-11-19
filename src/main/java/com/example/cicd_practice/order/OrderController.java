package com.example.cicd_practice.order;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
public class OrderController {

    private final OrderService orderService;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Spring이 주입하는 서비스 빈이며 외부에서 교체·변경되지 않음")
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // 내 주문 목록
    @GetMapping("/api/orders")
    public List<Order> myOrders(Authentication authentication) {
        return orderService.findByOwner(authentication.getName());
    }

    // 소유자 검사는 OrderService의 @PostAuthorize가 담당, 없는 주문은 404
    @GetMapping("/api/orders/{id}")
    public Order getOrder(@PathVariable long id) {
        return orderService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "주문이 없습니다."));
    }

    // 주문 생성: 소유자는 토큰의 사용자로 고정
    @PostMapping("/api/orders")
    public ResponseEntity<Order> create(@Valid @RequestBody OrderRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.create(authentication.getName(), request.item()));
    }

    // 주문 수정: 소유자 검사는 OrderService의 @PreAuthorize가 담당
    @PutMapping("/api/orders/{id}")
    public Order update(@PathVariable long id, @Valid @RequestBody OrderRequest request) {
        return orderService.update(id, request.item());
    }

    // 주문 삭제: 소유자 검사는 OrderService의 @PreAuthorize가 담당
    @DeleteMapping("/api/orders/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        orderService.delete(id);
        return ResponseEntity.noContent().build();
    }
}