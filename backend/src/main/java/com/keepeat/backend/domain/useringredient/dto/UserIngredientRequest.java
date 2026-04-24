package com.keepeat.backend.domain.useringredient.dto;

import com.keepeat.backend.domain.common.enums.StorageType;

import java.time.LocalDate;

public record UserIngredientRequest(
        StorageType storageType,
        Double quantity,
        String unit,
        LocalDate expiryDate,
        LocalDate purchaseDate,
        String customName
) {
}