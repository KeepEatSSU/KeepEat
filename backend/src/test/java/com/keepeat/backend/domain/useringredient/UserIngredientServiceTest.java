package com.keepeat.backend.domain.useringredient;

import com.keepeat.backend.domain.category.Category;
import com.keepeat.backend.domain.common.enums.Metric;
import com.keepeat.backend.domain.common.enums.StorageType;
import com.keepeat.backend.domain.common.exception.ErrorCode;
import com.keepeat.backend.domain.common.exception.KeepEatException;
import com.keepeat.backend.domain.ingredient.Ingredient;
import com.keepeat.backend.domain.ingredient.IngredientRepository;
import com.keepeat.backend.domain.ingredient.IngredientStorage;
import com.keepeat.backend.domain.ingredient.IngredientStorageRepository;
import com.keepeat.backend.domain.subcategory.SubCategory;
import com.keepeat.backend.domain.useringredient.dto.UserIngredientCreateRequest;
import com.keepeat.backend.domain.useringredient.dto.UserIngredientResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserIngredientServiceTest {

    @InjectMocks
    private UserIngredientService userIngredientService;

    @Mock
    private UserIngredientRepository userIngredientRepository;

    @Mock
    private IngredientRepository ingredientRepository;

    @Mock
    private IngredientStorageRepository ingredientStorageRepository;

    private static final Long USER_ID = 1L;
    private static final Long INGREDIENT_ID = 25L;

    @Test
    @DisplayName("단건 등록 — expiryDate 자동 계산")
    void create_singleItem_success() {
        // given
        Ingredient ingredient = createIngredient(INGREDIENT_ID, "삼겹살");
        IngredientStorage storage = createStorage(ingredient, StorageType.냉장, 5, Metric.Days);
        LocalDate purchaseDate = LocalDate.of(2026, 3, 31);

        given(ingredientRepository.findById(INGREDIENT_ID)).willReturn(Optional.of(ingredient));
        given(ingredientStorageRepository.findByIngredientIdAndStorageType(INGREDIENT_ID, StorageType.냉장))
                .willReturn(Optional.of(storage));
        given(userIngredientRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));

        UserIngredientCreateRequest request = new UserIngredientCreateRequest(
                INGREDIENT_ID, StorageType.냉장, purchaseDate, null, 500.0, "g", null
        );

        // when
        List<UserIngredientResponse> result = userIngredientService.create(USER_ID, List.of(request));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).expiryDate()).isEqualTo(purchaseDate.plusDays(5));
        assertThat(result.get(0).storageType()).isEqualTo(StorageType.냉장);
    }

    @Test
    @DisplayName("벌크 등록 — 복수 건 일괄 등록")
    void create_bulkItems_success() {
        // given
        Ingredient samgyeopsal = createIngredient(INGREDIENT_ID, "삼겹살");
        Ingredient milk = createIngredient(26L, "우유");
        IngredientStorage samgyeopsalStorage = createStorage(samgyeopsal, StorageType.냉장, 5, Metric.Days);
        IngredientStorage milkStorage = createStorage(milk, StorageType.냉장, 10, Metric.Days);
        LocalDate purchaseDate = LocalDate.of(2026, 3, 31);

        given(ingredientRepository.findById(INGREDIENT_ID)).willReturn(Optional.of(samgyeopsal));
        given(ingredientRepository.findById(26L)).willReturn(Optional.of(milk));
        given(ingredientStorageRepository.findByIngredientIdAndStorageType(INGREDIENT_ID, StorageType.냉장))
                .willReturn(Optional.of(samgyeopsalStorage));
        given(ingredientStorageRepository.findByIngredientIdAndStorageType(26L, StorageType.냉장))
                .willReturn(Optional.of(milkStorage));
        given(userIngredientRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));

        List<UserIngredientCreateRequest> requests = List.of(
                new UserIngredientCreateRequest(INGREDIENT_ID, StorageType.냉장, purchaseDate, null, 500.0, "g", null),
                new UserIngredientCreateRequest(26L, StorageType.냉장, purchaseDate, null, 1000.0, "ml", null)
        );

        // when
        List<UserIngredientResponse> result = userIngredientService.create(USER_ID, requests);

        // then
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("expiryDate 직접 지정 시 자동 계산 스킵")
    void create_withExplicitExpiryDate() {
        // given
        Ingredient ingredient = createIngredient(INGREDIENT_ID, "삼겹살");
        LocalDate purchaseDate = LocalDate.of(2026, 3, 31);
        LocalDate customExpiry = LocalDate.of(2026, 4, 15);

        given(ingredientRepository.findById(INGREDIENT_ID)).willReturn(Optional.of(ingredient));
        given(userIngredientRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));

        UserIngredientCreateRequest request = new UserIngredientCreateRequest(
                INGREDIENT_ID, StorageType.냉장, purchaseDate, customExpiry, 500.0, "g", null
        );

        // when
        List<UserIngredientResponse> result = userIngredientService.create(USER_ID, List.of(request));

        // then
        assertThat(result.get(0).expiryDate()).isEqualTo(customExpiry);
    }

    @Test
    @DisplayName("존재하지 않는 ingredientId — INGREDIENT_NOT_FOUND")
    void create_ingredientNotFound() {
        // given
        given(ingredientRepository.findById(999L)).willReturn(Optional.empty());

        UserIngredientCreateRequest request = new UserIngredientCreateRequest(
                999L, StorageType.냉장, LocalDate.now(), null, 500.0, "g", null
        );

        // when & then
        assertThatThrownBy(() -> userIngredientService.create(USER_ID, List.of(request)))
                .isInstanceOf(KeepEatException.class)
                .satisfies(e -> assertThat(((KeepEatException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INGREDIENT_NOT_FOUND));
    }

    @Test
    @DisplayName("보관 정보 없음 — INGREDIENT_STORAGE_NOT_FOUND")
    void create_storageNotFound() {
        // given
        Ingredient ingredient = createIngredient(INGREDIENT_ID, "삼겹살");

        given(ingredientRepository.findById(INGREDIENT_ID)).willReturn(Optional.of(ingredient));
        given(ingredientStorageRepository.findByIngredientIdAndStorageType(INGREDIENT_ID, StorageType.실온))
                .willReturn(Optional.empty());

        UserIngredientCreateRequest request = new UserIngredientCreateRequest(
                INGREDIENT_ID, StorageType.실온, LocalDate.now(), null, 500.0, "g", null
        );

        // when & then
        assertThatThrownBy(() -> userIngredientService.create(USER_ID, List.of(request)))
                .isInstanceOf(KeepEatException.class)
                .satisfies(e -> assertThat(((KeepEatException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INGREDIENT_STORAGE_NOT_FOUND));
    }

    private Ingredient createIngredient(Long id, String name) {
        Category category = Category.builder().name("육류").build();
        SubCategory subCategory = SubCategory.builder().category(category).name("돼지고기").build();
        Ingredient ingredient = Ingredient.builder()
                .subCategory(subCategory)
                .name(name)
                .build();
        ReflectionTestUtils.setField(ingredient, "id", id);
        return ingredient;
    }

    private IngredientStorage createStorage(Ingredient ingredient, StorageType storageType, int max, Metric metric) {
        return IngredientStorage.builder()
                .ingredient(ingredient)
                .storageType(storageType)
                .min(1)
                .max(max)
                .metric(metric)
                .build();
    }
}
