package com.keepeat.backend.domain.user.service;

import com.keepeat.backend.domain.user.entity.EmailAuth;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final com.keepeat.backend.domain.user.repository.EmailAuthRepository emailAuthRepository;
    private static final int AUTH_CODE_EXPIRATION_MINUTES = 5; // 만료 시간 5분

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
    // 1️⃣ 인증번호 생성 및 발송
    @Transactional
    public void sendVerificationCode(String email) {
        // 이미 가입된 이메일인지 체크 (옵션)
        // appUserRepository.findByEmail(email).ifPresent(u -> { throw new ... });

        // 6자리 난수 인증번호 생성
        String authCode = String.format("%06d", new java.util.Random().nextInt(1000000));
        LocalDateTime expiredAt = LocalDateTime.now().plusMinutes(AUTH_CODE_EXPIRATION_MINUTES);

        // 기존에 발급받은 내역이 있다면 덮어쓰고, 없으면 새로 생성
        EmailAuth emailAuth = emailAuthRepository.findByEmail(email)
                .orElseGet(() -> EmailAuth.builder().email(email).build());

        emailAuth.updateAuthCode(authCode, expiredAt);
        emailAuthRepository.save(emailAuth);

        // 이메일 발송
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("[KeepEat] 회원가입 이메일 인증번호");
        message.setText("안녕하세요! KeepEat 회원가입 인증번호는 [" + authCode + "] 입니다.\n5분 안에 입력해 주세요.");

        mailSender.send(message);
        log.info("📧 [{}] 회원가입 인증번호 이메일 발송 완료", email);
    }

    // 2️⃣ 인증번호 검증 로직
    @Transactional
    public void verifyAuthCode(String email, String inputCode) {
        EmailAuth emailAuth = emailAuthRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("인증 요청 내역이 없습니다. 인증번호를 다시 발급받아 주세요."));

        // 시간 만료 체크 로직 🔥 (RDBMS 방식의 핵심!)
        if (LocalDateTime.now().isAfter(emailAuth.getExpiredAt())) {
            throw new IllegalArgumentException("인증 시간이 만료되었습니다. 다시 시도해 주세요.");
        }

        // 인증번호 일치 여부 체크
        if (!emailAuth.getAuthCode().equals(inputCode)) {
            throw new IllegalArgumentException("인증번호가 일치하지 않습니다.");
        }

        // 모두 통과하면 인증 완료(isVerified = true) 처리
        emailAuth.verify();
    }
}