package com.keepeat.backend.domain.recipe.dto;

import com.keepeat.backend.domain.common.enums.IngredientStatus;

public record RecipeIngredientDetailDto(
        String name,
        String amount,
        boolean isOwned,
        IngredientStatus status
) { }
