package com.keepeat.backend.domain.recipe.dto;

public record RecipeIngredientDto (
        String name,
        String amount,
        boolean isAdditional
){ }
