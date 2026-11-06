package com.example.cicd_practice;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.regex.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController {

    // 허용 문자만 통과 (영문, 숫자, 점, 하이픈)
    private static final Pattern HOST = Pattern.compile("^[a-zA-Z0-9.-]{1,253}$");

    // 허용목록 검증 + 셸 없이 인자 분리 실행으로 조치 완료, 도구가 검증을 인식하지 못해 예외 처리
    @SuppressFBWarnings(value = "COMMAND_INJECTION",
            justification = "host is validated by allowlist regex and passed as a separate argument without shell")
    @GetMapping("/ping")
    public String ping(@RequestParam String host) throws Exception {
        if (!HOST.matcher(host).matches()) {
            throw new IllegalArgumentException("invalid host");
        }
        new ProcessBuilder("ping", "-c", "1", host).start();
        return "ok";
    }
}