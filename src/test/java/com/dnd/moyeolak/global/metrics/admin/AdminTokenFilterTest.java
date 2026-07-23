package com.dnd.moyeolak.global.metrics.admin;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AdminTokenFilterTest {

    private static final String TOKEN = "s3cret-admin-token";

    private final AdminTokenFilter filter = new AdminTokenFilter(TOKEN);

    private MockHttpServletRequest adminRequest(String method) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/admin/external-api/summary");
        request.setRequestURI("/api/admin/external-api/summary");
        return request;
    }

    @Test
    @DisplayName("올바른 토큰이면 다음 필터로 통과한다")
    void passesWithValidToken() throws Exception {
        MockHttpServletRequest request = adminRequest("GET");
        request.addHeader("X-Admin-Token", TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    @DisplayName("토큰이 틀리면 401을 반환하고 다음 필터로 넘기지 않는다")
    void rejectsWrongToken() throws Exception {
        MockHttpServletRequest request = adminRequest("GET");
        request.addHeader("X-Admin-Token", "wrong");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("토큰 헤더가 없으면 401을 반환한다")
    void rejectsMissingToken() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(adminRequest("GET"), response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("관리자 경로가 아니면 토큰 없이도 통과한다")
    void ignoresNonAdminPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/locations/vote");
        request.setRequestURI("/api/locations/vote");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("CORS 프리플라이트(OPTIONS)는 토큰 검사 없이 통과시킨다")
    void allowsCorsPreflight() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(adminRequest("OPTIONS"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }
}
