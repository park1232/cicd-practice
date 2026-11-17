package com.example.cicd_practice.admin;

import com.example.cicd_practice.order.Order;
import com.example.cicd_practice.order.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.List;

@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Spring이 주입하는 서비스 빈이며 외부에서 교체·변경되지 않음")
@RestController
public class AdminController {

    private final OrderService orderService;

    public AdminController(OrderService orderService) {
        this.orderService = orderService;
    }

    // ADMIN 역할만 접근 (SecurityConfig의 /api/admin/** 규칙)
    @GetMapping("/api/admin/orders")
    public List<Order> allOrders() {
        return orderService.findAll();
    }
}