package com.keepeat.backend.domain.notification.service;

import com.keepeat.backend.domain.notification.dto.PushSendRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpoPushService {

    // Expo 푸시 알림 공식 API 주소
    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";
    private final RestTemplate restTemplate = new RestTemplate();

    public void sendMessage(PushSendRequest request) {
        try {
            // 1. 헤더 설정 (JSON 타입)
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "application/json");

            // 2. 프론트가 필요로 하는 data 주머니 만들기 (라우팅용)
            Map<String, String> dataPayload = new HashMap<>();
            dataPayload.put("type", request.type().name());
            dataPayload.put("targetId", request.targetId() != null ? request.targetId() : "");

            // 3. Expo 규격에 맞는 Body 만들기
            Map<String, Object> body = new HashMap<>();
            body.put("to", request.targetToken()); // ExponentPushToken[...] 형태의 토큰
            body.put("title", request.title());
            body.put("body", request.body());
            body.put("data", dataPayload); // 프론트엔드로 넘어갈 숨은 데이터

            // 4. HTTP 요청 조립 및 발사!
            HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(body, headers);
            String response = restTemplate.postForObject(EXPO_PUSH_URL, httpEntity, String.class);

            log.info("Expo 푸시 알림 전송 완료. 대상: {}, 응답: {}", request.targetToken(), response);

        } catch (Exception e) {
            log.error("Expo 알림 전송 중 에러 발생: {}", e.getMessage());
        }
    }
}