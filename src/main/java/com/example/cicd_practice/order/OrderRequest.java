package com.example.cicd_practice.order;

// 주문 생성·수정 요청 본문 (owner를 받지 않으므로 클라이언트가 소유자를 지정할 수 없음)
public record OrderRequest(String item) {
}
