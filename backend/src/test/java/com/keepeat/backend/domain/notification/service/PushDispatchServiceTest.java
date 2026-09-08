package com.keepeat.backend.domain.notification.service;

import com.keepeat.backend.domain.notification.dto.PushSendRequest;
import com.keepeat.backend.domain.notification.entity.DeviceToken;
import com.keepeat.backend.domain.notification.entity.NotificationType;
import com.keepeat.backend.domain.notification.repository.DeviceTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PushDispatchServiceTest {

    @InjectMocks
    private PushDispatchService pushDispatchService;

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    @Mock
    private ExpoPushService expoPushService;

    @Test
    @DisplayName("dispatch는 notificationId를 담은 PushSendRequest를 만든다")
    void dispatch_buildsRequestWithNotificationId() {
        DeviceToken token = mock(DeviceToken.class);
        given(token.getToken()).willReturn("ExpoPushToken[test]");
        given(deviceTokenRepository.findAllByUserId(1L)).willReturn(List.of(token));

        pushDispatchService.dispatch(1L, "title", "body", NotificationType.RECIPE_READY, 42L);

        ArgumentCaptor<PushSendRequest> captor = ArgumentCaptor.forClass(PushSendRequest.class);
        verify(expoPushService).sendMessage(captor.capture());
        assertThat(captor.getValue().notificationId()).isEqualTo(42L);
        assertThat(captor.getValue().type()).isEqualTo(NotificationType.RECIPE_READY);
    }

    @Test
    @DisplayName("푸시 발송 실패가 dispatch 밖으로 전파되지 않는다")
    void dispatch_swallowsPushFailure() {
        DeviceToken token = mock(DeviceToken.class);
        given(token.getToken()).willReturn("ExpoPushToken[test]");
        given(deviceTokenRepository.findAllByUserId(1L)).willReturn(List.of(token));
        willThrow(new RuntimeException("expo down")).given(expoPushService).sendMessage(any());

        assertThatCode(() -> pushDispatchService.dispatch(
                1L, "title", "body", NotificationType.EXPIRY_SOON, 42L))
                .doesNotThrowAnyException();
    }
}
