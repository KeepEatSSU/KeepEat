package com.keepeat.backend.domain.admin.dto;

import com.keepeat.backend.domain.common.enums.IngredientStatus;
import com.keepeat.backend.domain.ingredient.Ingredient;

public record IngredientListItemDto(
        Long id,
        String name,
        Long subCategoryId,
        String subCategoryName,
        String categoryName,
        IngredientStatus status
) {
    public static IngredientListItemDto from(Ingredient ingredient) {
        return new IngredientListItemDto(
                ingredient.getId(),
                ingredient.getName(),
                ingredient.getSubCategory().getId(),
                ingredient.getSubCategory().getName(),
                ingredient.getSubCategory().getCategory().getName(),
                ingredient.getStatus()
        );
    }
}
