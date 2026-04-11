package com.keepeat.backend.domain.recipe.dto;

import com.keepeat.backend.domain.common.enums.CookingMethod;
import com.keepeat.backend.domain.common.enums.Difficulty;

import java.time.LocalDate;

public record MyRecipeDto(
        Long recipeId,
        String recipeName,
        Difficulty difficulty,
        CookingMethod cookingMethod,
        String cookingTime,
        Integer calories,
        LocalDate createdAt

) { }
