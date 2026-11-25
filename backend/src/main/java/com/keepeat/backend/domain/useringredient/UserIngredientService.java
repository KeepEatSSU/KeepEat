package com.keepeat.backend.domain.useringredient;

import com.keepeat.backend.domain.common.enums.IngredientStatus;
import com.keepeat.backend.domain.common.enums.StorageType;
import com.keepeat.backend.domain.common.exception.ErrorCode;
import com.keepeat.backend.domain.common.exception.KeepEatException;
import com.keepeat.backend.domain.ingredient.Ingredient;
import com.keepeat.backend.domain.ingredient.IngredientRepository;
import com.keepeat.backend.domain.ingredient.IngredientStorage;
import com.keepeat.backend.domain.ingredient.IngredientStorageRepository;
import com.keepeat.backend.domain.recipe.repository.RecipeRepository;
import com.keepeat.backend.domain.useringredient.dto.UserIngredientCreateRequest;
import com.keepeat.backend.domain.useringredient.dto.UserIngredientRequest;
import com.keepeat.backend.domain.useringredient.dto.UserIngredientResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserIngredientService {

    private final UserIngredientRepository userIngredientRepository;
    private final IngredientRepository ingredientRepository;
    private final IngredientStorageRepository ingredientStorageRepository;
    private final RecipeRepository recipeRepository;

    public List<UserIngredientResponse> findAll(Long userId) {
        return userIngredientRepository.findAllByUserIdOrderByExpiryDate(userId)
                .stream()
                .map(ui -> UserIngredientResponse.of(ui, calculateDaysLeft(ui.getExpiryDate())))
                .toList();
    }

    public List<UserIngredientResponse> search(Long userId, String q) {
        return userIngredientRepository.searchByIngredientName(userId, q)
                .stream()
                .map(ui -> UserIngredientResponse.of(ui, calculateDaysLeft(ui.getExpiryDate())))
                .toList();
    }

    public List<UserIngredientResponse> findByRecipe(Long userId, Long recipeId) {
        if (!recipeRepository.existsById(recipeId)) {
            throw new KeepEatException(ErrorCode.RECIPE_NOT_FOUND);
        }
        return userIngredientRepository.findAllByUserIdAndRecipeId(userId, recipeId)
                .stream()
                .map(ui -> UserIngredientResponse.of(ui, calculateDaysLeft(ui.getExpiryDate())))
                .toList();
    }

    @Transactional
    public UserIngredientResponse update(Long userId, Long id, UserIngredientRequest request) {
        UserIngredient ui = userIngredientRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new KeepEatException(ErrorCode.USER_INGREDIENT_NOT_FOUND));

        ui.update(request);

        return UserIngredientResponse.of(ui, calculateDaysLeft(ui.getExpiryDate()));
    }

    @Transactional
    public List<UserIngredientResponse> create(Long userId, List<UserIngredientCreateRequest> items) {
        Map<Long, Ingredient> ingredients = ingredientRepository.findAllById(
                items.stream().map(UserIngredientCreateRequest::ingredientId).filter(Objects::nonNull).distinct().toList()
        ).stream().collect(Collectors.toMap(Ingredient::getId, Function.identity()));

        List<Long> storageIngredientIds = items.stream()
                .filter(item -> item.ingredientId() != null && item.expiryDate() == null)
                .map(UserIngredientCreateRequest::ingredientId)
                .distinct()
                .toList();
        Map<Long, Map<StorageType, IngredientStorage>> storages = storageIngredientIds.isEmpty()
                ? Map.of()
                : ingredientStorageRepository.findAllByIngredientIdIn(storageIngredientIds).stream()
                        .collect(Collectors.groupingBy(
                                storage -> storage.getIngredient().getId(),
                                Collectors.toMap(IngredientStorage::getStorageType, Function.identity())
                        ));

        List<UserIngredient> entities = items.stream().map(item -> {
            if (item.ingredientId() == null) {
                return UserIngredient.builder()
                        .userId(userId)
                        .ingredient(null)
                        .storageType(item.storageType())
                        .purchaseDate(item.purchaseDate())
                        .expiryDate(item.expiryDate())
                        .quantity(item.quantity())
                        .unit(item.unit())
                        .customName(item.customName())
                        .build();
            }

            Ingredient ingredient = ingredients.get(item.ingredientId());
            if (ingredient == null || ingredient.getStatus() != IngredientStatus.ACTIVE) {
                throw new KeepEatException(ErrorCode.INGREDIENT_NOT_FOUND);
            }

            LocalDate expiryDate = item.expiryDate();
            if (expiryDate == null) {
                expiryDate = calculateExpiryDate(
                        storages.getOrDefault(ingredient.getId(), Map.of()).get(item.storageType()),
                        item.purchaseDate()
                );
            }

            return UserIngredient.builder()
                    .userId(userId)
                    .ingredient(ingredient)
                    .storageType(item.storageType())
                    .purchaseDate(item.purchaseDate())
                    .expiryDate(expiryDate)
                    .quantity(item.quantity())
                    .unit(item.unit())
                    .customName(item.customName())
                    .build();
        }).toList();

        List<UserIngredient> saved = userIngredientRepository.saveAll(entities);
        return saved.stream()
                .map(ui -> UserIngredientResponse.of(ui, calculateDaysLeft(ui.getExpiryDate())))
                .toList();
    }

    @Transactional
    public void delete(Long userId, Long id) {
        UserIngredient ui = userIngredientRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new KeepEatException(ErrorCode.USER_INGREDIENT_NOT_FOUND));

        userIngredientRepository.delete(ui);
    }

    @Transactional
    public void deleteMany(Long userId, List<Long> ids) {
        long matched = userIngredientRepository.countByIdInAndUserId(ids, userId);
        if (matched != ids.size()) {
            throw new KeepEatException(ErrorCode.USER_INGREDIENT_NOT_FOUND);
        }
        userIngredientRepository.deleteAllByIdInAndUserId(ids, userId);
    }

    private LocalDate calculateExpiryDate(IngredientStorage storage, LocalDate purchaseDate) {
        if (storage == null) {
            throw new KeepEatException(ErrorCode.INGREDIENT_STORAGE_NOT_FOUND);
        }
        int totalDays = storage.getMax() * storage.getMetric().toDays();
        return purchaseDate.plusDays(totalDays);
    }

    private Long calculateDaysLeft(LocalDate expiryDate) {
        if (expiryDate == null) return null;
        return ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
    }
}