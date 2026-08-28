package com.keepeat.backend.domain.recipe.service;

import com.keepeat.backend.domain.category.Category;
import com.keepeat.backend.domain.common.enums.RecipeGenerationJobStatus;
import com.keepeat.backend.domain.common.enums.StorageType;
import com.keepeat.backend.domain.common.exception.ErrorCode;
import com.keepeat.backend.domain.common.exception.KeepEatException;
import com.keepeat.backend.domain.ingredient.Ingredient;
import com.keepeat.backend.domain.recipe.entity.RecipeGenerationJob;
import com.keepeat.backend.domain.recipe.entity.RecipeGenerationUsage;
import com.keepeat.backend.domain.recipe.repository.RecipeGenerationJobRepository;
import com.keepeat.backend.domain.recipe.repository.RecipeGenerationUsageRepository;
import com.keepeat.backend.domain.subcategory.SubCategory;
import com.keepeat.backend.domain.useringredient.UserIngredient;
import com.keepeat.backend.domain.useringredient.UserIngredientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class RecipeGenerationRecoveryIntegrationTest {

    private static final Long USER_ID = 424242L;

    @Autowired
    private RecipeAiService recipeAiService;

    @Autowired
    private RecipeGenerationJobRepository jobRepository;

    @Autowired
    private RecipeGenerationUsageRepository usageRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private UserIngredientRepository userIngredientRepository;

    @MockitoBean
    private AsyncRecipeService asyncRecipeService;

    private final LocalDate todayKst = LocalDate.now(ZoneId.of("Asia/Seoul"));

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();
        usageRepository.deleteAll();

        when(userIngredientRepository.findAllByUserIdOrderByExpiryDate(USER_ID)).thenReturn(List.of(
                userIngredient("채소", "감자"),
                userIngredient("채소", "양파"),
                userIngredient("정육/계란", "돼지고기"),
                userIngredient("양념/소스", "간장"),
                userIngredient("양념/소스", "고추장"),
                userIngredient("양념/소스", "소금")
        ));
    }

    @Test
    @DisplayName("AI 실패로 FAILED가 된 job은 같은 날 재생성이 가능하다")
    void failedJobAllowsRetrySameDay() {
        claimJob("failed-attempt", Instant.now());
        markFailed("failed-attempt");

        assertThatCode(() -> recipeAiService.generateRecipes(USER_ID)).doesNotThrowAnyException();

        RecipeGenerationJob job = jobRepository.findByUserId(USER_ID).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(RecipeGenerationJobStatus.PENDING);
        assertThat(job.getAttemptId()).isNotEqualTo("failed-attempt");
        verify(asyncRecipeService).processGeneration(anyLong(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("10분을 초과한 stale PENDING job은 재생성 요청 시 탈환된다")
    void stalePendingJobIsReclaimedOnNewRequest() {
        claimJob("stale-attempt", Instant.now().minus(Duration.ofMinutes(11)));

        assertThatCode(() -> recipeAiService.generateRecipes(USER_ID)).doesNotThrowAnyException();

        RecipeGenerationJob job = jobRepository.findByUserId(USER_ID).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(RecipeGenerationJobStatus.PENDING);
        assertThat(job.getAttemptId()).isNotEqualTo("stale-attempt");
    }

    @Test
    @DisplayName("10분이 지나지 않은 PENDING job은 새 생성 요청을 막는다")
    void freshPendingJobBlocksNewRequest() {
        claimJob("fresh-attempt", Instant.now());

        assertThatThrownBy(() -> recipeAiService.generateRecipes(USER_ID))
                .isInstanceOfSatisfying(KeepEatException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RECIPE_GENERATING));

        RecipeGenerationJob job = jobRepository.findByUserId(USER_ID).orElseThrow();
        assertThat(job.getAttemptId()).isEqualTo("fresh-attempt");
        verify(asyncRecipeService, never()).processGeneration(anyLong(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("started_at이 없는 과거 스키마의 PENDING job도 탈환된다")
    void legacyPendingJobWithNullStartedAtIsReclaimed() {
        jobRepository.save(RecipeGenerationJob.create(USER_ID));

        assertThatCode(() -> recipeAiService.generateRecipes(USER_ID)).doesNotThrowAnyException();

        RecipeGenerationJob job = jobRepository.findByUserId(USER_ID).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(RecipeGenerationJobStatus.PENDING);
        assertThat(job.getAttemptId()).isNotNull();
        assertThat(job.getStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("그 날 성공 이력이 있으면 하루 1회 제한에 걸리고, PENDING 클레임은 롤백된다")
    void dailyLimitBlocksAfterSuccessfulGeneration() {
        usageRepository.save(new RecipeGenerationUsage(USER_ID, todayKst));

        assertThatThrownBy(() -> recipeAiService.generateRecipes(USER_ID))
                .isInstanceOfSatisfying(KeepEatException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RECIPE_DAILY_LIMIT_EXCEEDED));

        assertThat(jobRepository.findByUserId(USER_ID)).isEmpty();
        verify(asyncRecipeService, never()).processGeneration(anyLong(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("비동기 제출이 거부되면 job은 PENDING으로 남지 않고 즉시 FAILED가 되어 재시도할 수 있다")
    void rejectedAsyncSubmissionMarksJobFailedImmediately() {
        doThrow(new TaskRejectedException("executor queue full"))
                .when(asyncRecipeService).processGeneration(anyLong(), anyString(), any(), anyString());

        assertThatThrownBy(() -> recipeAiService.generateRecipes(USER_ID))
                .isInstanceOfSatisfying(KeepEatException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AI_API_FAILURE));

        RecipeGenerationJob job = jobRepository.findByUserId(USER_ID).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(RecipeGenerationJobStatus.FAILED);

        // stale 타임아웃을 기다리지 않고 즉시 재생성이 가능해야 한다
        doNothing().when(asyncRecipeService).processGeneration(anyLong(), anyString(), any(), anyString());
        assertThatCode(() -> recipeAiService.generateRecipes(USER_ID)).doesNotThrowAnyException();
        assertThat(jobRepository.findByUserId(USER_ID).orElseThrow().getStatus())
                .isEqualTo(RecipeGenerationJobStatus.PENDING);
    }

    @Test
    @DisplayName("FAILED job을 폴링하면 저장된 오류 코드가 그대로 전달된다")
    void pollingFailedJobReplaysStoredErrorCode() {
        claimJob("failed-attempt", Instant.now());
        markFailed("failed-attempt");

        assertThatThrownBy(() -> recipeAiService.getGenerationResult(USER_ID))
                .isInstanceOfSatisfying(KeepEatException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AI_API_FAILURE));
    }

    private void claimJob(String attemptId, Instant startedAt) {
        transactionTemplate.executeWithoutResult(status ->
                jobRepository.tryStartGeneration(USER_ID, todayKst, attemptId, startedAt,
                        startedAt.minus(RecipeAiService.STALE_PENDING_TIMEOUT)));
    }

    private void markFailed(String attemptId) {
        transactionTemplate.executeWithoutResult(status ->
                jobRepository.failIfCurrentAttempt(USER_ID, attemptId,
                        ErrorCode.AI_API_FAILURE.name(), "AI 호출 실패", todayKst));
    }

    private static UserIngredient userIngredient(String categoryName, String name) {
        Category category = Category.builder().name(categoryName).build();
        SubCategory subCategory = SubCategory.builder().category(category).name(categoryName).build();
        Ingredient ingredient = Ingredient.builder().subCategory(subCategory).name(name).build();
        return UserIngredient.builder()
                .userId(USER_ID)
                .ingredient(ingredient)
                .storageType(StorageType.냉장)
                .purchaseDate(LocalDate.now())
                .expiryDate(LocalDate.now().plusDays(3))
                .build();
    }
}
