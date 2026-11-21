package com.keepeat.backend.domain.config;

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
        if ("openai".equals(config.provider())) {
            chatModel = translatingOpenAiErrors(chatModel);
        }
        return ChatClient.builder(chatModel)
                .defaultOptions(ChatOptions.builder()
                        .model(config.model())
                        .temperature(config.temperature())
                        .maxTokens(config.maxTokens()))
                // .entity(...) 호출 시 응답 스키마를 공급자 JSON 모드로 넘긴다
                // (Gemini: responseMimeType=application/json + responseJsonSchema, OpenAI: response_format json_schema)
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .build();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Spring AI 2.x의 OpenAiChatModel은 OpenAI SDK 예외를 그대로 던진다. 서비스는 공급자와 무관하게
     * Transient/NonTransientAiException으로 API 실패를 구분하므로 여기서 바꾼다 (1.x 기본 분류와 같다: 4xx → NonTransient, 그 외 → Transient).
     * ponytail: call만 감싼다. 스트리밍을 쓰게 되면 stream도 감싼다.
     */
    static ChatModel translatingOpenAiErrors(ChatModel delegate) {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                try {
                    return delegate.call(prompt);
                } catch (OpenAIServiceException e) {
                    if (e.statusCode() >= 400 && e.statusCode() < 500) {
                        throw new NonTransientAiException(e.getMessage(), e);
                    }
                    throw new TransientAiException(e.getMessage(), e);
                } catch (OpenAIIoException e) {
                    throw new TransientAiException(e.getMessage(), e);
                }
            }

            @Override
            public ChatOptions getOptions() {
                return delegate.getOptions();
            }
        };
    }
}
