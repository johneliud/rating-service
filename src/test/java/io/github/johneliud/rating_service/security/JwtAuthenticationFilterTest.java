package io.github.johneliud.rating_service.security;

import io.github.johneliud.rating_service.util.JwtUtil;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    void doFilter_setsAuthentication_whenValidToken() throws Exception {
        when(jwtUtil.isTokenValid("valid-token")).thenReturn(true);
        when(jwtUtil.extractUserId("valid-token")).thenReturn("user-123");
        when(jwtUtil.extractRole("valid-token")).thenReturn("ROLE_USER");
        request.addHeader("Authorization", "Bearer valid-token");

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo("user-123");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
    }

    @Test
    void doFilter_doesNotSetAuthentication_whenNoAuthHeader() throws Exception {
        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtUtil, never()).isTokenValid(any());
    }

    @Test
    void doFilter_doesNotSetAuthentication_whenInvalidToken() throws Exception {
        when(jwtUtil.isTokenValid("bad-token")).thenReturn(false);
        request.addHeader("Authorization", "Bearer bad-token");

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_doesNotSetAuthentication_whenBearerPrefixMissing() throws Exception {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtUtil, never()).isTokenValid(any());
    }

    @Test
    void doFilter_continuesFilterChain_always() throws Exception {
        FilterChain mockChain = mock(FilterChain.class);
        when(jwtUtil.isTokenValid("some-token")).thenReturn(false);
        request.addHeader("Authorization", "Bearer some-token");

        filter.doFilterInternal(request, response, mockChain);

        verify(mockChain).doFilter(request, response);
    }
}