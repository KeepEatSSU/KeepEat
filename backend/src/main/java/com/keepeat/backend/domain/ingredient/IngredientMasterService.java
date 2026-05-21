package com.keepeat.backend.domain.ingredient;

import com.keepeat.backend.domain.category.Category;
import com.keepeat.backend.domain.ingredient.dto.IngredientMasterResponse;
import com.keepeat.backend.domain.ingredient.dto.IngredientMasterResponse.CategoryDto;
import com.keepeat.backend.domain.ingredient.dto.IngredientMasterResponse.IngredientDto;
import com.keepeat.backend.domain.ingredient.dto.IngredientMasterResponse.StorageOptionDto;
import com.keepeat.backend.domain.ingredient.dto.IngredientMasterResponse.SubCategoryDto;
import com.keepeat.backend.domain.subcategory.SubCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IngredientMasterService {

    private final IngredientRepository ingredientRepository;
    private final IngredientStorageRepository ingredientStorageRepository;
    private final ObjectMapper objectMapper;

    public record MasterPayload(IngredientMasterResponse body, String etag) {}

    public MasterPayload getMaster() {
        List<Ingredient> ingredients = ingredientRepository.findAllActiveWithSubCategoryAndCategory();
        List<Long> ingredientIds = ingredients.stream().map(Ingredient::getId).toList();

        Map<Long, List<StorageOptionDto>> storageByIngredientId = ingredientIds.isEmpty()
                ? Map.of()
                : ingredientStorageRepository.findAllByIngredientIdIn(ingredientIds).stream()
                        .collect(Collectors.groupingBy(
                                s -> s.getIngredient().getId(),
                                Collectors.mapping(
                                        s -> new StorageOptionDto(
                                                s.getStorageType(),
                                                s.getMin() * s.getMetric().toDays(),
                                                s.getMax() * s.getMetric().toDays()
                                        ),
                                        Collectors.toList()
                                )
                        ));

        Map<Long, SubCategory> subCategoryById = new LinkedHashMap<>();
        Map<Long, Category> categoryById = new LinkedHashMap<>();

        List<IngredientDto> ingredientDtos = ingredients.stream().map(i -> {
            SubCategory sc = i.getSubCategory();
            subCategoryById.putIfAbsent(sc.getId(), sc);
            categoryById.putIfAbsent(sc.getCategory().getId(), sc.getCategory());

            return new IngredientDto(
                    i.getId(),
                    sc.getId(),
                    i.getName(),
                    storageByIngredientId.getOrDefault(i.getId(), List.of())
            );
        }).toList();

        List<CategoryDto> categoryDtos = categoryById.values().stream()
                .map(c -> new CategoryDto(c.getId(), c.getName()))
                .toList();

        List<SubCategoryDto> subCategoryDtos = subCategoryById.values().stream()
                .map(sc -> new SubCategoryDto(sc.getId(), sc.getCategory().getId(), sc.getName()))
                .toList();

        IngredientMasterResponse body = new IngredientMasterResponse(categoryDtos, subCategoryDtos, ingredientDtos);
        return new MasterPayload(body, computeEtag(body));
    }

    private String computeEtag(IngredientMasterResponse body) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
