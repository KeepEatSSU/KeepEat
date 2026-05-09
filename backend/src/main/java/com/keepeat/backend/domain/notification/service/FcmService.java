package com.keepeat.backend.domain.notification.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.keepeat.backend.domain.notification.dto.PushSendRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class FcmService {

    public void sendMessage(PushSendRequest request) {
        try {
            // 1. 구글 FCM 규격에 맞게 메시지 조립
            Message message = Message.builder()
                    .setToken(request.targetToken()) // 목적지 기기 주소
                    .setNotification(com.google.firebase.messaging.Notification.builder()
                            .setTitle(request.title()) // 화면에 보일 제목
                            .setBody(request.body())   // 화면에 보일 내용
                            .build())
                    // 프론트엔드가 화면 이동(라우팅)할 때 쓸 'data 주머니' 세팅
                    .putData("type", request.type().name())
                    .putData("targetId", request.targetId() != null ? request.targetId() : "")
                    .build();

            // 2. 파이어베이스로 비동기(Async) 전송! (우리 서버 안 멈추게)
            FirebaseMessaging.getInstance().sendAsync(message);
            log.info("FCM 알림 전송 요청 완료: 토큰 {}", request.targetToken());

        } catch (Exception e) {
            log.error("FCM 알림 전송 중 에러 발생: {}", e.getMessage());

            // 만약 에러 메시지에 'NOT_FOUND'나 'UNREGISTERED'가 포함되어 있다면 유효하지 않은 토큰임
            if (e.getMessage().contains("NOT_FOUND") || e.getMessage().contains("UNREGISTERED")) {
                // 이 부분을 NotificationService를 통해 삭제하도록 처리
                // 간단하게는 이벤트를 발행하거나, 직접 레포지토리를 주입받아 삭제할 수 있습니다.
            }
        }
    }
}