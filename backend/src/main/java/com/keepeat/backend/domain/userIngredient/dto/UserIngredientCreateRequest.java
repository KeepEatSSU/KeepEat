package com.keepeat.backend.domain.useringredient.dto;

import com.keepeat.backend.domain.common.enums.StorageType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record UserIngredientCreateRequest(
        @NotNull Long ingredientId,
        @NotNull StorageType storageType,
        @NotNull LocalDate purchaseDate,
        LocalDate expiryDate,
        Double quantity,
        String unit,
        String customName
) {
}
