package com.keepeat.backend.domain.user.service;

import com.keepeat.backend.domain.notification.repository.DeviceTokenRepository;
import com.keepeat.backend.domain.security.JwtProvider;
import com.keepeat.backend.domain.user.dto.LoginRequest;
import com.keepeat.backend.domain.user.dto.PasswordChangeRequest;
import com.keepeat.backend.domain.user.dto.SignUpRequest;
import com.keepeat.backend.domain.user.dto.TokenRefreshRequest;
import com.keepeat.backend.domain.user.dto.TokenResponse;
import com.keepeat.backend.domain.user.dto.UserDeleteRequest;
import com.keepeat.backend.domain.user.dto.UserResponse;
import com.keepeat.backend.domain.user.entity.AppUser;
import com.keepeat.backend.domain.user.entity.EmailAuth;
import com.keepeat.backend.domain.user.entity.OAuthLoginCode;
import com.keepeat.backend.domain.user.entity.Role;
import com.keepeat.backend.domain.user.repository.AppUserRepository;
import com.keepeat.backend.domain.user.repository.EmailAuthRepository;
import com.keepeat.backend.domain.user.repository.OAuthLoginCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppUserService {

    private static final String LOCAL_PROVIDER = "LOCAL";
    private static final int OAUTH_LOGIN_CODE_EXPIRATION_MINUTES = 3;
    private static final char[] PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#$".toCharArray();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final EmailAuthRepository emailAuthRepository;
    private final EmailService emailService;
    private final DeviceTokenRepository deviceTokenRepository;
    private final OAuthLoginCodeRepository oauthLoginCodeRepository;

    @Transactional
    public Long signUp(SignUpRequest request) {
        EmailAuth emailAuth = emailAuthRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("이메일 인증을 진행해 주세요."));

        if (emailAuth.isExpired(LocalDateTime.now())) {
            emailAuthRepository.delete(emailAuth);
            throw new IllegalArgumentException("이메일 인증 시간이 만료되었습니다. 다시 인증해 주세요.");
        }

        if (!emailAuth.isVerified()) {
            throw new IllegalArgumentException("이메일 인증이 완료되지 않았습니다.");
        }

        if (appUserRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        AppUser newUser = new AppUser(
                request.nickname(),
                request.email(),
                encodedPassword,
                Role.ROLE_USER
        );

        emailAuthRepository.delete(emailAuth);
        return appUserRepository.save(newUser).getId();
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        AppUser user = appUserRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    log.warn("로그인 실패 - 미가입 이메일: {}", request.getEmail());
                    return new IllegalArgumentException("이메일 또는 비밀번호가 일치하지 않습니다.");
                });

        if (!LOCAL_PROVIDER.equals(user.getProvider())) {
            log.warn("로그인 실패 - 로컬 계정이 아님: userId={}, provider={}", user.getId(), user.getProvider());
            throw new IllegalArgumentException("이메일 또는 비밀번호가 일치하지 않습니다.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("로그인 실패 - 비밀번호 불일치: userId={}", user.getId());
            throw new IllegalArgumentException("이메일 또는 비밀번호가 일치하지 않습니다.");
        }

        return issueTokens(user);
    }

    @Transactional
    public void logout(Long userId) {
        AppUser user = findActiveUser(userId);
        user.clearRefreshToken();
    }

    @Transactional
    public TokenResponse refreshTokens(TokenRefreshRequest request) {
        String rawRefreshToken = request.getRefreshToken();

        if (!jwtProvider.validateToken(rawRefreshToken)) {
            throw new IllegalArgumentException("유효하지 않거나 만료된 Refresh Token입니다. 다시 로그인해주세요.");
        }

        Long userId = jwtProvider.getId(rawRefreshToken);
        AppUser user = findActiveUser(userId);

        if (user.getRefreshToken() == null || !hashToken(rawRefreshToken).equals(user.getRefreshToken())) {
            throw new IllegalArgumentException("이미 로그아웃 되었거나 무효화된 토큰입니다. 다시 로그인해주세요.");
        }

        return issueTokens(user);
    }

    public UserResponse getUserInfo(Long userId) {
        AppUser user = findActiveUser(userId);
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole(),
                user.getProvider(),
                user.isNotificationEnabled()
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
                throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
            }
        }

        deviceTokenRepository.deleteAllByUserId(userId);
        user.markAsDeleted();
        appUserRepository.flush();
        appUserRepository.delete(user);

        log.info("유저 ID [{}] 탈퇴 완료 (soft delete, provider={})", userId, user.getProvider());
    }

    @Transactional
    public boolean toggleNotification(Long userId) {
        AppUser user = findActiveUser(userId);
        user.toggleNotification(!user.isNotificationEnabled());
        return user.isNotificationEnabled();
    }

    @Transactional
    public void requestPasswordReset(String email) {
        appUserRepository.findByEmail(email).ifPresentOrElse(user -> {
            if (!LOCAL_PROVIDER.equals(user.getProvider())) {
                emailService.sendPasswordResetUnavailable(email);
                return;
            }
            emailService.sendPasswordResetCode(email);
        }, () -> log.warn("비밀번호 재설정 요청 - 미가입 이메일: {}", email));
    }

    @Transactional
    public void verifyPasswordResetAndSendTemporaryPassword(String email, String code) {
        AppUser user = appUserRepository.findByEmail(email)
                .filter(found -> LOCAL_PROVIDER.equals(found.getProvider()))
                .orElseThrow(() -> new IllegalArgumentException("인증번호가 올바르지 않거나 만료되었습니다."));

        emailService.consumePasswordResetCode(email, code);

        String tempPassword = generateRandomPassword();
        user.updatePassword(passwordEncoder.encode(tempPassword));
        user.clearRefreshToken();

        emailService.sendTemporaryPassword(email, tempPassword);
    }

    @Transactional
    public void changePassword(Long userId, PasswordChangeRequest request) {
        AppUser user = findActiveUser(userId);

        if (!LOCAL_PROVIDER.equals(user.getProvider())) {
            throw new IllegalArgumentException("소셜 로그인 계정은 비밀번호를 변경할 수 없습니다.");
        }

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }

        user.updatePassword(passwordEncoder.encode(request.newPassword()));
        user.clearRefreshToken();

        log.info("유저 ID [{}]의 비밀번호가 성공적으로 변경되었습니다.", userId);
    }

    @Transactional
    public String issueOAuthLoginCode(String email) {
        AppUser user = appUserRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));

        LocalDateTime now = LocalDateTime.now();
        oauthLoginCodeRepository.deleteAllByExpiresAtBefore(now);

        String code = generateOpaqueCode();
        OAuthLoginCode loginCode = OAuthLoginCode.issue(
                hashToken(code),
                user.getId(),
                now.plusMinutes(OAUTH_LOGIN_CODE_EXPIRATION_MINUTES)
        );
        oauthLoginCodeRepository.save(loginCode);

        return code;
    }

    @Transactional
    public TokenResponse exchangeOAuthLoginCode(String code) {
        OAuthLoginCode loginCode = oauthLoginCodeRepository.findByCodeHash(hashToken(code))
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않거나 만료된 OAuth 로그인 코드입니다."));

        LocalDateTime now = LocalDateTime.now();
        if (!loginCode.isUsable(now)) {
            oauthLoginCodeRepository.delete(loginCode);
            throw new IllegalArgumentException("유효하지 않거나 만료된 OAuth 로그인 코드입니다.");
        }

        AppUser user = findActiveUser(loginCode.getUserId());
        loginCode.consume(now);
        oauthLoginCodeRepository.delete(loginCode);

        return issueTokens(user);
    }

    public static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("해싱 알고리즘을 찾을 수 없습니다.", e);
        }
    }

    private AppUser findActiveUser(Long userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));
    }

    private TokenResponse issueTokens(AppUser user) {
        String accessToken = jwtProvider.createAccessToken(user.getEmail(), user.getId(), user.getRole());
        String rawRefreshToken = jwtProvider.createRefreshToken(user.getEmail(), user.getId(), user.getRole());
        user.updateRefreshToken(hashToken(rawRefreshToken));
        return new TokenResponse(accessToken, rawRefreshToken);
    }

    private String generateRandomPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(PASSWORD_CHARS[SECURE_RANDOM.nextInt(PASSWORD_CHARS.length)]);
        }
        return sb.toString();
    }

    private String generateOpaqueCode() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
