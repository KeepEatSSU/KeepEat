package com.keepeat.backend.domain.security;

import com.keepeat.backend.domain.user.entity.AppUser;
import com.keepeat.backend.domain.user.entity.Role;
import com.keepeat.backend.domain.user.entity.UserSession;
import com.keepeat.backend.domain.user.repository.AppUserRepository;
import com.keepeat.backend.domain.user.repository.UserSessionRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private FilterChain filterChain;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void expiredServerSessionCannotAuthenticateAnOtherwiseValidAccessToken() throws Exception {
        String token = "access-token";
        AppUser user = new AppUser("user", "user@test.com", "encoded", Role.ROLE_USER);
        UserSession expiredSession = UserSession.create(
                "session-1", 1L, "refresh-hash", Instant.now().minusSeconds(1)
        );
        MockHttpServletRequest request = bearerRequest(token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                jwtProvider, appUserRepository, userSessionRepository
        );

        given(jwtProvider.validateToken(token)).willReturn(true);
        given(jwtProvider.isAccessToken(token)).willReturn(true);
        given(jwtProvider.getId(token)).willReturn(1L);
        given(jwtProvider.getSessionId(token)).willReturn("session-1");
        given(appUserRepository.findById(1L)).willReturn(Optional.of(user));
        given(userSessionRepository.findByIdAndUserId("session-1", 1L))
                .willReturn(Optional.of(expiredSession));

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    private MockHttpServletRequest bearerRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
