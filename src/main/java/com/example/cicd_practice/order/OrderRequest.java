package com.example.cicd_practice.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 주문 생성·수정 요청 본문 (owner를 받지 않으므로 클라이언트가 소유자를 지정할 수 없음)
public record OrderRequest(
        // 빈 값·공백만 있는 값 거부, 최대 100자
        @NotBlank @Size(max = 100) String item) {
}