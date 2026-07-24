package com.keepeat.backend.domain.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepeat.backend.domain.notification.dto.PushSendRequest;
import com.keepeat.backend.domain.notification.repository.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpoPushService {

    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";
    private static final String EXPO_RECEIPTS_URL = "https://exp.host/--/api/v2/push/getReceipts";
    private static final int MAX_SEND_ATTEMPTS = 3;
    private static final int MAX_RECEIPT_ATTEMPTS = 10;

    private final DeviceTokenRepository deviceTokenRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, PendingReceipt> pendingReceipts = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate = createRestTemplate();

    public void sendMessage(PushSendRequest request) {
        for (int attempt = 1; attempt <= MAX_SEND_ATTEMPTS; attempt++) {
            try {
                JsonNode response = post(EXPO_PUSH_URL, buildPushBody(request));
                handleTicketResponse(response.path("data"), request);
                return;
            } catch (RestClientException | RetryablePushException e) {
                if (attempt == MAX_SEND_ATTEMPTS) {
                    log.warn("Expo 푸시 요청 실패: token={}, attempts={}", fingerprint(request.targetToken()), attempt);
                    return;
                }
                backoff(attempt);
            } catch (Exception e) {
                log.warn("Expo 푸시 응답 처리 실패: token={}, cause={}",
                        fingerprint(request.targetToken()), e.getClass().getSimpleName());
                return;
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.push.receipt-check-delay-ms:60000}")
    public void checkReceipts() {
        Instant now = Instant.now();
        List<String> ids = pendingReceipts.entrySet().stream()
                .filter(entry -> entry.getValue().nextCheckAt().isBefore(now))
                .limit(300)
                .map(Map.Entry::getKey)
                .toList();
        if (ids.isEmpty()) {
            return;
        }

        try {
            JsonNode data = post(EXPO_RECEIPTS_URL, Map.of("ids", ids)).path("data");
            for (String id : ids) {
                PendingReceipt pending = pendingReceipts.get(id);
                if (pending == null) {
                    continue;
                }
                JsonNode receipt = data.path(id);
                if (receipt.isMissingNode()) {
                    rescheduleOrDrop(id, pending, now);
                    continue;
                }

                String status = receipt.path("status").asText();
                if ("ok".equals(status)) {
                    pendingReceipts.remove(id);
                } else if ("error".equals(status)) {
                    String error = handleDeliveryError(pending.request().targetToken(), receipt);
                    pendingReceipts.remove(id);
                    if ("MessageRateExceeded".equals(error)) {
                        sendMessage(pending.request());
                    }
                } else {
                    rescheduleOrDrop(id, pending, now);
                }
            }
        } catch (Exception e) {
            log.warn("Expo receipt 조회 실패: count={}, cause={}", ids.size(), e.getClass().getSimpleName());
            ids.forEach(id -> {
                PendingReceipt pending = pendingReceipts.get(id);
                if (pending != null) {
                    rescheduleOrDrop(id, pending, now);
                }
            });
        }
    }

    private void handleTicketResponse(JsonNode ticket, PushSendRequest request) {
        if (ticket.isArray() && !ticket.isEmpty()) {
            ticket = ticket.get(0);
        }
        String status = ticket.path("status").asText();
        if ("ok".equals(status)) {
            String ticketId = ticket.path("id").asText(null);
            if (ticketId != null) {
                pendingReceipts.put(ticketId, new PendingReceipt(request, 0, Instant.now().plusSeconds(30)));
            }
            return;
        }
        if ("error".equals(status)) {
            String error = handleDeliveryError(request.targetToken(), ticket);
            if ("MessageRateExceeded".equals(error)) {
                throw new RetryablePushException();
            }
            return;
        }
        log.warn("Expo 푸시 응답 형식이 올바르지 않음: token={}", fingerprint(request.targetToken()));
    }

    private String handleDeliveryError(String token, JsonNode response) {
        String error = response.path("details").path("error").asText("unknown");
        if ("DeviceNotRegistered".equals(error)) {
            deviceTokenRepository.deleteByToken(token);
            log.info("만료된 Expo 토큰 제거: token={}", fingerprint(token));
            return error;
        }
        log.warn("Expo 푸시 전달 실패: token={}, error={}", fingerprint(token), error);
        return error;
    }

    private void rescheduleOrDrop(String id, PendingReceipt pending, Instant now) {
        int attempts = pending.attempts() + 1;
        if (attempts >= MAX_RECEIPT_ATTEMPTS || pending.nextCheckAt().isBefore(now.minus(1, ChronoUnit.DAYS))) {
            pendingReceipts.remove(id);
            log.warn("Expo receipt 확인 만료: token={}", fingerprint(pending.request().targetToken()));
            return;
        }
        pendingReceipts.put(id, new PendingReceipt(pending.request(), attempts, now.plusSeconds(60)));
    }

    private Map<String, Object> buildPushBody(PushSendRequest request) {
        Map<String, String> data = new HashMap<>();
        data.put("type", request.type().name());
        data.put("targetId", request.targetId() == null ? "" : request.targetId());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("to", request.targetToken());
        body.put("title", request.title());
        body.put("body", request.body());
        body.put("data", data);
        return body;
    }

    private JsonNode post(String url, Map<String, ?> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        String response = restTemplate.postForObject(url, new HttpEntity<>(body, headers), String.class);
        try {
            return objectMapper.readTree(response == null ? "{}" : response);
        } catch (Exception e) {
            throw new IllegalStateException("Expo 응답 JSON 파싱에 실패했습니다.", e);
        }
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(200L * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String fingerprint(String token) {
        if (token == null) {
            return "null";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest, 0, 6);
        } catch (Exception e) {
            return "unavailable";
        }
    }

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(5_000);
        return new RestTemplate(factory);
    }

    private record PendingReceipt(PushSendRequest request, int attempts, Instant nextCheckAt) {
    }

    private static final class RetryablePushException extends RuntimeException {
    }
}
