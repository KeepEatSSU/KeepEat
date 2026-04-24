package com.keepeat.backend.domain.ocr.dto;

import com.keepeat.backend.domain.common.enums.StorageType;

import java.util.List;

public record OcrParseResponse(List<OcrIngredientCandidate> candidates) {

    public record OcrIngredientCandidate(
            String rawName,
            String customName,
            Double quantity,
            String unit,
            MatchStatus matchStatus,
            Long ingredientId,
            String ingredientName,
            List<StorageOption> storageOptions
    ) {}

    public record StorageOption(
            StorageType storageType,
            Integer minDays,
            Integer maxDays
    ) {}

    public enum MatchStatus {
        MATCHED,
        UNMATCHED
    }
}
