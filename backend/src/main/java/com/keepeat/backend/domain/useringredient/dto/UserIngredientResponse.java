package com.keepeat.backend.domain.useringredient.dto;

import com.keepeat.backend.domain.common.enums.StorageType;
import com.keepeat.backend.domain.ingredient.Ingredient;
import com.keepeat.backend.domain.useringredient.UserIngredient;

import java.time.LocalDate;

public record UserIngredientResponse(
        Long id,
        Long ingredientId,
        String name,
        String customName,
        String categoryName,
        String subCategoryName,
        StorageType storageType,
        Double quantity,
        String unit,
        LocalDate purchaseDate,
        LocalDate expiryDate,
        Long daysLeft,
        boolean isCustom
) {
    public static UserIngredientResponse of(UserIngredient ui, Long daysLeft) {
        Ingredient ingredient = ui.getIngredient();
        boolean isCustom = (ingredient == null);

        return new UserIngredientResponse(
                ui.getId(),
                isCustom ? null : ingredient.getId(),
                isCustom ? null : ingredient.getName(),
                ui.getCustomName(),
                isCustom ? null : ingredient.getSubCategory().getCategory().getName(),
                isCustom ? null : ingredient.getSubCategory().getName(),
                ui.getStorageType(),
                ui.getQuantity(),
                ui.getUnit(),
                ui.getPurchaseDate(),
                ui.getExpiryDate(),
                daysLeft,
                isCustom
        );
    }
}
