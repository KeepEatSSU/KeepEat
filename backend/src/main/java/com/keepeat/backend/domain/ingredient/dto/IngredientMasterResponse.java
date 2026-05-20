package com.keepeat.backend.domain.ingredient.dto;

import com.keepeat.backend.domain.common.enums.StorageType;

import java.util.List;

public record IngredientMasterResponse(
        List<CategoryDto> categories,
        List<SubCategoryDto> subCategories,
        List<IngredientDto> ingredients
) {
    public record CategoryDto(Long id, String name) {}

    public record SubCategoryDto(Long id, Long categoryId, String name) {}

    public record IngredientDto(
            Long id,
            Long subCategoryId,
            String name,
            List<StorageOptionDto> storageOptions
    ) {}

    public record StorageOptionDto(
            StorageType storageType,
            int minDays,
            int maxDays
    ) {}
}
