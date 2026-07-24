package com.keepeat.backend.domain.notification.service;

import com.keepeat.backend.domain.common.exception.ErrorCode;
import com.keepeat.backend.domain.common.exception.KeepEatException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Long USER_ID = 1L;

    @InjectMocks
    private NotificationService notificationService;

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private PushDispatchService pushDispatchService;

    @Mock
    private AppUserRepository appUserRepository;

    @Test
    @DisplayName("registerToken atomically registers or updates the current session")
    void registerToken_upsertsCurrentSession() {
        AppUser user = new AppUser("user", "user@test.com", "encoded", Role.ROLE_USER);
        given(appUserRepository.findById(USER_ID)).willReturn(Optional.of(user));

        notificationService.registerToken(USER_ID, "session-id", "expo-token");

        verify(deviceTokenRepository).upsertToken(
                eq(USER_ID),
                eq("session-id"),
                eq("expo-token"),
                any(java.time.LocalDateTime.class)
        );
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
    @DisplayName("markAsRead does not reveal another user's notification")
    void markAsRead_otherUserNotification_notFound() {
        AppUser user = new AppUser("user", "user@test.com", "encoded", Role.ROLE_USER);
        given(appUserRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(notificationRepository.findByIdAndUserId(10L, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(10L, USER_ID))
                .isInstanceOfSatisfying(KeepEatException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND));
    }

    @Test
    @DisplayName("sendNotificationOnce skips duplicated dedupe key")
    void sendNotificationOnce_duplicateKey_skips() {
        AppUser user = new AppUser("user", "user@test.com", "encoded", Role.ROLE_USER);
        given(appUserRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(notificationRepository.insertIfAbsent(
                eq(USER_ID), eq("title"), eq("body"), eq("EXPIRY_SOON"),
                eq(null), eq("dedupe"), any(java.time.Instant.class)
        )).willReturn(0);

        boolean sent = notificationService.sendNotificationOnce(
                USER_ID, "title", "body", NotificationType.EXPIRY_SOON, null, "dedupe");

        assertThat(sent).isFalse();
        verify(notificationRepository, never()).saveAndFlush(any(Notification.class));
        verify(pushDispatchService, never()).dispatch(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("notification payloads longer than the database limit are rejected")
    void sendNotification_tooLongTitle_rejected() {
        AppUser user = new AppUser("user", "user@test.com", "encoded", Role.ROLE_USER);
        given(appUserRepository.findById(USER_ID)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> notificationService.sendNotification(
                USER_ID, "x".repeat(201), "body", NotificationType.NOTICE, null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(notificationRepository, never()).saveAndFlush(any(Notification.class));
    }
}
