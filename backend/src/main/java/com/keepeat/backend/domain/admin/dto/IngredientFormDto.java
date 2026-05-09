package com.keepeat.backend.domain.admin.dto;

import com.keepeat.backend.domain.common.enums.Metric;
import com.keepeat.backend.domain.common.enums.StorageType;
import com.keepeat.backend.domain.ingredient.Ingredient;
import com.keepeat.backend.domain.ingredient.IngredientStorage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IngredientFormDto {

    private Long id;

    @NotBlank(message = "식재료명을 입력해주세요.")
    private String name;

    @NotNull(message = "서브카테고리를 선택해주세요.")
    private Long subCategoryId;

    @Valid
    private List<StorageFormDto> storages = new ArrayList<>();

    public static IngredientFormDto fromEntity(Ingredient ingredient, List<IngredientStorage> storages) {
        List<StorageFormDto> storageDtos = storages.stream()
                .map(StorageFormDto::fromEntity)
                .toList();
        return new IngredientFormDto(
                ingredient.getId(),
                ingredient.getName(),
                ingredient.getSubCategory().getId(),
                new ArrayList<>(storageDtos)
        );
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StorageFormDto {

        @NotNull(message = "보관 방식을 선택해주세요.")
        private StorageType storageType;

        @NotNull(message = "최소값을 입력해주세요.")
        @Min(value = 0, message = "0 이상이어야 합니다.")
        private Integer min;

        @NotNull(message = "최대값을 입력해주세요.")
        @Min(value = 0, message = "0 이상이어야 합니다.")
        private Integer max;

        @NotNull(message = "단위를 선택해주세요.")
        private Metric metric;

        public static StorageFormDto fromEntity(IngredientStorage storage) {
            return new StorageFormDto(
                    storage.getStorageType(),
                    storage.getMin(),
                    storage.getMax(),
                    storage.getMetric()
            );
        }
    }
}
