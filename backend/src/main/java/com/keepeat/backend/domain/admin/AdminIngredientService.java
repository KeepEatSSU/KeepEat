package com.keepeat.backend.domain.admin;

import com.keepeat.backend.domain.admin.dto.IngredientFormDto;
import com.keepeat.backend.domain.admin.dto.IngredientListItemDto;
import com.keepeat.backend.domain.common.enums.IngredientStatus;
import com.keepeat.backend.domain.common.exception.ErrorCode;
import com.keepeat.backend.domain.common.exception.KeepEatException;
import com.keepeat.backend.domain.ingredient.*;
import com.keepeat.backend.domain.recipe.repository.RecipeIngredientRepository;
import com.keepeat.backend.domain.subcategory.SubCategory;
import com.keepeat.backend.domain.subcategory.SubCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminIngredientService {

    private final IngredientRepository ingredientRepository;
    private final IngredientStorageRepository ingredientStorageRepository;
    private final SubCategoryRepository subCategoryRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;
    private final IngredientAliasRepository ingredientAliasRepository;

    @Transactional(readOnly = true)
    public Page<IngredientListItemDto> findAll(String keyword, Long subCategoryId, IngredientStatus status, Pageable pageable) {
        return ingredientRepository.searchForAdmin(keyword, subCategoryId, status, pageable)
                .map(IngredientListItemDto::from);
    }

    @Transactional(readOnly = true)
    public IngredientFormDto findOne(Long id) {
        Ingredient ingredient = ingredientRepository.findById(id)
                .orElseThrow(() -> new KeepEatException(ErrorCode.INGREDIENT_NOT_FOUND));
        List<IngredientStorage> storages = ingredientStorageRepository.findAllByIngredientId(id);
        return IngredientFormDto.fromEntity(ingredient, storages);
    }

    @Transactional
    public Long create(IngredientFormDto form) {
        ingredientRepository.findByName(form.getName()).ifPresent(existing -> {
            throw new KeepEatException(ErrorCode.INGREDIENT_NAME_DUPLICATED);
        });

        SubCategory subCategory = subCategoryRepository.findById(form.getSubCategoryId())
                .orElseThrow(() -> new KeepEatException(ErrorCode.SUBCATEGORY_NOT_FOUND));

        Ingredient ingredient = Ingredient.builder()
                .subCategory(subCategory)
                .name(form.getName())
                .build();

        try {
            ingredientRepository.save(ingredient);
        } catch (DataIntegrityViolationException e) {
            throw new KeepEatException(ErrorCode.INGREDIENT_NAME_DUPLICATED);
        }

        saveStorages(ingredient, form.getStorages());
        return ingredient.getId();
    }

    @Transactional
    public void update(Long id, IngredientFormDto form) {
        Ingredient ingredient = ingredientRepository.findById(id)
                .orElseThrow(() -> new KeepEatException(ErrorCode.INGREDIENT_NOT_FOUND));

        ingredientRepository.findByName(form.getName()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new KeepEatException(ErrorCode.INGREDIENT_NAME_DUPLICATED);
            }
        });

        SubCategory subCategory = subCategoryRepository.findById(form.getSubCategoryId())
                .orElseThrow(() -> new KeepEatException(ErrorCode.SUBCATEGORY_NOT_FOUND));

        ingredient.update(subCategory, form.getName());

        ingredientStorageRepository.deleteByIngredientId(id);
        ingredientStorageRepository.flush();
        saveStorages(ingredient, form.getStorages());
    }

    @Transactional(readOnly = true)
    public List<SubCategory> findAllSubCategoriesForSelect() {
        return subCategoryRepository.findAll(Sort.by("category.id").and(Sort.by("name")));
    }

    private void saveStorages(Ingredient ingredient, List<IngredientFormDto.StorageFormDto> storageDtos) {
        if (storageDtos == null || storageDtos.isEmpty()) return;
        List<IngredientStorage> entities = storageDtos.stream()
                .map(dto -> IngredientStorage.builder()
                        .ingredient(ingredient)
                        .storageType(dto.getStorageType())
                        .min(dto.getMin())
                        .max(dto.getMax())
                        .metric(dto.getMetric())
                        .build())
                .toList();
        ingredientStorageRepository.saveAll(entities);
    }

    @Transactional
    public void activateIngredientStatus(Long id){
        Ingredient ingredient = ingredientRepository.findById(id)
                .orElseThrow(() -> new KeepEatException(ErrorCode.INGREDIENT_NOT_FOUND));

        if(ingredient.getStatus() != IngredientStatus.PENDING)
            throw new KeepEatException(ErrorCode.INGREDIENT_NOT_PENDING);

        ingredient.activateStatus();
    }

    @Transactional
    public void replacePendingIngredient(Long pendingId, Long targetId, boolean addAlias){

        Ingredient pendingIngredient = ingredientRepository.findById(pendingId)
                .orElseThrow(() -> new KeepEatException(ErrorCode.INGREDIENT_NOT_FOUND));

        Ingredient targetIngredient = ingredientRepository.findById(targetId)
                .orElseThrow(() -> new KeepEatException(ErrorCode.INGREDIENT_NOT_FOUND));

        if(pendingIngredient.getStatus() != IngredientStatus.PENDING)
            throw new KeepEatException(ErrorCode.INGREDIENT_NOT_PENDING);
        if (targetIngredient.getStatus() != IngredientStatus.ACTIVE)
            throw new KeepEatException(ErrorCode.REPLACE_TARGET_NOT_ACTIVE);
        if (pendingId.equals(targetId))
            throw new KeepEatException(ErrorCode.REPLACE_TARGET_SAME);

        recipeIngredientRepository.updateIngredient(pendingId, targetId);

        if(addAlias){
            IngredientAlias ingredientAlias = new IngredientAlias(targetIngredient, pendingIngredient.getName());
            ingredientAliasRepository.save(ingredientAlias);
        }

        ingredientStorageRepository.deleteByIngredientId(pendingId);
        ingredientRepository.deleteById(pendingId);
    }

    @Transactional(readOnly = true)
    public Ingredient findOnePendingIngredient(Long id) {
        Ingredient ingredient = ingredientRepository.findById(id)
                .orElseThrow(() -> new KeepEatException(ErrorCode.INGREDIENT_NOT_FOUND));
        if (ingredient.getStatus() != IngredientStatus.PENDING) {
            throw new KeepEatException(ErrorCode.INGREDIENT_NOT_PENDING);
        }
        return ingredient;
    }
}
