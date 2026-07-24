package com.keepeat.backend.domain.notification.scheduler;

import com.keepeat.backend.domain.notification.repository.NotificationRepository;
import com.keepeat.backend.domain.user.repository.EmailAuthRepository;
import com.keepeat.backend.domain.user.repository.OAuthLoginCodeRepository;
import com.keepeat.backend.domain.user.repository.PasswordResetTokenRepository;
import com.keepeat.backend.domain.user.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRetentionScheduler {

    private final NotificationRepository notificationRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final OAuthLoginCodeRepository oauthLoginCodeRepository;
    private final EmailAuthRepository emailAuthRepository;

    @Value("${app.notification.retention-days:90}")
    private long retentionDays;

    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Seoul")
    @Transactional
    public void deleteExpiredHistory() {
        int deleted = notificationRepository.deleteExpiredBefore(
                Instant.now().minus(Math.max(1, retentionDays), ChronoUnit.DAYS)
        );
        if (deleted > 0) {
            log.info("오래된 알림 기록 정리 완료: count={}", deleted);
        }
        userSessionRepository.deleteAllByExpiresAtBefore(Instant.now());
        passwordResetTokenRepository.deleteAllByExpiresAtBefore(LocalDateTime.now());
        oauthLoginCodeRepository.deleteAllByExpiresAtBefore(LocalDateTime.now());
        emailAuthRepository.deleteAllByExpiredAtBefore(LocalDateTime.now());
    }
}
