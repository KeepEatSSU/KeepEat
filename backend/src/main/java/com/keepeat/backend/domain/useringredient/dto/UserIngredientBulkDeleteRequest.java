package com.keepeat.backend.domain.useringredient.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record UserIngredientBulkDeleteRequest(
        @NotEmpty
        @Schema(description = "삭제할 UserIngredient ID 목록", example = "[1, 2, 3]")
        List<Long> ids
) {
}
