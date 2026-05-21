package com.keepeat.backend.domain.recipe.dto;

import java.util.List;

public record RecipeListResponseDto (
        List<RecipeListItemDto> recipes,
        String nextCursor,
        boolean hasNext
){ }
