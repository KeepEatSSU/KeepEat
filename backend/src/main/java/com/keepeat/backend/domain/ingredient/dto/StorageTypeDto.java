package com.keepeat.backend.domain.ingredient.dto;

import com.keepeat.backend.domain.common.enums.Metric;
import com.keepeat.backend.domain.common.enums.StorageType;

public record StorageTypeDto(
        StorageType storageType,
        Integer min,
        Integer max,
        Metric metric
) { }
