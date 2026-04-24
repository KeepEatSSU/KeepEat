package com.keepeat.backend.domain.recipe.dto;

public record RecipeIngredientDetailDto(
        String name,
        String amount,
        boolean isOwned
) { }
