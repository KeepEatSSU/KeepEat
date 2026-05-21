package com.keepeat.backend.domain.admin.dto;

import java.time.LocalDate;

public record AdminRecipeListItemDto(
        Long recipeId,
        String recipeName,
        LocalDate createdAt,
        long likeCount,
        long dislikeCount,
        long bookmarkCount
) {
    public AdminRecipeListItemDto(Long recipeId, String recipeName, LocalDate createdAt){
        this(recipeId, recipeName, createdAt, 0L, 0L, 0L);
    }
}
