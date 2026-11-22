package com.keepeat.backend.domain.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.keepeat.backend.domain.config.AiConfig.AiProperties.Feature;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AiConfig의 Gemini Client가 Spring AI 재시도와 합쳐도 전체 HTTP 요청을 MAX_ATTEMPTS(3회) 안으로 제한하고,
 * SDK 예외를 서비스가 구분하는 Transient/NonTransientAiException으로 바꾸는지 로컬 stub 서버로 확인한다 (#67).
 * 자동 설정 기본 Client는 같은 503에 5회 요청하고 RuntimeException을 던졌다.
 */
class GeminiRetryStubServerTest {

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    private volatile int status;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            byte[] body = ("{\"error\": {\"code\": " + status + ", \"message\": \"stub\"}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    @DisplayName("503 → SDK 재시도 포함 전체 3회 요청 후 TransientAiException (AI_API_FAILURE)")
    void serverErrorIsRetriedUpToMaxAttempts() {
        status = 503;

        assertThatThrownBy(() -> recipeClient().prompt().user("hi").call().content())
                .isInstanceOf(TransientAiException.class);
        assertThat(requests.get()).isEqualTo(AiConfig.MAX_ATTEMPTS);
    }

    @Test
    @DisplayName("400 → 재시도 없이 1회 요청 후 NonTransientAiException (AI_API_FAILURE)")
    void clientErrorIsNotRetried() {
        status = 400;

        assertThatThrownBy(() -> recipeClient().prompt().user("hi").call().content())
                .isInstanceOf(NonTransientAiException.class);
        assertThat(requests.get()).isEqualTo(1);
    }

    private ChatClient recipeClient() {
        Client client = AiConfig.geminiClient("test-key",
                HttpOptions.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort()));
        // 자동 설정(SpringAiRetryAutoConfiguration)과 같은 정책. 대기만 줄였다
        RetryTemplate retry = new RetryTemplate(RetryPolicy.builder().maxRetries(2)
                .includes(TransientAiException.class, ResourceAccessException.class)
                .delay(Duration.ofMillis(10)).build());
        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder().genAiClient(client).retryTemplate(retry).build();
        return AiConfig.chatClient(Map.of("googleGenAiChatModel", model), "recipe",
                new Feature("google-genai", "gemini-2.5-pro", 0.2, 100));
    }
}
