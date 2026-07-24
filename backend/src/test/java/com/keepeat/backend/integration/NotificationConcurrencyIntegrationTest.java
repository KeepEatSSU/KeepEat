package com.keepeat.backend.integration;

import com.keepeat.backend.domain.notification.entity.DeviceToken;
import com.keepeat.backend.domain.notification.entity.NotificationType;
import com.keepeat.backend.domain.notification.repository.DeviceTokenRepository;
import com.keepeat.backend.domain.notification.service.NotificationService;
import com.keepeat.backend.domain.user.entity.AppUser;
import com.keepeat.backend.domain.user.entity.Role;
import com.keepeat.backend.domain.user.repository.AppUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class NotificationConcurrencyIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Test
    @DisplayName("동일 dedupeKey 알림은 DB에서 원자적으로 한 번만 생성된다")
    void notificationDedupeIsAtomic() {
        AppUser user = savePushDisabledUser();
        String dedupeKey = "test:" + UUID.randomUUID();

        boolean first = notificationService.sendNotificationOnce(
                user.getId(), "title", "body", NotificationType.NOTICE, null, dedupeKey
        );
        boolean second = notificationService.sendNotificationOnce(
                user.getId(), "title", "body", NotificationType.NOTICE, null, dedupeKey
        );

        assertThat(first).isTrue();
        assertThat(second).isFalse();
    }

    @Test
    @DisplayName("동일 Expo 토큰 재등록은 중복 row 없이 최신 세션으로 갱신된다")
    void deviceTokenRegistrationIsIdempotent() {
        AppUser user = savePushDisabledUser();
        String token = "ExpoPushToken[" + UUID.randomUUID().toString().replace("-", "") + ']';

        notificationService.registerToken(user.getId(), "session-1", token);
        notificationService.registerToken(user.getId(), "session-2", token);

        List<DeviceToken> tokens = deviceTokenRepository.findAllByUserId(user.getId());
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getSessionId()).isEqualTo("session-2");
    }

    private AppUser savePushDisabledUser() {
        AppUser user = new AppUser(
                "user",
                "notification-" + UUID.randomUUID() + "@test.com",
                "encoded",
                Role.ROLE_USER
        );
        user.setNotificationEnabled(false);
        return appUserRepository.saveAndFlush(user);
    }
}
