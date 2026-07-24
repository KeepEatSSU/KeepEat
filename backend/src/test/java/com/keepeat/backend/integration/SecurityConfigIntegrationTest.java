package com.keepeat.backend.integration;

import com.keepeat.backend.domain.user.dto.PasswordResetRequest;
import com.keepeat.backend.domain.user.service.AppUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class SecurityConfigIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private AppUserService appUserService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    @DisplayName("비로그인 사용자도 일회용 토큰으로 비밀번호를 재설정할 수 있다")
    void passwordResetEndpointIsPublic() throws Exception {
        mockMvc.perform(post("/api/users/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resetToken": "valid-reset-token",
                                  "newPassword": "NewPassword1!"
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(appUserService).resetPassword(
                new PasswordResetRequest("valid-reset-token", "NewPassword1!")
        );
    }
}
