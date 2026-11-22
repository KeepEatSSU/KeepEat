package com.keepeat.backend.domain.config;

import com.keepeat.backend.domain.config.AiConfig.AiProperties.Feature;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeTypeUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spring AI starter와 같은 방식으로 만든 OpenAI ChatModel이 AiConfig의 ChatClient를 거쳐 Boot 4 환경에서 요청을 직렬화하고 응답을 읽는지 로컬 stub 서버로 확인한다.
 * 실제 OpenAI는 호출하지 않는다.
 */
class OpenAiChatModelStubServerTest {

    private HttpServer server;
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicInteger requests = new AtomicInteger();
    private volatile int status = 200;
    private volatile String responseBody;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requests.incrementAndGet();
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
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
    @DisplayName("OCR 기본 설정(openai/gpt-4o) → 모델·max_tokens·이미지 data URL을 보내고 응답 본문을 읽는다")
    void sendsImageRequestAndReadsContent() {
        responseBody = """
                {"id": "chatcmpl-1", "object": "chat.completion", "created": 1, "model": "gpt-4o",
                 "choices": [{"index": 0, "finish_reason": "stop",
                   "message": {"role": "assistant", "content": "{\\"ingredients\\": []}"}}],
                 "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2}}
                """;
        ChatClient client = AiConfig.chatClient(Map.of("openAiChatModel", openAiChatModel()), "ocr",
                new Feature("openai", "gpt-4o", null, 1024));

        String content = client.prompt()
                .system("system prompt")
                .user(u -> u.text("format").media(MimeTypeUtils.IMAGE_JPEG, new ByteArrayResource(new byte[]{1, 2, 3})))
                .call()
                .content();

        assertThat(content).isEqualTo("{\"ingredients\": []}");
        assertThat(authorization.get()).isEqualTo("Bearer test-key");
        assertThat(requestBody.get())
                .contains("\"model\":\"gpt-4o\"")
                .contains("\"max_tokens\":1024")
                .contains("system prompt")
                .contains("data:image/jpeg;base64,AQID");
    }

    @Test
    @DisplayName("4xx 응답 → NonTransientAiException (OCR_API_FAILURE / AI_API_FAILURE로 매핑되는 예외)")
    void clientErrorBecomesNonTransientAiException() {
        status = 401;
        responseBody = "{\"error\": {\"message\": \"Incorrect API key provided\"}}";
        ChatClient client = AiConfig.chatClient(Map.of("openAiChatModel", openAiChatModel()), "recipe",
                new Feature("openai", "gpt-4o", 0.2, 8000));

        assertThatThrownBy(() -> client.prompt().user("hi").call().content())
                .isInstanceOf(NonTransientAiException.class);
    }

    @Test
    @DisplayName("503 → 운영 설정(max-retries 2)으로 전체 3회 요청 후 TransientAiException (#67)")
    void serverErrorIsRetriedUpToMaxAttempts() {
        status = 503;
        responseBody = "{\"error\": {\"message\": \"overloaded\"}}";
        ChatClient client = AiConfig.chatClient(Map.of("openAiChatModel", openAiChatModel(AiConfig.MAX_ATTEMPTS - 1)), "recipe",
                new Feature("openai", "gpt-4o", 0.2, 8000));

        assertThatThrownBy(() -> client.prompt().user("hi").call().content())
                .isInstanceOf(TransientAiException.class);
        assertThat(requests.get()).isEqualTo(AiConfig.MAX_ATTEMPTS);
    }

    private ChatModel openAiChatModel() {
        return openAiChatModel(0);
    }

    private ChatModel openAiChatModel(int maxRetries) {
        return OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder()
                        .apiKey("test-key")
                        .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                        .maxRetries(maxRetries)
                        .build())
                .build();
    }
}
