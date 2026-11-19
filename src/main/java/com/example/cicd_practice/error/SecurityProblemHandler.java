package com.example.cicd_practice.error;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;

// 보안 필터 단계(컨트롤러 도달 전)의 401·403도 ApiExceptionHandler와 같은 형식으로 응답
@Component
public class SecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    // 상태코드·WWW-Authenticate 헤더는 Spring 기본 처리기에 맡기고 본문만 추가
    private final BearerTokenAuthenticationEntryPoint entryPoint = new BearerTokenAuthenticationEntryPoint();
    private final BearerTokenAccessDeniedHandler deniedHandler = new BearerTokenAccessDeniedHandler();
    private final JsonMapper jsonMapper;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Spring이 주입하는 JsonMapper 빈이며 외부에서 교체·변경되지 않음")
    public SecurityProblemHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    // 토큰 없음·서명 불일치·만료 → 401
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) throws IOException {
        entryPoint.commence(request, response, ex);
        write(request, response, HttpStatus.UNAUTHORIZED, "인증이 필요합니다.");
    }

    // 토큰은 유효하지만 역할 부족(예: USER가 /api/admin) → 403
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) throws IOException {
        deniedHandler.handle(request, response, ex);
        write(request, response, HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getOutputStream(), problem);
    }
}