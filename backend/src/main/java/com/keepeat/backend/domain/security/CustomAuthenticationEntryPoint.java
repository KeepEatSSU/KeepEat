package com.keepeat.backend.domain.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        // 어드민 페이지는 401 JSON 대신 로그인 페이지로 리다이렉트 (모바일 API 흐름은 영향 없음)
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/admin/") && !uri.equals("/admin/login")) {
            response.sendRedirect("/admin/login");
            return;
        }

        // 응답 상태 코드를 401(Unauthorized)로 설정
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        // 응답 데이터의 타입을 JSON으로, 인코딩 UTF-8로 설정
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // 프론트엔드에게 보낼 JSON 문자열 직접 작성
        String jsonErrorMsg = "{\"error\": \"Unauthorized\", \"message\": \"토큰이 만료되었거나 유효하지 않습니다.\"}";

        // 화면에 출력
        response.getWriter().write(jsonErrorMsg);
    }
}