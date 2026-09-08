package com.keepeat.backend.domain.recipe.repository;

import com.keepeat.backend.domain.recipe.entity.RecipeGenerationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

public interface RecipeGenerationJobRepository extends JpaRepository<RecipeGenerationJob, Long> {
    Optional<RecipeGenerationJob> findByUserId(Long userId);

    //PostgreSQL에 의존적인 쿼리임.
    // started_at이 staleBefore보다 오래된(또는 NULL인) PENDING은 죽은 시도로 보고 탈환을 허용한다.
    @Modifying
    @Query(
            value = """
                INSERT INTO recipe_generation_job AS job (
                    user_id,
                    status,
                    attempt_id,
                    started_at,
                    created_at
                )
                VALUES (
                    :userId,
                    'PENDING',
                    :attemptId,
                    :startedAt,
                    :today
                )
                ON CONFLICT (user_id)
                DO UPDATE
                SET status = 'PENDING',
                    result_json = NULL,
                    error_code = NULL,
                    error_message = NULL,
                    attempt_id = :attemptId,
                    started_at = :startedAt,
                    updated_at = :today
                WHERE job.status <> 'PENDING'
                   OR job.started_at IS NULL
                   OR job.started_at < :staleBefore
                """,
            nativeQuery = true
    )
    int tryStartGeneration(
            @Param("userId") Long userId,
            @Param("today") LocalDate today,
            @Param("attemptId") String attemptId,
            @Param("startedAt") Instant startedAt,
            @Param("staleBefore") Instant staleBefore
    );

    // 해당 시도가 여전히 job을 소유한(PENDING) 경우에만 상태를 전이한다.
    // 탈환당한 좀비 시도가 남의 결과를 덮어쓰지 못하게 하는 멱등 가드.
    @Modifying
    @Query(
            value = """
                UPDATE recipe_generation_job
                SET status = 'DONE',
                    result_json = :resultJson,
                    updated_at = :today
                WHERE user_id = :userId
                  AND attempt_id = :attemptId
                  AND status = 'PENDING'
                """,
            nativeQuery = true
    )
    int completeIfCurrentAttempt(
            @Param("userId") Long userId,
            @Param("attemptId") String attemptId,
            @Param("resultJson") String resultJson,
            @Param("today") LocalDate today
    );

    @Modifying
    @Query(
            value = """
                UPDATE recipe_generation_job
                SET status = 'FAILED',
                    error_code = :errorCode,
                    error_message = :errorMessage,
                    updated_at = :today
                WHERE user_id = :userId
                  AND attempt_id = :attemptId
                  AND status = 'PENDING'
                """,
            nativeQuery = true
    )
    int failIfCurrentAttempt(
            @Param("userId") Long userId,
            @Param("attemptId") String attemptId,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("today") LocalDate today
    );
}
