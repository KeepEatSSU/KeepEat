package com.keepeat.backend.domain.config;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * 기능별(OCR, 레시피 추천, 식재료 분류) ChatClient를 만든다.
 * 공급자와 모델은 app.ai.&lt;기능&gt;.provider|model 설정값으로 고르고, 공급자별 ChatModel 빈은 Spring AI starter가 만든다.
 */
@Configuration
@EnableConfigurationProperties(AiConfig.AiProperties.class)
public class AiConfig {

    /** provider 설정값 → Spring AI starter가 등록하는 ChatModel 빈 이름 */
    static final Map<String, String> CHAT_MODEL_BEANS = Map.of(
            "openai", "openAiChatModel",
            "google-genai", "googleGenAiChatModel"
    );

    @ConfigurationProperties("app.ai")
    public record AiProperties(Feature ocr, Feature recipe, Feature ingredient) {
        public record Feature(String provider, String model, Double temperature, Integer maxTokens) {}
    }

    /** 전체 HTTP 시도 횟수(최초 1회 + 재시도 2회). OpenAI는 application.yml의 spring.ai.openai.max-retries(2)로 같은 값을 쓴다 */
    static final int MAX_ATTEMPTS = 3;

    /**
     * Spring AI 자동 설정의 googleGenAiClient를 대체한다(@ConditionalOnMissingBean). 자동 설정은 HttpRetryOptions 없이 만들어
     * SDK 기본값(5회, 최대 60초 대기)으로 재시도한다. GoogleGenAiChatModel은 SDK 예외를 RuntimeException으로 감싸 던져
     * Spring AI RetryTemplate(TransientAiException만 재시도)은 동작하지 않으므로, 재시도는 SDK 한 곳에서만 제한한다 (#67).
     * ponytail: API 키 방식만 지원한다. Vertex AI를 쓰게 되면 project/location 설정을 추가한다.
     */
    @Bean
    public Client googleGenAiClient(@Value("${spring.ai.google.genai.api-key}") String apiKey) {
        return geminiClient(apiKey, HttpOptions.builder());
    }

    static Client geminiClient(String apiKey, HttpOptions.Builder httpOptions) {
        return Client.builder()
                .apiKey(apiKey)
                .httpOptions(httpOptions.retryOptions(HttpRetryOptions.builder().attempts(MAX_ATTEMPTS)).build())
                .build();
    }

    @Bean
    public ChatClient ocrChatClient(Map<String, ChatModel> chatModels, AiProperties properties) {
        return chatClient(chatModels, "ocr", properties.ocr());
    }

    @Bean
    public ChatClient recipeChatClient(Map<String, ChatModel> chatModels, AiProperties properties) {
        return chatClient(chatModels, "recipe", properties.recipe());
    }

    @Bean
    public ChatClient ingredientChatClient(Map<String, ChatModel> chatModels, AiProperties properties) {
        return chatClient(chatModels, "ingredient", properties.ingredient());
    }

    static ChatClient chatClient(Map<String, ChatModel> chatModels, String feature, AiProperties.Feature config) {
        if (config == null || isBlank(config.provider()) || isBlank(config.model())) {
            throw new IllegalStateException("app.ai." + feature + ".provider와 model을 설정해야 합니다.");
        }
        String beanName = CHAT_MODEL_BEANS.get(config.provider());
        ChatModel chatModel = beanName == null ? null : chatModels.get(beanName);
        if (chatModel == null) {
            throw new IllegalStateException("app.ai." + feature + ".provider '" + config.provider()
                    + "'에 해당하는 ChatModel이 없습니다. 사용 가능: " + CHAT_MODEL_BEANS.keySet());
        }
        return ChatClient.builder(translatingAiErrors(chatModel))
                .defaultOptions(ChatOptions.builder()
                        .model(config.model())
                        .temperature(config.temperature())
                        .maxTokens(config.maxTokens()))
                // .entity(...) 호출 시 응답 스키마를 공급자 JSON 모드로 넘긴다
                // (Gemini: responseMimeType=application/json + responseJsonSchema, OpenAI: response_format json_schema)
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .build();
    }

    static RuntimeException translate(RuntimeException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof OpenAIServiceException ex) {
                return byStatus(ex.statusCode(), e);
            }
            if (t instanceof ApiException ex) {
                return byStatus(ex.code(), e);
            }
            if (t instanceof OpenAIIoException || t instanceof GenAiIOException) {
                return new TransientAiException(e.getMessage(), e);
            }
        }
        return e;
    }

    private static RuntimeException byStatus(int status, RuntimeException e) {
        return status >= 400 && status < 500
                ? new NonTransientAiException(e.getMessage(), e)
                : new TransientAiException(e.getMessage(), e);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Spring AI 2.x의 OpenAiChatModel은 OpenAI SDK 예외를 그대로, GoogleGenAiChatModel은 Gemini SDK 예외를 RuntimeException으로 감싸 던진다.
     * 서비스는 공급자와 무관하게 Transient/NonTransientAiException으로 API 실패를 구분하므로 여기서 바꾼다
     * (1.x 기본 분류와 같다: 4xx → NonTransient, 그 외 → Transient). 재시도가 끝난 뒤 바깥에서 바꾸므로 Spring 재시도를 다시 일으키지 않는다.
     * ponytail: call만 감싼다. 스트리밍을 쓰게 되면 stream도 감싼다.
     */
    static ChatModel translatingAiErrors(ChatModel delegate) {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                try {
                    return delegate.call(prompt);
                } catch (RuntimeException e) {
                    throw translate(e);
                }
            }

            @Override
            public ChatOptions getOptions() {
                return delegate.getOptions();
            }
        };
    }
}
