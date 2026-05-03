package com.keepeat.backend.domain.ingredient.dto;

import java.util.List;

public record IngredientInfoDto(
        String ingredientName,
        String categoryName,
        String subCategoryName,
        List<StorageTypeDto> storageInfo
) { }
