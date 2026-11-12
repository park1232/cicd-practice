package com.example.cicd_practice.order;

// 주문 한 건 (owner = 주문한 사용자)
public record Order(long id, String owner, String item) {
}