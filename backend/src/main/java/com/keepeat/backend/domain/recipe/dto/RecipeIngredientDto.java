package com.keepeat.backend.domain.recipe.dto;

import jakarta.validation.constraints.NotBlank;

public record RecipeIngredientDto (
        @NotBlank(message = "재료 이름이 누락되었습니다.")
        String name,

        @NotBlank(message = "재료 양이 누락되었습니다.")
        String amount
){ }
