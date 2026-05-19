package com.keepeat.backend.domain.useringredient.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.keepeat.backend.domain.common.enums.StorageType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UserIngredientCreateRequest(
        @Schema(description = "마스터 식재료 ID. 커스텀 식재료 등록 시 null", example = "1", nullable = true)
        Long ingredientId,
        @NotNull
        @Schema(description = "보관 방법", example = "냉장")
        StorageType storageType,
        @NotNull
        @Schema(description = "구매일", example = "2026-05-19")
        LocalDate purchaseDate,
        @Schema(description = "소비기한. 마스터 식재료면 자동 계산, 커스텀이면 필수", example = "2026-06-30", nullable = true)
        LocalDate expiryDate,
        @Schema(description = "수량", example = "1", nullable = true)
        Double quantity,
        @Schema(description = "단위", example = "병", nullable = true)
        String unit,
        @Size(max = 100)
        @Schema(description = "사용자 정의 이름. 커스텀 식재료(ingredientId=null)면 필수", example = "수제 딸기잼", nullable = true)
        String customName
) {
    @AssertTrue(message = "ingredientId가 없으면 customName과 expiryDate는 필수입니다")
    @JsonIgnore
    @Schema(hidden = true)
    public boolean isCustomFieldsValidWhenNoIngredient() {
        if (ingredientId != null) {
            return true;
        }
        return customName != null && !customName.isBlank() && expiryDate != null;
    }
}
