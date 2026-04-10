package com.keepeat.backend.domain.recipe.dto;

import com.keepeat.backend.domain.common.enums.CookingMethod;
import com.keepeat.backend.domain.common.enums.Difficulty;

import java.util.List;

public record GeneratedRecipeDto(
        String recipeName,
        Difficulty difficulty,
        CookingMethod cookingMethod,
        String cookingTime,
        List<RecipeIngredientDto> requiredIngredients,
        List<String> instructions
){

}
