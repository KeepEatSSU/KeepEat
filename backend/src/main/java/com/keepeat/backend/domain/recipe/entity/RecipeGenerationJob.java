package com.keepeat.backend.domain.recipe.entity;

import com.keepeat.backend.domain.common.enums.RecipeGenerationJobStatus;
import com.keepeat.backend.domain.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecipeGenerationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecipeGenerationJobStatus status;

    @Column(columnDefinition = "TEXT")
    private String resultJson;

    @Enumerated(EnumType.STRING)
    @Column
    private ErrorCode errorCode;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private LocalDate createdAt;

    @Column
    private LocalDate updatedAt;

    // PENDING 클레임 시각. NULL(과거 스키마의 행 포함)은 stale로 취급되어 탈환 가능하다.
    @Column
    private Instant startedAt;

    // 생성 시도 식별자. 상태 전이는 이 값이 일치하는 시도만 수행할 수 있다.
    @Column(length = 36)
    private String attemptId;


    public static RecipeGenerationJob create(Long userId){
        RecipeGenerationJob job = new RecipeGenerationJob();
        job.userId = userId;
        job.status = RecipeGenerationJobStatus.PENDING;
        job.createdAt = LocalDate.now();
        return job;
    }
}
