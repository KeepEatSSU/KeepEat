package com.keepeat.backend.domain.recipe.dto;

import com.keepeat.backend.domain.common.enums.ReactionType;
import jakarta.validation.constraints.NotNull;

public record RecipeReactionRequestDto(
        @NotNull(message = "반응 타입이 누락되었습니다.")
        ReactionType reactionType
) { }
