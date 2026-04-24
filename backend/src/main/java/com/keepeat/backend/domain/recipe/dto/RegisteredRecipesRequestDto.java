package com.keepeat.backend.domain.recipe.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record RegisteredRecipesRequestDto(
        @NotEmpty(message = "레시피가 누락되었습니다.")
        @Valid
        List<RegisteredRecipeDto> recipes
) { }