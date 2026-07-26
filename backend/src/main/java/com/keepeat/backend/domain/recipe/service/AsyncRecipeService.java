package com.keepeat.backend.domain.recipe.service;

import com.keepeat.backend.domain.common.exception.KeepEatException;
import org.slf4j.MDC;
import tools.jackson.databind.ObjectMapper;
import com.keepeat.backend.domain.common.exception.ErrorCode;
import com.keepeat.backend.domain.notification.entity.NotificationType;
import com.keepeat.backend.domain.notification.service.NotificationService;
import com.keepeat.backend.domain.recipe.dto.GeneratedRecipesResponseDto;
import com.keepeat.backend.domain.recipe.entity.RecipeGenerationJob;
import com.keepeat.backend.domain.recipe.entity.RecipeGenerationUsage;
import com.keepeat.backend.domain.recipe.repository.RecipeGenerationJobRepository;
import com.keepeat.backend.domain.recipe.repository.RecipeGenerationUsageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;

@Service
@Slf4j
public class AsyncRecipeService {

    private final ChatClient chatClient;
    private final RecipeGenerationJobRepository recipeGenerationJobRepository;
    private final RecipeGenerationUsageRepository recipeGenerationUsageRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public AsyncRecipeService(
            ChatClient.Builder chatClientBuilder,
            RecipeGenerationJobRepository recipeGenerationJobRepository,
            RecipeGenerationUsageRepository recipeGenerationUsageRepository,
            NotificationService notificationService,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate
    ) {
        this.recipeGenerationJobRepository = recipeGenerationJobRepository;
        this.recipeGenerationUsageRepository = recipeGenerationUsageRepository;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;

        this.chatClient = chatClientBuilder
                .defaultOptions(GoogleGenAiChatOptions.builder()
                        .temperature(0.2)
                        .maxOutputTokens(8000)
                        .model("gemini-2.5-pro")
                        .responseMimeType("application/json")
                        .build())
                .defaultSystem("""
                        Role: 제로 웨이스트 전문 셰프
                        당신은 냉장고 재료 활용을 극대화하여 버려지는 식재료를 최소화하고, 미식적 완성도가 높은 '진짜 요리'를 설계하는 1인분 요리 전문가입니다.
                        
                        Mission
                        제공된 보유 식재료와 조미료 리스트를 바탕으로 총 5개의 레시피를 제안하세요.
                        
                        1. 레시피 구성 및 우선순위
                        레시피 1~3 (Zero Waste): 오직 보유 재료와 조미료만 사용.절대로, 단 1개의 새로운 식재료나 조미료도 추가해서는 안 됩니다.
                        레시피 4~5 (Level Up): 보유 재료를 베이스로 하되, 식재료 또는 조미료 1~3개를 추가 구매하여 요리의 질을 높임.
                        최우선 순위: `days_left`가 짧은 재료를 우선적으로 활용하되, 자연스러운 조합이 아닌 경우 다른 레시피에 나누어 배치하세요.
                        핵심 재료 체크: 요리 제목에 들어가는 주재료(밥, 면, 고기 등)가 없을 경우 1~3번에서 제외하거나, 보유한 재료로 대체 가능한 제목으로 수정할 것.
                        실존 요리 원칙: 모든 레시피는 대중적으로 검증된 레시피여야 한다. 검색 시 관련된 비슷한 레시피가 있을 정도의 레시피여야 한다.
                        
                        
                        2. 조리 디테일 (Quality Control)
                        음식의 완성도: 단순 혼합이 아닌, 식재료의 특성을 살린 조리법을 적용한 실제 레시피여야 함.
                        상세 가이드: `instructions`는 반드시 5단계 이상으로 구성하며, 각 단계의 시작은 항상 숫자와 마침표로 시작하세요 (예: "1. 팬을 달구고...", "2. 고기를 볶아줍니다.").
                        필수 정보: 단계별로 정확한 불 세기(강/중/약불), 조리 시간(분/초), 투입하는 양념의 양을 반드시 명시할 것.
                        계량 및 속성 필수: requiredIngredients 배열은 재료명(name)과 계량 수치(amount)만을 속성으로 갖는 객체 리스트로 구성하세요. (예: [{"name": "생삼겹살", "amount": "150g"}]) 추가로 구매해야 하는 식재료가 포함되어도 절대 "(추가 구매)"와 같은 별도의 표시나 마킹을 하지 말고 평범한 재료명만 적으세요.
                        다양성: 5개 레시피의 `cookingMethod`는 중복되지 않게 골고루 배분할 것.
                        추가 정보: 각 레시피의 예상 칼로리를 계산하여 `calories` 필드에 숫자(kcal 단위)로 제공하세요.
                        """)
                .build();
    }

    @Async("recipeAiExecutor")
    public void processGeneration(Long userId, String userPrompt, LocalDate usageDate) {
        MDC.put("userId", String.valueOf(userId));
        try{
            GeneratedRecipesResponseDto responseFromAi;
            try {
                responseFromAi = chatClient.prompt()
                        .user(userPrompt)
                        .call()
                        .entity(GeneratedRecipesResponseDto.class);
            } catch (TransientAiException | NonTransientAiException e) {
                log.error("[{}] AI 호출 실패", ErrorCode.AI_API_FAILURE.name(), e);
                updateFailed(userId, ErrorCode.AI_API_FAILURE, e.getMessage());
                return;
            } catch (Exception e) {
                log.error("[{}] 레시피 생성 처리 실패", ErrorCode.AI_RESPONSE_PARSE_FAILURE.name(), e);
                updateFailed(userId, ErrorCode.AI_RESPONSE_PARSE_FAILURE, e.getMessage());
                return;
            }


            if (responseFromAi == null || responseFromAi.recipes() == null || responseFromAi.recipes().isEmpty()) {
                log.error("[{}] AI 응답이 비어있음", ErrorCode.AI_RESPONSE_PARSE_FAILURE.name());
                updateFailed(userId, ErrorCode.AI_RESPONSE_PARSE_FAILURE, "AI 응답이 비어있음");
                return;
            }


            try {
                String resultJson = objectMapper.writeValueAsString(responseFromAi);
                transactionTemplate.executeWithoutResult(status -> {
                    RecipeGenerationJob job = recipeGenerationJobRepository.findByUserId(userId)
                            .orElseThrow(() -> new KeepEatException(ErrorCode.RECIPE_JOB_NOT_FOUND));

                    job.done(resultJson);
                    recipeGenerationUsageRepository.save(
                            new RecipeGenerationUsage(userId, usageDate)
                    );
                });
            } catch (Exception e) {
                log.error("[{}] 결과 직렬화/저장 실패", ErrorCode.AI_RESPONSE_PARSE_FAILURE.name(), e);
                updateFailed(userId, ErrorCode.AI_RESPONSE_PARSE_FAILURE, e.getMessage());
                return;
            }

            notificationService.sendNotification(
                    userId,
                    "🍳 AI 레시피 생성 완료!",
                    "가지고 계신 식재료로 맛있는 요리를 만들어 보세요.",
                    NotificationType.RECIPE_READY,
                    null
            );
        } finally {
            MDC.clear();
        }
    }

    private void updateFailed(Long userId, ErrorCode code, String message) {
        transactionTemplate.executeWithoutResult(status ->
                recipeGenerationJobRepository.findByUserId(userId)
                        .ifPresent(job -> job.failed(code, message))
        );
    }
}
