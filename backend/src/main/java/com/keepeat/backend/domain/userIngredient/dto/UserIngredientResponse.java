package com.keepeat.backend.domain.userIngredient.dto;

import com.keepeat.backend.domain.common.enums.StorageType;
import com.keepeat.backend.domain.userIngredient.UserIngredient;

import java.time.LocalDate;

public record UserIngredientResponse(
        Long id,
        String name,
        String nameSubtitle,
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
                ui.getIngredient().getNameSubtitle(),
                ui.getStorageType(),
                ui.getQuantity(),
                ui.getUnit(),
                ui.getPurchaseDate(),
                ui.getExpiryDate(),
                daysLeft
        );
    }
}
