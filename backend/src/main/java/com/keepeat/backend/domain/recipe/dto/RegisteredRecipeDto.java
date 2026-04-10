package com.keepeat.backend.domain.recipe.dto;

import com.keepeat.backend.domain.common.enums.CookingMethod;
import com.keepeat.backend.domain.common.enums.Difficulty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RegisteredRecipeDto(

        @NotBlank(message = "레시피 이름이 누락되었습니다.")
        String recipeName,

        @NotNull(message = "레시피 난이도가 누락되었습니다.")
        Difficulty difficulty,

        @NotNull(message = "요리 방법이 누락되었습니다.")
        CookingMethod cookingMethod,

        @NotBlank(message = "조리시간이 누락되었습니다.")
        String cookingTime,

        @NotNull(message = "칼로리가 누락되었습니다.")
        Integer calories,

        @NotEmpty(message = "레시피에 필요한 재료가 누락되었습니다.")
        List<RecipeIngredientDto> requiredIngredients,

        @NotEmpty(message = "조리방법이 누락되었습니다.")
        List<String> instructions
) { }
