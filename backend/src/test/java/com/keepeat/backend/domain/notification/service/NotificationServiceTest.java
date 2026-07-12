package com.keepeat.backend.domain.notification.service;

import com.keepeat.backend.domain.common.exception.ErrorCode;
import com.keepeat.backend.domain.common.exception.KeepEatException;
import com.keepeat.backend.domain.notification.entity.DeviceToken;
import com.keepeat.backend.domain.notification.entity.Notification;
import com.keepeat.backend.domain.notification.entity.NotificationType;
import com.keepeat.backend.domain.notification.repository.DeviceTokenRepository;
import com.keepeat.backend.domain.notification.repository.NotificationRepository;
import com.keepeat.backend.domain.user.entity.AppUser;
import com.keepeat.backend.domain.user.entity.Role;
import com.keepeat.backend.domain.user.repository.AppUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @InjectMocks
    private NotificationService notificationService;

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private ExpoPushService expoPushService;

    @Mock
    private AppUserRepository appUserRepository;

    @Test
    @DisplayName("registerToken reassigns existing token to current user")
    void registerToken_existingToken_reassignsOwner() {
        AppUser user = new AppUser("user", "user@test.com", "encoded", Role.ROLE_USER);
        DeviceToken token = new DeviceToken(OTHER_USER_ID, "expo-token");
        given(appUserRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(deviceTokenRepository.findByToken("expo-token")).willReturn(Optional.of(token));

        notificationService.registerToken(USER_ID, "expo-token");

        assertThat(token.getUserId()).isEqualTo(USER_ID);
        verify(deviceTokenRepository, never()).save(any(DeviceToken.class));
    }

    @Test
    @DisplayName("getNotifications rejects invalid page size")
    void getNotifications_invalidSize_throws() {
        AppUser user = new AppUser("user", "user@test.com", "encoded", Role.ROLE_USER);
        given(appUserRepository.findById(USER_ID)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> notificationService.getNotifications(USER_ID, null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
    }

    @Test
    @DisplayName("markAsRead rejects another user's notification")
    void markAsRead_otherUserNotification_forbidden() {
        AppUser user = new AppUser("user", "user@test.com", "encoded", Role.ROLE_USER);
        Notification notification = new Notification(OTHER_USER_ID, "title", "body", NotificationType.NOTICE, null);
        given(appUserRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(notificationRepository.findById(10L)).willReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.markAsRead(10L, USER_ID))
                .isInstanceOfSatisfying(KeepEatException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOTIFICATION_ACCESS_DENIED));
    }

    @Test
    @DisplayName("sendNotificationOnce skips duplicated dedupe key")
    void sendNotificationOnce_duplicateKey_skips() {
        given(notificationRepository.existsByDedupeKey("dedupe")).willReturn(true);

        boolean sent = notificationService.sendNotificationOnce(
                USER_ID, "title", "body", NotificationType.EXPIRY_SOON, null, "dedupe");

        assertThat(sent).isFalse();
        verify(notificationRepository, never()).saveAndFlush(any(Notification.class));
    }
}
