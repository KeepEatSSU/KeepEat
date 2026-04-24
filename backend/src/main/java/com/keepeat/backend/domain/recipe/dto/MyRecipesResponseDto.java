package com.keepeat.backend.domain.recipe.dto;

import java.util.List;

public record MyRecipesResponseDto (
        List<MyRecipeDto> myRecipes
){
}
