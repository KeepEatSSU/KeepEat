package com.keepeat.backend.domain.user.service;

import com.keepeat.backend.domain.common.exception.ErrorCode;
import com.keepeat.backend.domain.common.exception.KeepEatException;
import com.keepeat.backend.domain.notification.repository.DeviceTokenRepository;
import com.keepeat.backend.domain.notification.repository.NotificationRepository;
import com.keepeat.backend.domain.recipe.repository.RecipeReactionRepository;
import com.keepeat.backend.domain.recipe.repository.UserRecipeRepository;
import com.keepeat.backend.domain.security.JwtProvider;
import com.keepeat.backend.domain.user.dto.LoginRequest;
import com.keepeat.backend.domain.user.dto.LogoutRequest;
import com.keepeat.backend.domain.user.dto.PasswordChangeRequest;
import com.keepeat.backend.domain.user.dto.PasswordResetRequest;
import com.keepeat.backend.domain.user.dto.PasswordResetTokenResponse;
import com.keepeat.backend.domain.user.dto.SignUpRequest;
import com.keepeat.backend.domain.user.dto.TokenRefreshRequest;
import com.keepeat.backend.domain.user.dto.TokenResponse;
import com.keepeat.backend.domain.user.dto.UserDeleteRequest;
import com.keepeat.backend.domain.user.dto.UserResponse;
import com.keepeat.backend.domain.user.entity.AppUser;
import com.keepeat.backend.domain.user.entity.EmailAuth;
import com.keepeat.backend.domain.user.entity.OAuthLoginCode;
import com.keepeat.backend.domain.user.entity.PasswordResetToken;
import com.keepeat.backend.domain.user.entity.Role;
import com.keepeat.backend.domain.user.entity.UserSession;
import com.keepeat.backend.domain.user.repository.AppUserRepository;
import com.keepeat.backend.domain.user.repository.EmailAuthRepository;
import com.keepeat.backend.domain.user.repository.OAuthLoginCodeRepository;
import com.keepeat.backend.domain.user.repository.PasswordResetTokenRepository;
import com.keepeat.backend.domain.user.repository.UserSessionRepository;
import com.keepeat.backend.domain.useringredient.UserIngredientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppUserService {

    private static final String LOCAL_PROVIDER = "LOCAL";
    private static final int OAUTH_LOGIN_CODE_EXPIRATION_MINUTES = 3;
    private static final int PASSWORD_RESET_TOKEN_EXPIRATION_MINUTES = 10;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final EmailAuthRepository emailAuthRepository;
    private final EmailService emailService;
    private final DeviceTokenRepository deviceTokenRepository;
    private final NotificationRepository notificationRepository;
    private final OAuthLoginCodeRepository oauthLoginCodeRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserSessionRepository userSessionRepository;
    private final UserIngredientRepository userIngredientRepository;
    private final UserRecipeRepository userRecipeRepository;
    private final RecipeReactionRepository recipeReactionRepository;

    @Transactional
    public Long signUp(SignUpRequest request) {
        String email = EmailNormalizer.normalize(request.email());
        EmailAuth emailAuth = emailAuthRepository.findWithLockByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("이메일 인증을 진행해 주세요."));

        if (emailAuth.isExpired(LocalDateTime.now())) {
            emailAuthRepository.delete(emailAuth);
            throw new IllegalArgumentException("이메일 인증 시간이 만료되었습니다. 다시 인증해 주세요.");
        }
        if (!emailAuth.isVerified()) {
            throw new IllegalArgumentException("이메일 인증이 완료되지 않았습니다.");
        }
        if (appUserRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        AppUser newUser = new AppUser(
                request.nickname().trim(),
                email,
                passwordEncoder.encode(request.password()),
                Role.ROLE_USER
        );

        emailAuthRepository.delete(emailAuth);
        return appUserRepository.save(newUser).getId();
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        String email = EmailNormalizer.normalize(request.getEmail());
        AppUser user = appUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> {
                    log.warn("로그인 실패 - 미가입 계정");
                    return new KeepEatException(ErrorCode.INVALID_CREDENTIALS);
                });

        if (!LOCAL_PROVIDER.equals(user.getProvider())
                || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("로그인 실패 - 자격 증명 불일치: userId={}", user.getId());
            throw new KeepEatException(ErrorCode.INVALID_CREDENTIALS);
        }

        return issueNewSessionTokens(user);
    }

    @Transactional
    public void logout(Long userId, String authenticatedSessionId, LogoutRequest request) {
        AppUser user = findActiveUser(userId);
        String sessionId = authenticatedSessionId;

        if (sessionId == null && request != null && request.refreshToken() != null
                && jwtProvider.validateToken(request.refreshToken())) {
            sessionId = jwtProvider.getSessionId(request.refreshToken());
        }

        if (sessionId != null) {
            userSessionRepository.deleteByIdAndUserId(sessionId, userId);
            deviceTokenRepository.deleteAllBySessionId(sessionId);
            if (request != null && request.deviceToken() != null && !request.deviceToken().isBlank()) {
                deviceTokenRepository.deleteByUserIdAndToken(userId, request.deviceToken());
            }
        } else {
            // 배포 전 발급된 sid 없는 토큰은 세션을 특정할 수 없어 전체 로그아웃한다.
            userSessionRepository.deleteAllByUserId(userId);
            if (request != null && request.deviceToken() != null && !request.deviceToken().isBlank()) {
                deviceTokenRepository.deleteByUserIdAndToken(userId, request.deviceToken());
            } else {
                deviceTokenRepository.deleteAllByUserId(userId);
            }
        }
        user.clearRefreshToken();
    }

    @Transactional
    public void logoutAll(Long userId) {
        AppUser user = findActiveUser(userId);
        userSessionRepository.deleteAllByUserId(userId);
        deviceTokenRepository.deleteAllByUserId(userId);
        user.clearRefreshToken();
    }

    @Transactional
    public TokenResponse refreshTokens(TokenRefreshRequest request) {
        String rawRefreshToken = request.getRefreshToken();
        if (!jwtProvider.validateToken(rawRefreshToken) || !jwtProvider.isRefreshToken(rawRefreshToken)) {
            throw new KeepEatException(ErrorCode.INVALID_TOKEN);
        }

        Long userId = jwtProvider.getId(rawRefreshToken);
        AppUser user = findActiveUser(userId);
        String sessionId = jwtProvider.getSessionId(rawRefreshToken);

        if (sessionId == null) {
            // 기존 버전의 refresh token을 새 세션 구조로 한 번만 승격한다.
            if (user.getRefreshToken() == null || !hashToken(rawRefreshToken).equals(user.getRefreshToken())) {
                throw new KeepEatException(ErrorCode.INVALID_TOKEN);
            }
            user.clearRefreshToken();
            return issueNewSessionTokens(user);
        }

        UserSession session = userSessionRepository.findByIdAndUserIdForUpdate(sessionId, userId)
                .filter(found -> found.isUsable(Instant.now()))
                .filter(found -> MessageDigest.isEqual(
                        found.getRefreshTokenHash().getBytes(StandardCharsets.UTF_8),
                        hashToken(rawRefreshToken).getBytes(StandardCharsets.UTF_8)))
                .orElseThrow(() -> new KeepEatException(ErrorCode.INVALID_TOKEN));

        return rotateSessionTokens(user, session);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserInfo(Long userId) {
        AppUser user = findActiveUser(userId);
        return new UserResponse(
                user.getId(), user.getEmail(), user.getNickname(), user.getRole(),
                user.getProvider(), user.isNotificationEnabled()
        );
    }

    @Transactional
    public void deleteUser(Long userId, UserDeleteRequest request) {
        AppUser user = findActiveUser(userId);
        if (LOCAL_PROVIDER.equals(user.getProvider())) {
            if (request == null || request.password() == null || request.password().isBlank()) {
                throw new IllegalArgumentException("탈퇴를 위해서는 현재 비밀번호 입력이 필요합니다.");
            }
            if (!passwordEncoder.matches(request.password(), user.getPassword())) {
                throw new KeepEatException(ErrorCode.INVALID_CREDENTIALS);
            }
        }

        String originalEmail = user.getEmail();
        recipeReactionRepository.deleteAllByAppUserId(userId);
        userRecipeRepository.deleteAllByAppUserId(userId);
        userIngredientRepository.deleteAllByUserId(userId);
        notificationRepository.deleteAllByUserId(userId);
        deviceTokenRepository.deleteAllByUserId(userId);
        userSessionRepository.deleteAllByUserId(userId);
        oauthLoginCodeRepository.deleteAllByUserId(userId);
        passwordResetTokenRepository.deleteAllByUserId(userId);
        emailAuthRepository.findByEmail(originalEmail).ifPresent(emailAuthRepository::delete);

        user.markAsDeleted();
        appUserRepository.flush();
        appUserRepository.delete(user);
        log.info("유저 탈퇴 및 개인정보 익명화 완료: userId={}", userId);
    }

    @Transactional
    public boolean setNotificationEnabled(Long userId, boolean enabled) {
        AppUser user = findActiveUser(userId);
        user.setNotificationEnabled(enabled);
        return user.isNotificationEnabled();
    }

    public void requestPasswordReset(String rawEmail) {
        String email = EmailNormalizer.normalize(rawEmail);
        appUserRepository.findByEmailIgnoreCase(email).ifPresentOrElse(user -> {
            if (!LOCAL_PROVIDER.equals(user.getProvider())) {
                emailService.sendPasswordResetUnavailable(email);
                return;
            }
            emailService.sendPasswordResetCode(email);
        }, () -> log.warn("비밀번호 재설정 요청 - 미가입 계정"));
    }

    @Transactional(noRollbackFor = EmailVerificationException.class)
    public PasswordResetTokenResponse verifyPasswordReset(String rawEmail, String code) {
        String email = EmailNormalizer.normalize(rawEmail);
        AppUser user = appUserRepository.findByEmailIgnoreCase(email)
                .filter(found -> LOCAL_PROVIDER.equals(found.getProvider()))
                .orElseThrow(() -> new IllegalArgumentException("인증번호가 올바르지 않거나 만료되었습니다."));

        emailService.consumePasswordResetCode(email, code);
        passwordResetTokenRepository.deleteAllByUserId(user.getId());
        passwordResetTokenRepository.deleteAllByExpiresAtBefore(LocalDateTime.now());

        String rawToken = generateOpaqueCode();
        passwordResetTokenRepository.save(PasswordResetToken.issue(
                hashToken(rawToken),
                user.getId(),
                LocalDateTime.now().plusMinutes(PASSWORD_RESET_TOKEN_EXPIRATION_MINUTES)
        ));
        return new PasswordResetTokenResponse(rawToken, PASSWORD_RESET_TOKEN_EXPIRATION_MINUTES * 60L);
    }

    @Transactional
    public void resetPassword(PasswordResetRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByTokenHashForUpdate(hashToken(request.resetToken()))
                .filter(token -> token.isUsable(LocalDateTime.now()))
                .orElseThrow(() -> new KeepEatException(ErrorCode.INVALID_TOKEN));

        AppUser user = findActiveUser(resetToken.getUserId());
        user.updatePassword(passwordEncoder.encode(request.newPassword()));
        user.clearRefreshToken();
        userSessionRepository.deleteAllByUserId(user.getId());
        deviceTokenRepository.deleteAllByUserId(user.getId());
        passwordResetTokenRepository.deleteAllByUserId(user.getId());
    }

    @Transactional
    public void changePassword(Long userId, PasswordChangeRequest request) {
        AppUser user = findActiveUser(userId);
        if (!LOCAL_PROVIDER.equals(user.getProvider())) {
            throw new IllegalArgumentException("소셜 로그인 계정은 비밀번호를 변경할 수 없습니다.");
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new KeepEatException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new IllegalArgumentException("새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }

        user.updatePassword(passwordEncoder.encode(request.newPassword()));
        user.clearRefreshToken();
        userSessionRepository.deleteAllByUserId(userId);
        deviceTokenRepository.deleteAllByUserId(userId);
        passwordResetTokenRepository.deleteAllByUserId(userId);
        log.info("비밀번호 변경 및 전체 세션 폐기 완료: userId={}", userId);
    }

    @Transactional
    public String issueOAuthLoginCode(Long userId) {
        AppUser user = findActiveUser(userId);
        LocalDateTime now = LocalDateTime.now();
        oauthLoginCodeRepository.deleteAllByExpiresAtBefore(now);

        String code = generateOpaqueCode();
        oauthLoginCodeRepository.save(OAuthLoginCode.issue(
                hashToken(code), user.getId(), now.plusMinutes(OAUTH_LOGIN_CODE_EXPIRATION_MINUTES)
        ));
        return code;
    }

    @Transactional
    public TokenResponse exchangeOAuthLoginCode(String code) {
        OAuthLoginCode loginCode = oauthLoginCodeRepository.findByCodeHashForUpdate(hashToken(code))
                .orElseThrow(() -> new KeepEatException(ErrorCode.INVALID_TOKEN));
        LocalDateTime now = LocalDateTime.now();
        if (!loginCode.isUsable(now)) {
            oauthLoginCodeRepository.delete(loginCode);
            throw new KeepEatException(ErrorCode.INVALID_TOKEN);
        }

        AppUser user = findActiveUser(loginCode.getUserId());
        loginCode.consume(now);
        oauthLoginCodeRepository.delete(loginCode);
        return issueNewSessionTokens(user);
    }

    public static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }

    private AppUser findActiveUser(Long userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new KeepEatException(ErrorCode.USER_NOT_FOUND));
    }

    private TokenResponse issueNewSessionTokens(AppUser user) {
        String sessionId = UUID.randomUUID().toString();
        String accessToken = jwtProvider.createAccessToken(user.getEmail(), user.getId(), user.getRole(), sessionId);
        String rawRefreshToken = jwtProvider.createRefreshToken(user.getEmail(), user.getId(), user.getRole(), sessionId);
        userSessionRepository.save(UserSession.create(
                sessionId,
                user.getId(),
                hashToken(rawRefreshToken),
                Instant.now().plusMillis(jwtProvider.getRefreshTokenValidityTime())
        ));
        return new TokenResponse(accessToken, rawRefreshToken);
    }

    private TokenResponse rotateSessionTokens(AppUser user, UserSession session) {
        String accessToken = jwtProvider.createAccessToken(user.getEmail(), user.getId(), user.getRole(), session.getId());
        String rawRefreshToken = jwtProvider.createRefreshToken(user.getEmail(), user.getId(), user.getRole(), session.getId());
        session.rotate(
                hashToken(rawRefreshToken),
                Instant.now().plusMillis(jwtProvider.getRefreshTokenValidityTime())
        );
        return new TokenResponse(accessToken, rawRefreshToken);
    }

    private String generateOpaqueCode() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
