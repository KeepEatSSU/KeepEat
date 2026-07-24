package com.keepeat.backend.domain.notification.service;

import com.keepeat.backend.domain.notification.dto.PushSendRequest;
import com.keepeat.backend.domain.notification.entity.DeviceToken;
import com.keepeat.backend.domain.notification.entity.NotificationType;
import com.keepeat.backend.domain.notification.repository.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushDispatchService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final ExpoPushService expoPushService;

    @Async("pushExecutor")
    public void dispatch(Long userId, String title, String body, NotificationType type, String targetId) {
        List<DeviceToken> tokens = deviceTokenRepository.findAllByUserId(userId);
        for (DeviceToken token : tokens) {
            try {
                expoPushService.sendMessage(new PushSendRequest(token.getToken(), title, body, type, targetId));
            } catch (Exception e) {
                log.warn("푸시 비동기 처리 실패: userId={}, cause={}", userId, e.getClass().getSimpleName());
            }
        }
    }
}
