package com.keepeat.backend.domain.notification.service;

import com.keepeat.backend.domain.notification.dto.PushSendRequest;
import com.keepeat.backend.domain.notification.entity.NotificationType;
import com.keepeat.backend.domain.notification.repository.DeviceTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ExpoPushServiceTest {

    @InjectMocks
    private ExpoPushService expoPushService;

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    @Test
    @DisplayName("푸시 payload data에는 notificationId가 숫자로 포함되고 targetId는 포함되지 않는다")
    void buildPushBody_containsNumericNotificationId_withoutTargetId() {
        PushSendRequest request = new PushSendRequest(
                "ExpoPushToken[test]", "title", "body", NotificationType.EXPIRY_SOON, 42L);

        Map<String, Object> body = expoPushService.buildPushBody(request);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertThat(data.get("notificationId")).isEqualTo(42L);
        assertThat(data.get("type")).isEqualTo("EXPIRY_SOON");
        assertThat(data).doesNotContainKey("targetId");
    }

    @Test
    @DisplayName("notificationId가 없으면 data에 notificationId 키를 넣지 않는다")
    void buildPushBody_withoutNotificationId_omitsKey() {
        PushSendRequest request = new PushSendRequest(
                "ExpoPushToken[test]", "title", "body", NotificationType.NOTICE, null);

        Map<String, Object> body = expoPushService.buildPushBody(request);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertThat(data).doesNotContainKey("notificationId");
        assertThat(data.get("type")).isEqualTo("NOTICE");
    }
}
