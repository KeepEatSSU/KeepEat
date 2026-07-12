package com.keepeat.backend.domain.user.service;

import com.keepeat.backend.domain.user.entity.EmailAuth;
import com.keepeat.backend.domain.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.LocalDateTime;


@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final com.keepeat.backend.domain.user.repository.EmailAuthRepository emailAuthRepository;
    private final AppUserRepository appUserRepository;
    private static final int AUTH_CODE_EXPIRATION_MINUTES = 5; // 만료 시간 5분
    private static final int MAX_AUTH_CODE_ATTEMPTS = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public void sendTemporaryPassword(String toEmail, String temporaryPassword) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("[KeepEat] 임시 비밀번호 발급 안내");
        message.setText(String.format("""
            안녕하세요. KeepEat 서비스 팀입니다.
            
            요청하신 임시 비밀번호를 아래와 같이 발급해 드립니다.
            로그인 후 마이페이지에서 반드시 비밀번호를 변경해 주세요.
            
            임시 비밀번호 : %s
            
            감사합니다.
            """, temporaryPassword));

        try {
            mailSender.send(message);
            log.info("📧 [{}] 유저에게 임시 비밀번호 이메일 발송 완료", toEmail);
        } catch (Exception e) {
            log.error("❌ 이메일 발송 중 오류 발생: {}", e.getMessage(), e);
            throw new RuntimeException("이메일 발송에 실패했습니다."); // Custom Exception으로 대체 가능
        }
    }
    // 인증번호 생성 및 발송
    @Transactional
    public void sendVerificationCode(String email) {
        // 이미 가입된 활성 유저면 인증번호 발송 자체를 거절한다.
        // existsByEmail은 AppUser의 @SQLRestriction("deleted_at IS NULL") 적용을 받아
        // 탈퇴(soft delete)된 유저의 원본 이메일은 변조되어 저장되므로 자연스럽게 통과된다.
        if (appUserRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }

        String authCode = issueAuthCode(email);

        // 이메일 발송
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("[KeepEat] 회원가입 이메일 인증번호");
        message.setText("안녕하세요! KeepEat 회원가입 인증번호는 [" + authCode + "] 입니다.\n5분 안에 입력해 주세요.");

        mailSender.send(message);
        log.info("📧 [{}] 회원가입 인증번호 이메일 발송 완료", email);
    }

    @Transactional
    public void sendPasswordResetCode(String email) {
        String authCode = issueAuthCode(email);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("[KeepEat] 비밀번호 재설정 인증번호");
        message.setText("안녕하세요. KeepEat 비밀번호 재설정 인증번호는 [" + authCode + "] 입니다.\n5분 안에 입력해 주세요.");

        mailSender.send(message);
        log.info("📧 [{}] 비밀번호 재설정 인증번호 이메일 발송 완료", email);
    }

    public void sendPasswordResetUnavailable(String email) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("[KeepEat] 비밀번호 재설정 안내");
        message.setText("""
            안녕하세요. KeepEat 서비스 팀입니다.
            
            해당 이메일은 소셜 로그인으로 가입된 계정입니다.
            비밀번호 재설정 대신 기존에 사용하신 소셜 로그인으로 접속해 주세요.
            
            감사합니다.
            """);

        mailSender.send(message);
        log.info("📧 [{}] 소셜 로그인 계정 비밀번호 재설정 안내 이메일 발송 완료", email);
    }

    // 인증번호 검증 로직
    @Transactional
    public void verifyAuthCode(String email, String inputCode) {
        EmailAuth emailAuth = emailAuthRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("인증 요청 내역이 없습니다. 인증번호를 다시 발급받아 주세요."));

        validateAuthCode(emailAuth, inputCode, "인증번호가 일치하지 않습니다.");

        // 모두 통과하면 인증 완료(isVerified = true) 처리
        emailAuth.verify();
    }

    @Transactional
    public void consumePasswordResetCode(String email, String inputCode) {
        EmailAuth emailAuth = emailAuthRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("인증번호가 올바르지 않거나 만료되었습니다."));

        validateAuthCode(emailAuth, inputCode, "인증번호가 올바르지 않거나 만료되었습니다.");
        emailAuthRepository.delete(emailAuth);
    }

    private String issueAuthCode(String email) {
        String authCode = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        LocalDateTime expiredAt = LocalDateTime.now().plusMinutes(AUTH_CODE_EXPIRATION_MINUTES);

        EmailAuth emailAuth = emailAuthRepository.findByEmail(email)
                .orElseGet(() -> EmailAuth.builder().email(email).build());

        emailAuth.updateAuthCode(authCode, expiredAt);
        emailAuthRepository.save(emailAuth);
        return authCode;
    }

    private void validateAuthCode(EmailAuth emailAuth, String inputCode, String mismatchMessage) {
        LocalDateTime now = LocalDateTime.now();

        if (emailAuth.isExpired(now)) {
            emailAuthRepository.delete(emailAuth);
            throw new IllegalArgumentException("인증 시간이 만료되었습니다. 다시 시도해 주세요.");
        }

        if (emailAuth.getFailedAttempts() >= MAX_AUTH_CODE_ATTEMPTS) {
            emailAuthRepository.delete(emailAuth);
            throw new IllegalArgumentException("인증 시도 횟수를 초과했습니다. 인증번호를 다시 발급받아 주세요.");
        }

        if (!emailAuth.getAuthCode().equals(inputCode)) {
            emailAuth.recordFailure();
            if (emailAuth.getFailedAttempts() >= MAX_AUTH_CODE_ATTEMPTS) {
                emailAuthRepository.delete(emailAuth);
                throw new IllegalArgumentException("인증 시도 횟수를 초과했습니다. 인증번호를 다시 발급받아 주세요.");
            }
            throw new IllegalArgumentException(mismatchMessage);
        }
    }
}
