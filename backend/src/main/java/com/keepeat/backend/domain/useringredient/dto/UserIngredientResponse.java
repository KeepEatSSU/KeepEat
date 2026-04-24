package com.keepeat.backend.domain.useringredient.dto;

import com.keepeat.backend.domain.common.enums.StorageType;
import com.keepeat.backend.domain.useringredient.UserIngredient;

import java.time.LocalDate;

public record UserIngredientResponse(
        Long id,
        String name,
        String customName,
        String categoryName,
        String subCategoryName,
        StorageType storageType,
        Double quantity,
        String unit,
        LocalDate purchaseDate,
        LocalDate expiryDate,
        Long daysLeft
) {
    public static UserIngredientResponse of(UserIngredient ui, Long daysLeft) {
        return new UserIngredientResponse(
                ui.getId(),
                ui.getIngredient().getName(),
                ui.getCustomName(),
                ui.getIngredient().getSubCategory().getCategory().getName(),
                ui.getIngredient().getSubCategory().getName(),
                ui.getStorageType(),
                ui.getQuantity(),
                ui.getUnit(),
                ui.getPurchaseDate(),
                ui.getExpiryDate(),
                daysLeft
        );
    }
}
