package com.keepeat.backend.integration;

import com.keepeat.backend.domain.user.entity.AppUser;
import com.keepeat.backend.domain.user.entity.EmailAuth;
import com.keepeat.backend.domain.user.entity.Role;
import com.keepeat.backend.domain.user.repository.AppUserRepository;
import com.keepeat.backend.domain.user.repository.EmailAuthRepository;
import com.keepeat.backend.domain.user.service.AppUserService;
import com.keepeat.backend.domain.user.service.EmailService;
import com.keepeat.backend.domain.user.service.EmailVerificationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class EmailVerificationPersistenceIntegrationTest {

    @Autowired
    private EmailService emailService;

    @Autowired
    private AppUserService appUserService;

    @Autowired
    private EmailAuthRepository emailAuthRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Test
    @DisplayName("회원가입 인증 실패 횟수는 예외 응답 후에도 커밋된다")
    void failedSignupVerificationIsCommitted() {
        String email = uniqueEmail("signup");
        saveEmailAuth(email, "123456");

        assertThatThrownBy(() -> emailService.verifyAuthCode(email, "000000"))
                .isInstanceOf(EmailVerificationException.class);

        EmailAuth reloaded = emailAuthRepository.findByEmail(email).orElseThrow();
        assertThat(reloaded.getFailedAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("비밀번호 재설정 인증 실패도 외부 트랜잭션 롤백 없이 누적된다")
    void failedPasswordResetVerificationIsCommitted() {
        String email = uniqueEmail("reset");
        appUserRepository.saveAndFlush(new AppUser("user", email, "encoded", Role.ROLE_USER));
        saveEmailAuth(email, "123456");

        assertThatThrownBy(() -> appUserService.verifyPasswordReset(email, "000000"))
                .isInstanceOf(EmailVerificationException.class);

        EmailAuth reloaded = emailAuthRepository.findByEmail(email).orElseThrow();
        assertThat(reloaded.getFailedAttempts()).isEqualTo(1);
    }

    private void saveEmailAuth(String email, String rawCode) {
        emailAuthRepository.saveAndFlush(EmailAuth.builder()
                .email(email)
                .authCode(AppUserService.hashToken(rawCode))
                .expiredAt(LocalDateTime.now().plusMinutes(5))
                .build());
    }

    private String uniqueEmail(String prefix) {
        return prefix + '-' + UUID.randomUUID() + "@test.com";
    }
}
