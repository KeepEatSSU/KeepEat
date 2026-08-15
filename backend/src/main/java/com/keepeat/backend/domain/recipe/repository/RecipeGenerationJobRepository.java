package com.keepeat.backend.domain.recipe.repository;

import com.keepeat.backend.domain.recipe.entity.RecipeGenerationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface RecipeGenerationJobRepository extends JpaRepository<RecipeGenerationJob, Long> {
    Optional<RecipeGenerationJob> findByUserId(Long userId);

    //PostgreSQL에 의존적인 쿼리임.
    @Modifying
    @Query(
            value = """
                INSERT INTO recipe_generation_job AS job (
                    user_id,
                    status,
                    created_at
                )
                VALUES (
                    :userId,
                    'PENDING',
                    :today
                )
                ON CONFLICT (user_id)
                DO UPDATE
                SET status = 'PENDING',
                    result_json = NULL,
                    error_code = NULL,
                    error_message = NULL,
                    updated_at = :today
                WHERE job.status <> 'PENDING'
                """,
            nativeQuery = true
    )
    int tryStartGeneration(
            @Param("userId") Long userId,
            @Param("today") LocalDate today
    );
}
