package com.keepeat.backend.domain.recipe.entity;

import com.keepeat.backend.domain.common.enums.RecipeGenerationJobStatus;
import com.keepeat.backend.domain.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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


    public static RecipeGenerationJob create(Long userId){
        RecipeGenerationJob job = new RecipeGenerationJob();
        job.userId = userId;
        job.status = RecipeGenerationJobStatus.PENDING;
        job.createdAt = LocalDate.now();
        return job;
    }

    public void done(String resultJson){
        this.status = RecipeGenerationJobStatus.DONE;
        this.resultJson = resultJson;
        this.updatedAt = LocalDate.now();
    }

    public void failed(ErrorCode errorCode,String errorMessage){
        this.status = RecipeGenerationJobStatus.FAILED;
        this.errorMessage = errorMessage;
        this.errorCode = errorCode;
        this.updatedAt = LocalDate.now();
    }
}
