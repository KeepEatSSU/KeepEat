package com.keepeat.backend.domain.ingredient;

import com.keepeat.backend.domain.category.Category;
import com.keepeat.backend.domain.common.enums.Metric;
import com.keepeat.backend.domain.common.enums.StorageType;
import com.keepeat.backend.domain.ingredient.IngredientMasterService.MasterPayload;
import com.keepeat.backend.domain.subcategory.SubCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class IngredientMasterServiceTest {

    @InjectMocks
    private IngredientMasterService ingredientMasterService;

    @Mock
    private IngredientRepository ingredientRepository;

    @Mock
    private IngredientStorageRepository ingredientStorageRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("마스터 조회 — 카테고리/서브/식재료/storage 정상 조립")
    void getMaster_assemblesAllParts() {
        // given
        Category meat = createCategory(1L, "육류");
        SubCategory pork = createSubCategory(10L, meat, "돼지고기");
        Ingredient samgyeopsal = createIngredient(100L, pork, "삼겹살");
        IngredientStorage storage = createStorage(samgyeopsal, StorageType.냉장, 1, 5, Metric.Days);

        given(ingredientRepository.findAllActiveWithSubCategoryAndCategory())
                .willReturn(List.of(samgyeopsal));
        given(ingredientStorageRepository.findAllByIngredientIdIn(anyList()))
                .willReturn(List.of(storage));

        // when
        MasterPayload payload = ingredientMasterService.getMaster();

        // then
        assertThat(payload.body().categories()).hasSize(1);
        assertThat(payload.body().categories().get(0).name()).isEqualTo("육류");
        assertThat(payload.body().subCategories()).hasSize(1);
        assertThat(payload.body().subCategories().get(0).name()).isEqualTo("돼지고기");
        assertThat(payload.body().subCategories().get(0).categoryId()).isEqualTo(1L);
        assertThat(payload.body().ingredients()).hasSize(1);
        assertThat(payload.body().ingredients().get(0).name()).isEqualTo("삼겹살");
        assertThat(payload.body().ingredients().get(0).storageOptions()).hasSize(1);
        assertThat(payload.body().ingredients().get(0).storageOptions().get(0).maxDays()).isEqualTo(5);
        assertThat(payload.etag()).isNotBlank().hasSize(16);
    }

    @Test
    @DisplayName("동일 입력 → 동일 ETag (결정성)")
    void getMaster_etagIsDeterministic() {
        // given
        Category meat = createCategory(1L, "육류");
        SubCategory pork = createSubCategory(10L, meat, "돼지고기");
        Ingredient samgyeopsal = createIngredient(100L, pork, "삼겹살");
        IngredientStorage storage = createStorage(samgyeopsal, StorageType.냉장, 1, 5, Metric.Days);

        given(ingredientRepository.findAllActiveWithSubCategoryAndCategory())
                .willReturn(List.of(samgyeopsal));
        given(ingredientStorageRepository.findAllByIngredientIdIn(anyList()))
                .willReturn(List.of(storage));

        // when
        MasterPayload first = ingredientMasterService.getMaster();
        MasterPayload second = ingredientMasterService.getMaster();

        // then
        assertThat(first.etag()).isEqualTo(second.etag());
    }

    @Test
    @DisplayName("ingredient 0건 — 빈 DTO 정상 반환")
    void getMaster_emptyIngredients() {
        // given
        given(ingredientRepository.findAllActiveWithSubCategoryAndCategory())
                .willReturn(List.of());

        // when
        MasterPayload payload = ingredientMasterService.getMaster();

        // then
        assertThat(payload.body().categories()).isEmpty();
        assertThat(payload.body().subCategories()).isEmpty();
        assertThat(payload.body().ingredients()).isEmpty();
        assertThat(payload.etag()).isNotBlank();
    }

    @Test
    @DisplayName("metric 환산 — Weeks max 2 → 14일")
    void getMaster_storageMetricConverted() {
        // given
        Category meat = createCategory(1L, "육류");
        SubCategory pork = createSubCategory(10L, meat, "돼지고기");
        Ingredient samgyeopsal = createIngredient(100L, pork, "삼겹살");
        IngredientStorage storage = createStorage(samgyeopsal, StorageType.냉동, 1, 2, Metric.Weeks);

        given(ingredientRepository.findAllActiveWithSubCategoryAndCategory())
                .willReturn(List.of(samgyeopsal));
        given(ingredientStorageRepository.findAllByIngredientIdIn(anyList()))
                .willReturn(List.of(storage));

        // when
        MasterPayload payload = ingredientMasterService.getMaster();

        // then
        assertThat(payload.body().ingredients().get(0).storageOptions().get(0).minDays()).isEqualTo(7);
        assertThat(payload.body().ingredients().get(0).storageOptions().get(0).maxDays()).isEqualTo(14);
    }

    private Category createCategory(Long id, String name) {
        Category category = Category.builder().name(name).build();
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    private SubCategory createSubCategory(Long id, Category category, String name) {
        SubCategory subCategory = SubCategory.builder().category(category).name(name).build();
        ReflectionTestUtils.setField(subCategory, "id", id);
        return subCategory;
    }

    private Ingredient createIngredient(Long id, SubCategory subCategory, String name) {
        Ingredient ingredient = Ingredient.builder().subCategory(subCategory).name(name).build();
        ReflectionTestUtils.setField(ingredient, "id", id);
        return ingredient;
    }

    private IngredientStorage createStorage(Ingredient ingredient, StorageType type, int min, int max, Metric metric) {
        return IngredientStorage.builder()
                .ingredient(ingredient)
                .storageType(type)
                .min(min)
                .max(max)
                .metric(metric)
                .build();
    }
}
