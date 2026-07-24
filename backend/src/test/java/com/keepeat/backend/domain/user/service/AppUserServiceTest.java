package com.keepeat.backend.domain.user.service;

import com.keepeat.backend.domain.notification.repository.DeviceTokenRepository;
import com.keepeat.backend.domain.notification.repository.NotificationRepository;
import com.keepeat.backend.domain.recipe.repository.RecipeReactionRepository;
import com.keepeat.backend.domain.recipe.repository.UserRecipeRepository;
import com.keepeat.backend.domain.security.JwtProvider;
import com.keepeat.backend.domain.common.exception.ErrorCode;
import com.keepeat.backend.domain.common.exception.KeepEatException;
import com.keepeat.backend.domain.user.dto.LogoutRequest;
import com.keepeat.backend.domain.user.dto.TokenRefreshRequest;
import com.keepeat.backend.domain.user.dto.TokenResponse;
import com.keepeat.backend.domain.user.dto.PasswordResetTokenResponse;
import com.keepeat.backend.domain.user.dto.PasswordChangeRequest;
import com.keepeat.backend.domain.user.dto.PasswordResetRequest;
import com.keepeat.backend.domain.user.entity.AppUser;
import com.keepeat.backend.domain.user.entity.OAuthLoginCode;
import com.keepeat.backend.domain.user.entity.PasswordResetToken;
import com.keepeat.backend.domain.user.entity.Role;
import com.keepeat.backend.domain.user.repository.AppUserRepository;
import com.keepeat.backend.domain.user.repository.EmailAuthRepository;
import com.keepeat.backend.domain.user.repository.OAuthLoginCodeRepository;
import com.keepeat.backend.domain.user.repository.PasswordResetTokenRepository;
import com.keepeat.backend.domain.user.repository.UserSessionRepository;
import com.keepeat.backend.domain.useringredient.UserIngredientRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
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

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserIngredientRepository userIngredientRepository;

    @Mock
    private UserRecipeRepository userRecipeRepository;

    @Mock
    private RecipeReactionRepository recipeReactionRepository;

    @Test
    @DisplayName("password reset request sends only verification code for local user")
    void requestPasswordReset_localUser_sendsVerificationCodeOnly() {
        AppUser user = new AppUser("local", EMAIL, "encoded-old", Role.ROLE_USER);
        given(appUserRepository.findByEmailIgnoreCase(EMAIL)).willReturn(Optional.of(user));

        appUserService.requestPasswordReset(EMAIL);

        verify(emailService).sendPasswordResetCode(EMAIL);
        assertThat(user.getPassword()).isEqualTo("encoded-old");
    }

    @Test
    @DisplayName("password reset request guides oauth user without issuing code")
    void requestPasswordReset_oauthUser_sendsUnavailableNotice() {
        AppUser user = new AppUser("oauth", EMAIL, "google", "google-id", Role.ROLE_USER);
        given(appUserRepository.findByEmailIgnoreCase(EMAIL)).willReturn(Optional.of(user));

        appUserService.requestPasswordReset(EMAIL);

        verify(emailService).sendPasswordResetUnavailable(EMAIL);
        verify(emailService, never()).sendPasswordResetCode(EMAIL);
    }

    @Test
    @DisplayName("verified password reset issues a short-lived one-time reset token")
    void verifyPasswordReset_success_issuesResetToken() {
        AppUser user = new AppUser("local", EMAIL, "encoded-old", Role.ROLE_USER);
        given(appUserRepository.findByEmailIgnoreCase(EMAIL)).willReturn(Optional.of(user));

        PasswordResetTokenResponse response = appUserService.verifyPasswordReset(EMAIL, "123456");

        verify(emailService).consumePasswordResetCode(EMAIL, "123456");
        verify(passwordResetTokenRepository).save(any());
        assertThat(response.resetToken()).isNotBlank();
        assertThat(response.expiresInSeconds()).isEqualTo(600L);
    }

    @Test
    @DisplayName("oauth login code can be exchanged for token pair")
    void exchangeOAuthLoginCode_success() {
        String code = "one-time-code";
        String codeHash = AppUserService.hashToken(code);
        OAuthLoginCode loginCode = OAuthLoginCode.issue(codeHash, USER_ID, LocalDateTime.now().plusMinutes(1));
        AppUser user = new AppUser("oauth", EMAIL, "google", "google-id", Role.ROLE_USER);
        ReflectionTestUtils.setField(user, "id", USER_ID);

        given(oauthLoginCodeRepository.findByCodeHashForUpdate(codeHash)).willReturn(Optional.of(loginCode));
        given(appUserRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(jwtProvider.createAccessToken(eq(EMAIL), eq(USER_ID), eq(Role.ROLE_USER), anyString())).willReturn("access");
        given(jwtProvider.createRefreshToken(eq(EMAIL), eq(USER_ID), eq(Role.ROLE_USER), anyString())).willReturn("refresh");
        given(jwtProvider.getRefreshTokenValidityTime()).willReturn(1_209_600_000L);

        TokenResponse response = appUserService.exchangeOAuthLoginCode(code);

        assertThat(response.getAccessToken()).isEqualTo("access");
        assertThat(response.getRefreshToken()).isEqualTo("refresh");
        verify(userSessionRepository).save(any());
        verify(oauthLoginCodeRepository).delete(loginCode);
    }

    @Test
    @DisplayName("access token cannot be used at the refresh endpoint")
    void refreshTokens_accessToken_rejected() {
        TokenRefreshRequest request = new TokenRefreshRequest();
        ReflectionTestUtils.setField(request, "refreshToken", "access-token");
        given(jwtProvider.validateToken("access-token")).willReturn(true);
        given(jwtProvider.isRefreshToken("access-token")).willReturn(false);

        assertThatThrownBy(() -> appUserService.refreshTokens(request))
                .isInstanceOfSatisfying(KeepEatException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TOKEN));
    }

    @Test
    @DisplayName("logout revokes only the authenticated session and its device tokens")
    void logout_currentSession_revoked() {
        AppUser user = new AppUser("local", EMAIL, "encoded", Role.ROLE_USER);
        given(appUserRepository.findById(USER_ID)).willReturn(Optional.of(user));

        appUserService.logout(USER_ID, "session-1", new LogoutRequest(null, "ExpoPushToken[token]"));

        verify(userSessionRepository).deleteByIdAndUserId("session-1", USER_ID);
        verify(deviceTokenRepository).deleteAllBySessionId("session-1");
        verify(deviceTokenRepository).deleteByUserIdAndToken(USER_ID, "ExpoPushToken[token]");
    }

    @Test
    @DisplayName("password change revokes sessions, push tokens, and outstanding reset tokens")
    void changePassword_revokesAllCredentials() {
        AppUser user = new AppUser("local", EMAIL, "encoded-old", Role.ROLE_USER);
        given(appUserRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("old-password", "encoded-old")).willReturn(true);
        given(passwordEncoder.matches("NewPassword1!", "encoded-old")).willReturn(false);
        given(passwordEncoder.encode("NewPassword1!")).willReturn("encoded-new");

        appUserService.changePassword(
                USER_ID,
                new PasswordChangeRequest("old-password", "NewPassword1!")
        );

        assertThat(user.getPassword()).isEqualTo("encoded-new");
        verify(userSessionRepository).deleteAllByUserId(USER_ID);
        verify(deviceTokenRepository).deleteAllByUserId(USER_ID);
        verify(passwordResetTokenRepository).deleteAllByUserId(USER_ID);
    }

    @Test
    @DisplayName("password reset consumes every outstanding reset token for the user")
    void resetPassword_revokesAllResetTokens() {
        String rawToken = "reset-token";
        PasswordResetToken resetToken = PasswordResetToken.issue(
                AppUserService.hashToken(rawToken),
                USER_ID,
                LocalDateTime.now().plusMinutes(5)
        );
        AppUser user = new AppUser("local", EMAIL, "encoded-old", Role.ROLE_USER);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        given(passwordResetTokenRepository.findByTokenHashForUpdate(AppUserService.hashToken(rawToken)))
                .willReturn(Optional.of(resetToken));
        given(appUserRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(passwordEncoder.encode("NewPassword1!")).willReturn("encoded-new");

        appUserService.resetPassword(new PasswordResetRequest(rawToken, "NewPassword1!"));

        assertThat(user.getPassword()).isEqualTo("encoded-new");
        verify(passwordResetTokenRepository).deleteAllByUserId(USER_ID);
        verify(userSessionRepository).deleteAllByUserId(USER_ID);
        verify(deviceTokenRepository).deleteAllByUserId(USER_ID);
    }
}
