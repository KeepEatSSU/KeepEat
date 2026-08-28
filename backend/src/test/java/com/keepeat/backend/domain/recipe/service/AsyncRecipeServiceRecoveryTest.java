package com.keepeat.backend.domain.recipe.service;

import com.keepeat.backend.domain.common.enums.RecipeGenerationJobStatus;
import com.keepeat.backend.domain.common.exception.ErrorCode;
import com.keepeat.backend.domain.notification.service.NotificationService;
import com.keepeat.backend.domain.recipe.dto.GeneratedRecipeDto;
import com.keepeat.backend.domain.recipe.dto.GeneratedRecipesResponseDto;
import com.keepeat.backend.domain.recipe.entity.RecipeGenerationJob;
import com.keepeat.backend.domain.recipe.repository.RecipeGenerationJobRepository;
import com.keepeat.backend.domain.recipe.repository.RecipeGenerationUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class AsyncRecipeServiceRecoveryTest {

    private static final Long USER_ID = 525252L;
    private static final String ATTEMPT_A = "attempt-a";
    private static final String ATTEMPT_B = "attempt-b";

    @Autowired
    private RecipeGenerationJobRepository jobRepository;

    @Autowired
    private RecipeGenerationUsageRepository usageRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private ChatClient chatClient;
    private NotificationService notificationService;
    private AsyncRecipeService service;

    private final LocalDate todayKst = LocalDate.now(ZoneId.of("Asia/Seoul"));

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();
        usageRepository.deleteAll();

        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.defaultOptions(any())).thenReturn(builder);
        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);

        notificationService = mock(NotificationService.class);
        service = new AsyncRecipeService(builder, jobRepository, usageRepository,
                notificationService, objectMapper, transactionTemplate);
    }

    @Test
    @DisplayName("AI 호출 실패 시 job은 FAILED가 되고 사용량은 소진되지 않아 같은 날 재클레임이 가능하다")
    void aiFailureMarksJobFailedAndKeepsQuota() {
        claimJob(ATTEMPT_A, Instant.now());
        when(chatClient.prompt().user(anyString()).call().entity(GeneratedRecipesResponseDto.class))
                .thenThrow(new TransientAiException("quota exceeded"));

        service.processGeneration(USER_ID, "prompt", todayKst, ATTEMPT_A);

        RecipeGenerationJob job = jobRepository.findByUserId(USER_ID).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(RecipeGenerationJobStatus.FAILED);
        assertThat(job.getErrorCode()).isEqualTo(ErrorCode.AI_API_FAILURE);
        assertThat(usageRepository.count()).isZero();

        // FAILED 상태이므로 같은 날 새 시도가 job을 다시 클레임할 수 있다
        Integer reclaimed = transactionTemplate.execute(status ->
                jobRepository.tryStartGeneration(USER_ID, todayKst, ATTEMPT_B, Instant.now(),
                        Instant.now().minus(Duration.ofMinutes(10))));
        assertThat(reclaimed).isEqualTo(1);
    }

    @Test
    @DisplayName("정상 완료 시 job DONE + 사용량 1회가 같이 기록되고 알림이 발송된다")
    void successfulCompletionSavesResultAndConsumesQuota() {
        claimJob(ATTEMPT_A, Instant.now());
        stubAiSuccess();

        service.processGeneration(USER_ID, "prompt", todayKst, ATTEMPT_A);

        RecipeGenerationJob job = jobRepository.findByUserId(USER_ID).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(RecipeGenerationJobStatus.DONE);
        assertThat(job.getResultJson()).isNotBlank();
        assertThat(usageRepository.count()).isEqualTo(1);
        verify(notificationService).sendNotification(anyLong(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("같은 시도가 중복 완료되어도 사용량과 알림은 한 번만 기록된다")
    void duplicateCompletionOfSameAttemptIsIdempotent() {
        claimJob(ATTEMPT_A, Instant.now());
        stubAiSuccess();

        service.processGeneration(USER_ID, "prompt", todayKst, ATTEMPT_A);
        service.processGeneration(USER_ID, "prompt", todayKst, ATTEMPT_A);

        RecipeGenerationJob job = jobRepository.findByUserId(USER_ID).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(RecipeGenerationJobStatus.DONE);
        assertThat(usageRepository.count()).isEqualTo(1);
        verify(notificationService, times(1)).sendNotification(anyLong(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("탈환당한 좀비 시도의 늦은 완료는 새 시도의 job 상태를 건드리지 못한다")
    void zombieAttemptCannotOverrideReclaimedJob() {
        claimJob(ATTEMPT_A, Instant.now().minus(Duration.ofMinutes(11)));
        // 새 시도가 stale PENDING을 탈환한 상황
        claimJob(ATTEMPT_B, Instant.now());
        stubAiSuccess();

        service.processGeneration(USER_ID, "prompt", todayKst, ATTEMPT_A);

        RecipeGenerationJob job = jobRepository.findByUserId(USER_ID).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(RecipeGenerationJobStatus.PENDING);
        assertThat(job.getAttemptId()).isEqualTo(ATTEMPT_B);
        assertThat(usageRepository.count()).isZero();
        verify(notificationService, never()).sendNotification(anyLong(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("탈환당한 좀비 시도의 늦은 실패도 새 시도의 job 상태를 건드리지 못한다")
    void zombieFailureCannotOverrideReclaimedJob() {
        claimJob(ATTEMPT_A, Instant.now().minus(Duration.ofMinutes(11)));
        claimJob(ATTEMPT_B, Instant.now());
        when(chatClient.prompt().user(anyString()).call().entity(GeneratedRecipesResponseDto.class))
                .thenThrow(new TransientAiException("quota exceeded"));

        service.processGeneration(USER_ID, "prompt", todayKst, ATTEMPT_A);

        RecipeGenerationJob job = jobRepository.findByUserId(USER_ID).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(RecipeGenerationJobStatus.PENDING);
        assertThat(job.getAttemptId()).isEqualTo(ATTEMPT_B);
    }

    @Test
    @DisplayName("push 발송 실패는 레시피 완료(DONE + 사용량 기록)에 영향을 주지 않는다")
    void pushFailureDoesNotAffectRecipeCompletion() {
        claimJob(ATTEMPT_A, Instant.now());
        stubAiSuccess();
        doThrow(new RuntimeException("push infrastructure down"))
                .when(notificationService).sendNotification(anyLong(), anyString(), anyString(), any(), any());

        // 알림 예외는 @Async 경계에서 삼켜지므로 job 상태에 영향이 없어야 한다
        assertThatThrownBy(() -> service.processGeneration(USER_ID, "prompt", todayKst, ATTEMPT_A))
                .isInstanceOf(RuntimeException.class);

        RecipeGenerationJob job = jobRepository.findByUserId(USER_ID).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(RecipeGenerationJobStatus.DONE);
        assertThat(job.getResultJson()).isNotBlank();
        assertThat(usageRepository.count()).isEqualTo(1);
    }

    private void stubAiSuccess() {
        GeneratedRecipesResponseDto response = new GeneratedRecipesResponseDto(List.of(
                new GeneratedRecipeDto("김치볶음밥", null, null, "20분", 500, List.of(), List.of("1. 볶는다."))
        ));
        when(chatClient.prompt().user(anyString()).call().entity(GeneratedRecipesResponseDto.class))
                .thenReturn(response);
    }

    private void claimJob(String attemptId, Instant startedAt) {
        transactionTemplate.executeWithoutResult(status ->
                jobRepository.tryStartGeneration(USER_ID, todayKst, attemptId, startedAt,
                        Instant.now().minus(RecipeAiService.STALE_PENDING_TIMEOUT)));
    }
}
