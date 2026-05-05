package com.keepeat.backend.domain.ingredient.dto;

import java.util.List;

public record IngredientAiResponseDto(
        List<IngredientInfoDto> ingredients
) { }
