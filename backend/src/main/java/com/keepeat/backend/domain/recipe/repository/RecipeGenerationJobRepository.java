package com.keepeat.backend.domain.recipe.repository;

import com.keepeat.backend.domain.recipe.entity.RecipeGenerationJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecipeGenerationJobRepository extends JpaRepository<RecipeGenerationJob, Long> {
    Optional<RecipeGenerationJob> findByUserId(Long userId);
}
