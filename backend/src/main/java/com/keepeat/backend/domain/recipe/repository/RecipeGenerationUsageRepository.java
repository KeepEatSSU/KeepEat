package com.keepeat.backend.domain.recipe.repository;

import com.keepeat.backend.domain.recipe.entity.RecipeGenerationUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface RecipeGenerationUsageRepository extends JpaRepository<RecipeGenerationUsage, Long> {

    boolean existsByUserIdAndUsageDate(Long userId, LocalDate usageDate);
}
