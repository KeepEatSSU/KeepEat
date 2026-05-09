package com.keepeat.backend.domain.notification.service;

import com.keepeat.backend.domain.notification.dto.NotificationResponse;
import com.keepeat.backend.domain.notification.dto.PushSendRequest;
import com.keepeat.backend.domain.notification.entity.DeviceToken;
import com.keepeat.backend.domain.notification.entity.Notification;
import com.keepeat.backend.domain.notification.entity.NotificationType;
import com.keepeat.backend.domain.notification.repository.DeviceTokenRepository;
import com.keepeat.backend.domain.notification.repository.NotificationRepository;
import com.keepeat.backend.domain.user.entity.AppUser;
import com.keepeat.backend.domain.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final NotificationRepository notificationRepository;
    private final ExpoPushService expoPushService;
    private final AppUserRepository appUserRepository;

    @Transactional
    public void registerToken(Long userId, String token) {
        deviceTokenRepository.findByToken(token).orElseGet(() -> {
            DeviceToken newToken = new DeviceToken(userId, token);
            return deviceTokenRepository.save(newToken);
        });
        log.info("유저 {}의 새로운 기기 토큰이 등록되었습니다.", userId);
    }

    @Transactional
    public void sendNotification(Long userId, String title, String body, NotificationType type, String targetId) {

        // 1. 유저 정보 조회 (소문자 appUserRepository 사용!)
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        // 2. 기획 반영: 유저 알림 설정과 무관하게 히스토리(종 모양 버튼용)는 무조건 DB에 저장합니다.
        Notification notification = new Notification(userId, title, body, type, targetId);
        notificationRepository.save(notification);

        // 3. 기획 반영: 유저가 알림을 껐다면 여기서 메서드를 종료합니다. (푸시 안 쏨)
        if (!user.isNotificationEnabled()) {
            log.info("유저 {}는 알림을 끈 상태입니다. 히스토리만 저장하고 푸시 알림은 생략합니다.", userId);
            return;
        }

        // 4. 알림이 켜져 있는 유저라면 기기 토큰들을 꺼내서 엑스포로 푸시 발송!
        List<DeviceToken> tokens = deviceTokenRepository.findAllByUserId(userId);
        for (DeviceToken deviceToken : tokens) {
            PushSendRequest request = new PushSendRequest(deviceToken.getToken(), title, body, type, targetId);
            expoPushService.sendMessage(request);
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(Long userId) {
        return notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 알림입니다."));

        if (!notification.getUserId().equals(userId)) {
            throw new IllegalStateException("해당 알림에 대한 권한이 없습니다.");
        }

        notification.markAsRead();
    }

    @Transactional
    public void deleteToken(String token) {
        deviceTokenRepository.deleteByToken(token);
        log.info("기기 토큰이 삭제되었습니다 (로그아웃 처리).");
    }

    @Transactional(readOnly = true)
    public int getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }
}