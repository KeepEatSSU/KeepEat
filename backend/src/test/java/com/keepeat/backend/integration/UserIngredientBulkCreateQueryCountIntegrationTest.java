package com.keepeat.backend.integration;

import com.keepeat.backend.domain.category.Category;
import com.keepeat.backend.domain.category.CategoryRepository;
import com.keepeat.backend.domain.common.enums.Metric;
import com.keepeat.backend.domain.common.enums.StorageType;
import com.keepeat.backend.domain.ingredient.Ingredient;
import com.keepeat.backend.domain.ingredient.IngredientRepository;
import com.keepeat.backend.domain.ingredient.IngredientStorage;
import com.keepeat.backend.domain.ingredient.IngredientStorageRepository;
import com.keepeat.backend.domain.subcategory.SubCategory;
import com.keepeat.backend.domain.subcategory.SubCategoryRepository;
import com.keepeat.backend.domain.useringredient.UserIngredientService;
import com.keepeat.backend.domain.useringredient.dto.UserIngredientCreateRequest;
import com.keepeat.backend.domain.useringredient.dto.UserIngredientResponse;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserIngredientBulkCreateQueryCountIntegrationTest {

    @Autowired
    private UserIngredientService userIngredientService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private SubCategoryRepository subCategoryRepository;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private IngredientStorageRepository ingredientStorageRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("하위 분류가 서로 다른 식재료를 일괄 등록해도 조회는 식재료 1번, 보관 정보 1번이고 지연 로딩이 없다")
    void bulkCreateRunsConstantSelects() {
        LocalDate purchaseDate = LocalDate.of(2026, 11, 25);
        List<UserIngredientCreateRequest> requests = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Category category = categoryRepository.save(Category.builder().name("카테고리" + i).build());
            SubCategory sub = subCategoryRepository.save(SubCategory.builder().category(category).name("하위" + i).build());
            Ingredient ingredient = ingredientRepository.save(Ingredient.builder().subCategory(sub).name("재료" + i).build());
            ingredientStorageRepository.save(IngredientStorage.builder()
                    .ingredient(ingredient).storageType(StorageType.냉장).min(1).max(i + 1).metric(Metric.Days).build());
            requests.add(new UserIngredientCreateRequest(
                    ingredient.getId(), StorageType.냉장, purchaseDate, null, 1.0, "개", null));
        }
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        List<UserIngredientResponse> result = userIngredientService.create(1L, requests);
        entityManager.flush();

        long statements = statistics.getPrepareStatementCount();
        long fetches = statistics.getEntityFetchCount();
        statistics.setStatisticsEnabled(false);
        assertThat(result).extracting(UserIngredientResponse::subCategoryName)
                .containsExactly("하위0", "하위1", "하위2", "하위3");
        assertThat(fetches).isZero();
        assertThat(statements).isEqualTo(2 + requests.size());
    }
}
