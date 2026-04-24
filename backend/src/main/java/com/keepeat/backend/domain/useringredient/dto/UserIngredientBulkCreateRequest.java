package com.keepeat.backend.domain.useringredient.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record UserIngredientBulkCreateRequest(
        @NotEmpty List<@Valid UserIngredientCreateRequest> items
) {
}
