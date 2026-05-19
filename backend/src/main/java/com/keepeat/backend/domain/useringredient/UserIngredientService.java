package com.keepeat.backend.domain.useringredient;

import com.keepeat.backend.domain.common.enums.IngredientStatus;
import com.keepeat.backend.domain.common.enums.StorageType;
import com.keepeat.backend.domain.common.exception.ErrorCode;
import com.keepeat.backend.domain.common.exception.KeepEatException;
import com.keepeat.backend.domain.ingredient.Ingredient;
import com.keepeat.backend.domain.ingredient.IngredientRepository;
import com.keepeat.backend.domain.ingredient.IngredientStorage;
import com.keepeat.backend.domain.ingredient.IngredientStorageRepository;
import com.keepeat.backend.domain.useringredient.dto.UserIngredientCreateRequest;
import com.keepeat.backend.domain.useringredient.dto.UserIngredientRequest;
import com.keepeat.backend.domain.useringredient.dto.UserIngredientResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserIngredientService {

    private final UserIngredientRepository userIngredientRepository;
    private final IngredientRepository ingredientRepository;
    private final IngredientStorageRepository ingredientStorageRepository;

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

    @Transactional
    public UserIngredientResponse update(Long userId, Long id, UserIngredientRequest request) {
        UserIngredient ui = userIngredientRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new KeepEatException(ErrorCode.USER_INGREDIENT_NOT_FOUND));

        ui.update(request);

        return UserIngredientResponse.of(ui, calculateDaysLeft(ui.getExpiryDate()));
    }

    @Transactional
    public List<UserIngredientResponse> create(Long userId, List<UserIngredientCreateRequest> items) {
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

            Ingredient ingredient = ingredientRepository.findById(item.ingredientId())
                    .orElseThrow(() -> new KeepEatException(ErrorCode.INGREDIENT_NOT_FOUND));

            // PENDING 상태의 식재료는 식재료 등록 대상이 아님
            if(ingredient.getStatus() != IngredientStatus.ACTIVE){
                throw new KeepEatException(ErrorCode.INGREDIENT_NOT_FOUND);
            }

            LocalDate expiryDate = item.expiryDate();
            if (expiryDate == null) {
                expiryDate = calculateExpiryDate(ingredient.getId(), item.storageType(), item.purchaseDate());
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

    private LocalDate calculateExpiryDate(Long ingredientId, StorageType storageType, LocalDate purchaseDate) {
        IngredientStorage storage = ingredientStorageRepository
                .findByIngredientIdAndStorageType(ingredientId, storageType)
                .orElseThrow(() -> new KeepEatException(ErrorCode.INGREDIENT_STORAGE_NOT_FOUND));

        int totalDays = storage.getMax() * storage.getMetric().toDays();
        return purchaseDate.plusDays(totalDays);
    }

    private Long calculateDaysLeft(LocalDate expiryDate) {
        if (expiryDate == null) return null;
        return ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
    }
}