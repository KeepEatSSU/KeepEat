package com.keepeat.backend.domain.recipe.dto;

import com.keepeat.backend.domain.common.enums.CookingMethod;
import com.keepeat.backend.domain.common.enums.Difficulty;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

@Builder
public record RecipeDetailResponseDto(
        Long recipeId,
        String recipeName,
        Difficulty difficulty,
        CookingMethod cookingMethod,
        String cookingTime,
        Integer calories,
        List<RecipeIngredientDto> requiredIngredients,
        List<String> instructions,
        LocalDate createdAt
) { }
