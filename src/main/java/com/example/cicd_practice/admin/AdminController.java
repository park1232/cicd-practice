package com.example.cicd_practice.admin;

import com.example.cicd_practice.order.Order;
import com.example.cicd_practice.order.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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