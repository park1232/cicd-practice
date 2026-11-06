package com.example.cicd_practice;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 실습용 취약 코드: 사용자 입력을 그대로 OS 명령어에 붙임 (명령어 삽입)
@RestController
public class PingController {

    @GetMapping("/ping")
    public String ping(@RequestParam String host) throws Exception {
        Runtime.getRuntime().exec("ping -c 1 " + host);
        return "ok";
    }
}