package com.keepeat.backend.domain.user.service;

import com.keepeat.backend.domain.user.entity.EmailAuth;
import com.keepeat.backend.domain.user.repository.AppUserRepository;
import com.keepeat.backend.domain.user.repository.EmailAuthRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Slf4j
@Service
public class EmailService {

    private static final int AUTH_CODE_EXPIRATION_MINUTES = 5;
    private static final int MAX_AUTH_CODE_ATTEMPTS = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final JavaMailSender mailSender;
    private final EmailAuthRepository emailAuthRepository;
    private final AppUserRepository appUserRepository;
    private final TransactionTemplate transactionTemplate;
    private final Object[] issueLocks = new Object[256];

    public EmailService(
            JavaMailSender mailSender,
            EmailAuthRepository emailAuthRepository,
            AppUserRepository appUserRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.mailSender = mailSender;
        this.emailAuthRepository = emailAuthRepository;
        this.appUserRepository = appUserRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        java.util.Arrays.setAll(issueLocks, ignored -> new Object());
    }

    public void sendVerificationCode(String rawEmail) {
        String email = EmailNormalizer.normalize(rawEmail);
        if (appUserRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }

        String authCode = issueAuthCodeSafely(email);
        send(
                email,
                "[KeepEat] 회원가입 이메일 인증번호",
                "안녕하세요! KeepEat 회원가입 인증번호는 [" + authCode + "] 입니다.\n5분 안에 입력해 주세요."
        );
        log.info("회원가입 인증번호 이메일 발송 완료");
    }

    public void sendPasswordResetCode(String rawEmail) {
        String email = EmailNormalizer.normalize(rawEmail);
        String authCode = issueAuthCodeSafely(email);
        send(
                email,
                "[KeepEat] 비밀번호 재설정 인증번호",
                "안녕하세요. KeepEat 비밀번호 재설정 인증번호는 [" + authCode + "] 입니다.\n5분 안에 입력해 주세요."
        );
        log.info("비밀번호 재설정 인증번호 이메일 발송 완료");
    }

    public void sendPasswordResetUnavailable(String rawEmail) {
        String email = EmailNormalizer.normalize(rawEmail);
        send(
                email,
                "[KeepEat] 비밀번호 재설정 안내",
                """
                안녕하세요. KeepEat 서비스 팀입니다.

                해당 이메일은 소셜 로그인으로 가입된 계정입니다.
                비밀번호 재설정 대신 기존에 사용하신 소셜 로그인으로 접속해 주세요.

                감사합니다.
                """
        );
        log.info("소셜 로그인 계정 비밀번호 재설정 안내 이메일 발송 완료");
    }

    public void verifyAuthCode(String rawEmail, String inputCode) {
        String email = EmailNormalizer.normalize(rawEmail);
        String errorMessage = transactionTemplate.execute(status -> {
            EmailAuth emailAuth = emailAuthRepository.findWithLockByEmail(email).orElse(null);
            if (emailAuth == null) {
                return "인증 요청 내역이 없습니다. 인증번호를 다시 발급받아 주세요.";
            }
            String validationError = validateAuthCode(emailAuth, inputCode, "인증번호가 일치하지 않습니다.");
            if (validationError != null) {
                return validationError;
            }
            emailAuth.verify();
            return null;
        });
        throwIfVerificationFailed(errorMessage);
    }

    public void consumePasswordResetCode(String rawEmail, String inputCode) {
        String email = EmailNormalizer.normalize(rawEmail);
        String errorMessage = transactionTemplate.execute(status -> {
            EmailAuth emailAuth = emailAuthRepository.findWithLockByEmail(email).orElse(null);
            if (emailAuth == null) {
                return "인증번호가 올바르지 않거나 만료되었습니다.";
            }
            String validationError = validateAuthCode(
                    emailAuth,
                    inputCode,
                    "인증번호가 올바르지 않거나 만료되었습니다."
            );
            if (validationError != null) {
                return validationError;
            }
            emailAuthRepository.delete(emailAuth);
            return null;
        });
        throwIfVerificationFailed(errorMessage);
    }

    private String issueAuthCode(String email) {
        String authCode = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        LocalDateTime expiredAt = LocalDateTime.now().plusMinutes(AUTH_CODE_EXPIRATION_MINUTES);
        EmailAuth emailAuth = emailAuthRepository.findWithLockByEmail(email)
                .orElseGet(() -> EmailAuth.builder().email(email).build());
        emailAuth.updateAuthCode(AppUserService.hashToken(authCode), expiredAt);
        emailAuthRepository.saveAndFlush(emailAuth);
        return authCode;
    }

    private String issueAuthCodeSafely(String email) {
        Object lock = issueLocks[(email.hashCode() & Integer.MAX_VALUE) % issueLocks.length];
        synchronized (lock) {
            return transactionTemplate.execute(status -> issueAuthCode(email));
        }
    }

    private String validateAuthCode(EmailAuth emailAuth, String inputCode, String mismatchMessage) {
        LocalDateTime now = LocalDateTime.now();
        if (emailAuth.isExpired(now)) {
            emailAuthRepository.delete(emailAuth);
            return "인증 시간이 만료되었습니다. 다시 시도해 주세요.";
        }
        if (emailAuth.getFailedAttempts() >= MAX_AUTH_CODE_ATTEMPTS) {
            emailAuthRepository.delete(emailAuth);
            return "인증 시도 횟수를 초과했습니다. 인증번호를 다시 발급받아 주세요.";
        }
        if (!MessageDigestSupport.constantTimeEquals(emailAuth.getAuthCode(), AppUserService.hashToken(inputCode))) {
            emailAuth.recordFailure();
            if (emailAuth.getFailedAttempts() >= MAX_AUTH_CODE_ATTEMPTS) {
                emailAuthRepository.delete(emailAuth);
                return "인증 시도 횟수를 초과했습니다. 인증번호를 다시 발급받아 주세요.";
            }
            return mismatchMessage;
        }
        return null;
    }

    private void throwIfVerificationFailed(String errorMessage) {
        if (errorMessage != null) {
            throw new EmailVerificationException(errorMessage);
        }
    }

    private void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
        } catch (Exception e) {
            log.error("이메일 발송 실패: {}", e.getClass().getSimpleName());
            throw new IllegalStateException("이메일 발송에 실패했습니다.", e);
        }
    }

    private static final class MessageDigestSupport {
        private MessageDigestSupport() {
        }

        private static boolean constantTimeEquals(String left, String right) {
            if (left == null || right == null) {
                return false;
            }
            return java.security.MessageDigest.isEqual(
                    left.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    right.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );
        }
    }
}
