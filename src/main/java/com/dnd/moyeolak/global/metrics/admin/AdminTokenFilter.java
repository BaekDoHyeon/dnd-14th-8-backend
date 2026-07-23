package com.dnd.moyeolak.global.metrics.admin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * /api/admin/** 요청을 공유 관리자 토큰으로 보호한다.
 * X-Admin-Token 헤더가 설정값과 일치하지 않으면 401. 토큰 미설정 시 관리자 API는 잠긴다(기본 안전).
 * CORS 프리플라이트(OPTIONS)는 커스텀 헤더를 싣지 않으므로 검사에서 제외한다.
 */
@Component
public class AdminTokenFilter extends OncePerRequestFilter {

    private static final String ADMIN_PATH_PREFIX = "/api/admin";
    private static final String TOKEN_HEADER = "X-Admin-Token";

    private final String adminToken;

    public AdminTokenFilter(@Value("${admin.token:}") String adminToken) {
        this.adminToken = adminToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(ADMIN_PATH_PREFIX)
                || HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!isAuthorized(request.getHeader(TOKEN_HEADER))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, TOKEN_HEADER);
            response.getWriter().write("{\"message\":\"Unauthorized\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isAuthorized(String provided) {
        if (adminToken == null || adminToken.isBlank() || provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                adminToken.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
