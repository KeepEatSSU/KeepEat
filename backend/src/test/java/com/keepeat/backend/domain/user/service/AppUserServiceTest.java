package com.keepeat.backend.domain.user.service;

import com.keepeat.backend.domain.notification.repository.DeviceTokenRepository;
import com.keepeat.backend.domain.security.JwtProvider;
import com.keepeat.backend.domain.user.dto.TokenResponse;
import com.keepeat.backend.domain.user.entity.AppUser;
import com.keepeat.backend.domain.user.entity.OAuthLoginCode;
import com.keepeat.backend.domain.user.entity.Role;
import com.keepeat.backend.domain.user.repository.AppUserRepository;
import com.keepeat.backend.domain.user.repository.EmailAuthRepository;
import com.keepeat.backend.domain.user.repository.OAuthLoginCodeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AppUserServiceTest {

    private static final Long USER_ID = 1L;
    private static final String EMAIL = "user@test.com";

    @InjectMocks
    private AppUserService appUserService;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private EmailAuthRepository emailAuthRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    @Mock
    private OAuthLoginCodeRepository oauthLoginCodeRepository;

    @Test
    @DisplayName("password reset request sends only verification code for local user")
    void requestPasswordReset_localUser_sendsVerificationCodeOnly() {
        AppUser user = new AppUser("local", EMAIL, "encoded-old", Role.ROLE_USER);
        given(appUserRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));

        appUserService.requestPasswordReset(EMAIL);

        verify(emailService).sendPasswordResetCode(EMAIL);
        verify(emailService, never()).sendTemporaryPassword(eq(EMAIL), anyString());
        assertThat(user.getPassword()).isEqualTo("encoded-old");
    }

    @Test
    @DisplayName("password reset request guides oauth user without issuing code")
    void requestPasswordReset_oauthUser_sendsUnavailableNotice() {
        AppUser user = new AppUser("oauth", EMAIL, "google", "google-id", Role.ROLE_USER);
        given(appUserRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));

        appUserService.requestPasswordReset(EMAIL);

        verify(emailService).sendPasswordResetUnavailable(EMAIL);
        verify(emailService, never()).sendPasswordResetCode(EMAIL);
    }

    @Test
    @DisplayName("verified password reset changes password and clears refresh token")
    void verifyPasswordReset_success_changesPasswordAndClearsRefreshToken() {
        AppUser user = new AppUser("local", EMAIL, "encoded-old", Role.ROLE_USER);
        user.updateRefreshToken("old-refresh");
        given(appUserRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));
        given(passwordEncoder.encode(anyString())).willReturn("encoded-temp");

        appUserService.verifyPasswordResetAndSendTemporaryPassword(EMAIL, "123456");

        verify(emailService).consumePasswordResetCode(EMAIL, "123456");
        verify(emailService).sendTemporaryPassword(eq(EMAIL), anyString());
        assertThat(user.getPassword()).isEqualTo("encoded-temp");
        assertThat(user.getRefreshToken()).isNull();
    }

    @Test
    @DisplayName("oauth login code can be exchanged for token pair")
    void exchangeOAuthLoginCode_success() {
        String code = "one-time-code";
        String codeHash = AppUserService.hashToken(code);
        OAuthLoginCode loginCode = OAuthLoginCode.issue(codeHash, USER_ID, LocalDateTime.now().plusMinutes(1));
        AppUser user = new AppUser("oauth", EMAIL, "google", "google-id", Role.ROLE_USER);
        ReflectionTestUtils.setField(user, "id", USER_ID);

        given(oauthLoginCodeRepository.findByCodeHash(codeHash)).willReturn(Optional.of(loginCode));
        given(appUserRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(jwtProvider.createAccessToken(EMAIL, USER_ID, Role.ROLE_USER)).willReturn("access");
        given(jwtProvider.createRefreshToken(EMAIL, USER_ID, Role.ROLE_USER)).willReturn("refresh");

        TokenResponse response = appUserService.exchangeOAuthLoginCode(code);

        assertThat(response.getAccessToken()).isEqualTo("access");
        assertThat(response.getRefreshToken()).isEqualTo("refresh");
        assertThat(user.getRefreshToken()).isEqualTo(AppUserService.hashToken("refresh"));
        verify(oauthLoginCodeRepository).delete(loginCode);
    }
}
