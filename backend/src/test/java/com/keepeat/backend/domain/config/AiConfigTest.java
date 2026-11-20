package com.keepeat.backend.domain.config;

import com.keepeat.backend.domain.config.AiConfig.AiProperties;
import com.keepeat.backend.domain.config.AiConfig.AiProperties.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiConfigTest {

    private final ChatModel openAi = mockModel();
    private final ChatModel googleGenAi = mockModel();
    private final Map<String, ChatModel> chatModels = Map.of(
            "openAiChatModel", openAi,
            "googleGenAiChatModel", googleGenAi
    );

    @Test
    @DisplayName("provider 설정값에 맞는 ChatModel로 호출하고, model·temperature·maxTokens를 공통 ChatOptions로 넘긴다")
    void selectsChatModelByProviderAndAppliesOptions() {
        ChatClient client = AiConfig.chatClient(chatModels, "recipe",
                new Feature("openai", "gpt-4.1", 0.2, 8000));

        client.prompt().user("hi").call().content();

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(openAi).call(captor.capture());
        verify(googleGenAi, never()).call(any(Prompt.class));
        ChatOptions options = (ChatOptions) captor.getValue().getOptions();
        assertThat(options.getModel()).isEqualTo("gpt-4.1");
        assertThat(options.getTemperature()).isEqualTo(0.2);
        assertThat(options.getMaxTokens()).isEqualTo(8000);
    }

    @Test
    @DisplayName("같은 코드로 공급자만 바꾸면 다른 ChatModel이 호출된다")
    void switchingProviderUsesOtherChatModel() {
        ChatClient client = AiConfig.chatClient(chatModels, "ocr",
                new Feature("google-genai", "gemini-2.5-flash", null, 1024));

        client.prompt().user("hi").call().content();

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(googleGenAi).call(captor.capture());
        verify(openAi, never()).call(any(Prompt.class));
        assertThat(((ChatOptions) captor.getValue().getOptions()).getModel()).isEqualTo("gemini-2.5-flash");
    }

    @Test
    @DisplayName("알 수 없는 provider면 기동 시점에 실패한다")
    void unknownProviderFailsFast() {
        assertThatThrownBy(() -> AiConfig.chatClient(chatModels, "ocr", new Feature("anthropic", "x", null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.ai.ocr.provider");
    }

    @Test
    @DisplayName("provider나 model이 비어 있으면 기동 시점에 실패한다")
    void missingSettingFailsFast() {
        assertThatThrownBy(() -> AiConfig.chatClient(chatModels, "recipe", new Feature("openai", null, null, null)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> AiConfig.chatClient(chatModels, "recipe", null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("환경변수가 없으면 기본값은 기존 동작과 같다 (OCR=openai/gpt-4o, 레시피·식재료=google-genai)")
    void defaultsMatchPreviousBehavior() throws IOException {
        AiProperties properties = bindApplicationYml(Map.of());

        assertThat(properties.ocr()).isEqualTo(new Feature("openai", "gpt-4o", null, 1024));
        assertThat(properties.recipe()).isEqualTo(new Feature("google-genai", "gemini-2.5-pro", 0.2, 8000));
        assertThat(properties.ingredient())
                .isEqualTo(new Feature("google-genai", "gemini-flash-lite-latest", 0.1, 5000));
    }

    @Test
    @DisplayName("AI_*_PROVIDER / AI_*_MODEL 환경변수로 기능별 공급자와 모델을 바꾼다")
    void envVarsOverrideProviderAndModel() throws IOException {
        AiProperties properties = bindApplicationYml(Map.of(
                "AI_OCR_PROVIDER", "google-genai",
                "AI_OCR_MODEL", "gemini-2.5-flash",
                "AI_RECIPE_PROVIDER", "openai",
                "AI_RECIPE_MODEL", "gpt-4.1",
                "AI_INGREDIENT_MODEL", "gemini-2.5-flash-lite"
        ));

        assertThat(properties.ocr().provider()).isEqualTo("google-genai");
        assertThat(properties.ocr().model()).isEqualTo("gemini-2.5-flash");
        assertThat(properties.recipe().provider()).isEqualTo("openai");
        assertThat(properties.recipe().model()).isEqualTo("gpt-4.1");
        assertThat(properties.ingredient().provider()).isEqualTo("google-genai");
        assertThat(properties.ingredient().model()).isEqualTo("gemini-2.5-flash-lite");
    }

    private static AiProperties bindApplicationYml(Map<String, Object> env) throws IOException {
        List<PropertySource<?>> yaml = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        MutablePropertySources sources = new MutablePropertySources();
        sources.addFirst(new MapPropertySource("env", env));
        yaml.forEach(sources::addLast);
        Binder binder = new Binder(ConfigurationPropertySources.from(sources),
                new PropertySourcesPlaceholdersResolver(sources));
        return binder.bind("app.ai", AiProperties.class).get();
    }

    private static ChatModel mockModel() {
        ChatModel model = mock(ChatModel.class);
        // Spring AI 2.x ChatClient는 호출마다 chatModel.getOptions()를 복사한다
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        when(model.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("ok")))));
        return model;
    }
}
