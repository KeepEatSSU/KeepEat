package com.keepeat.backend.domain.recipe.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record MyRecipesRegisterRequestDto(
        @NotEmpty(message = "레시피 아이디가 누락되었습니다.")
        List<Long> recipeIds
) {
}
