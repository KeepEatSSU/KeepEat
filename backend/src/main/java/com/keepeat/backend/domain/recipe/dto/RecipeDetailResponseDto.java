package com.keepeat.backend.domain.recipe.dto;

import com.keepeat.backend.domain.common.enums.CookingMethod;
import com.keepeat.backend.domain.common.enums.Difficulty;
import com.keepeat.backend.domain.common.enums.ReactionType;
import lombok.Builder;

import java.util.List;

@Builder
public record RecipeDetailResponseDto(
        Long recipeId,
        String recipeName,
        Difficulty difficulty,
        CookingMethod cookingMethod,
        String cookingTime,
        Integer calories,
        List<RecipeIngredientDetailDto> requiredIngredients,
        List<String> instructions,
        boolean isRegisteredMyRecipe,
        long likeCount,
        long dislikeCount,
        ReactionType myReaction
) { }
