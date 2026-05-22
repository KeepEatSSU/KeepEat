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
import com.keepeat.backend.domain.recipe.repository.RecipeRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

    @Mock
    private RecipeRepository recipeRepository;

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

    @Test
    @DisplayName("커스텀 식재료 등록 — ingredientId=null, 마스터 조회 호출 0회")
    void create_customIngredient_success() {
        // given
        LocalDate purchaseDate = LocalDate.of(2026, 5, 19);
        LocalDate expiryDate = LocalDate.of(2026, 6, 30);

        given(userIngredientRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));

        UserIngredientCreateRequest request = new UserIngredientCreateRequest(
                null, StorageType.냉장, purchaseDate, expiryDate, 1.0, "병", "수제 딸기잼"
        );

        // when
        List<UserIngredientResponse> result = userIngredientService.create(USER_ID, List.of(request));

        // then
        assertThat(result).hasSize(1);
        UserIngredientResponse response = result.get(0);
        assertThat(response.ingredientId()).isNull();
        assertThat(response.name()).isNull();
        assertThat(response.categoryName()).isNull();
        assertThat(response.subCategoryName()).isNull();
        assertThat(response.customName()).isEqualTo("수제 딸기잼");
        assertThat(response.isCustom()).isTrue();
        assertThat(response.expiryDate()).isEqualTo(expiryDate);

        verify(ingredientRepository, never()).findById(any());
        verify(ingredientStorageRepository, never()).findByIngredientIdAndStorageType(any(), any());
    }

    @Test
    @DisplayName("커스텀과 마스터 식재료 혼합 등록")
    void create_customAndMasterMixed() {
        // given
        Ingredient ingredient = createIngredient(INGREDIENT_ID, "삼겹살");
        IngredientStorage storage = createStorage(ingredient, StorageType.냉장, 5, Metric.Days);
        LocalDate purchaseDate = LocalDate.of(2026, 5, 19);

        given(ingredientRepository.findById(INGREDIENT_ID)).willReturn(Optional.of(ingredient));
        given(ingredientStorageRepository.findByIngredientIdAndStorageType(INGREDIENT_ID, StorageType.냉장))
                .willReturn(Optional.of(storage));
        given(userIngredientRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));

        List<UserIngredientCreateRequest> requests = List.of(
                new UserIngredientCreateRequest(INGREDIENT_ID, StorageType.냉장, purchaseDate, null, 500.0, "g", null),
                new UserIngredientCreateRequest(null, StorageType.냉장, purchaseDate, LocalDate.of(2026, 6, 30), 1.0, "병", "수제 딸기잼")
        );

        // when
        List<UserIngredientResponse> result = userIngredientService.create(USER_ID, requests);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).isCustom()).isFalse();
        assertThat(result.get(0).name()).isEqualTo("삼겹살");
        assertThat(result.get(1).isCustom()).isTrue();
        assertThat(result.get(1).customName()).isEqualTo("수제 딸기잼");
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

    // ---------- findByRecipe ----------

    @Test
    @DisplayName("findByRecipe — 존재하지 않는 레시피 → RECIPE_NOT_FOUND")
    void findByRecipe_recipeNotFound() {
        given(recipeRepository.existsById(99L)).willReturn(false);

        assertThatThrownBy(() -> userIngredientService.findByRecipe(USER_ID, 99L))
                .isInstanceOf(KeepEatException.class)
                .satisfies(e -> assertThat(((KeepEatException) e).getErrorCode())
                        .isEqualTo(ErrorCode.RECIPE_NOT_FOUND));
    }

    @Test
    @DisplayName("findByRecipe — 같은 ingredient_id 행이 2개면 2개 모두 반환 (중복 보존)")
    void findByRecipe_duplicatesPreserved() {
        // given
        Long recipeId = 7L;
        Ingredient onion = createIngredient(10L, "양파");
        UserIngredient ui1 = UserIngredient.builder()
                .userId(USER_ID).ingredient(onion).storageType(StorageType.냉장)
                .purchaseDate(LocalDate.now()).expiryDate(LocalDate.now().plusDays(3))
                .quantity(1.0).unit("개").build();
        UserIngredient ui2 = UserIngredient.builder()
                .userId(USER_ID).ingredient(onion).storageType(StorageType.냉장)
                .purchaseDate(LocalDate.now()).expiryDate(LocalDate.now().plusDays(5))
                .quantity(1.0).unit("개").build();
        ReflectionTestUtils.setField(ui1, "id", 100L);
        ReflectionTestUtils.setField(ui2, "id", 101L);

        given(recipeRepository.existsById(recipeId)).willReturn(true);
        given(userIngredientRepository.findAllByUserIdAndRecipeId(USER_ID, recipeId))
                .willReturn(List.of(ui1, ui2));

        // when
        List<UserIngredientResponse> result = userIngredientService.findByRecipe(USER_ID, recipeId);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(r -> r.ingredientId().equals(10L));
        assertThat(result).extracting(UserIngredientResponse::id).containsExactly(100L, 101L);
    }

    @Test
    @DisplayName("findByRecipe — repository가 빈 결과 반환 시 빈 리스트 반환")
    void findByRecipe_emptyResult() {
        Long recipeId = 7L;
        given(recipeRepository.existsById(recipeId)).willReturn(true);
        given(userIngredientRepository.findAllByUserIdAndRecipeId(USER_ID, recipeId))
                .willReturn(List.of());

        List<UserIngredientResponse> result = userIngredientService.findByRecipe(USER_ID, recipeId);

        assertThat(result).isEmpty();
    }

    // ---------- deleteMany ----------

    @Test
    @DisplayName("deleteMany — 본인 소유 ids만 → 정상 삭제")
    void deleteMany_allOwned_success() {
        List<Long> ids = List.of(100L, 101L, 102L);
        given(userIngredientRepository.countByIdInAndUserId(ids, USER_ID)).willReturn(3L);

        userIngredientService.deleteMany(USER_ID, ids);

        verify(userIngredientRepository).deleteAllByIdInAndUserId(ids, USER_ID);
    }

    @Test
    @DisplayName("deleteMany — 타인 소유 또는 존재하지 않는 id 포함 시 USER_INGREDIENT_NOT_FOUND, 삭제 호출 0회")
    void deleteMany_someNotOwned_throwsAndNoDelete() {
        List<Long> ids = List.of(100L, 200L);
        given(userIngredientRepository.countByIdInAndUserId(ids, USER_ID)).willReturn(1L);

        assertThatThrownBy(() -> userIngredientService.deleteMany(USER_ID, ids))
                .isInstanceOf(KeepEatException.class)
                .satisfies(e -> assertThat(((KeepEatException) e).getErrorCode())
                        .isEqualTo(ErrorCode.USER_INGREDIENT_NOT_FOUND));

        verify(userIngredientRepository, never()).deleteAllByIdInAndUserId(anyList(), eq(USER_ID));
    }

    @Test
    @DisplayName("deleteMany — 매칭 0개 (모두 존재하지 않거나 타인 소유) → USER_INGREDIENT_NOT_FOUND")
    void deleteMany_noneOwned_throws() {
        List<Long> ids = List.of(999L);
        given(userIngredientRepository.countByIdInAndUserId(ids, USER_ID)).willReturn(0L);

        assertThatThrownBy(() -> userIngredientService.deleteMany(USER_ID, ids))
                .isInstanceOf(KeepEatException.class)
                .satisfies(e -> assertThat(((KeepEatException) e).getErrorCode())
                        .isEqualTo(ErrorCode.USER_INGREDIENT_NOT_FOUND));

        verify(userIngredientRepository, never()).deleteAllByIdInAndUserId(anyList(), eq(USER_ID));
    }
}
