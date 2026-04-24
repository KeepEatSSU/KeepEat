package com.keepeat.backend.domain.ocr;

import com.keepeat.backend.domain.category.Category;
import com.keepeat.backend.domain.common.enums.Metric;
import com.keepeat.backend.domain.common.enums.StorageType;
import com.keepeat.backend.domain.common.exception.ErrorCode;
import com.keepeat.backend.domain.common.exception.KeepEatException;
import com.keepeat.backend.domain.ingredient.Ingredient;
import com.keepeat.backend.domain.ingredient.IngredientRepository;
import com.keepeat.backend.domain.ingredient.IngredientStorage;
import com.keepeat.backend.domain.ingredient.IngredientStorageRepository;
import com.keepeat.backend.domain.ocr.dto.OcrParseResponse;
import com.keepeat.backend.domain.ocr.dto.OcrParseResponse.MatchStatus;
import com.keepeat.backend.domain.subcategory.SubCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OcrServiceTest {

    @InjectMocks
    private OcrService ocrService;

    @Mock
    private OpenAiClient openAiClient;

    @Mock
    private IngredientRepository ingredientRepository;

    @Mock
    private IngredientStorageRepository ingredientStorageRepository;

    @Test
    @DisplayName("유효한 이미지 → 매칭된 식재료 후보 반환")
    void parse_validImage_matchedCandidates() {
        // given
        MockMultipartFile image = createMockImage();
        Ingredient samgyeopsal = createIngredient(25L, "삼겹살");

        given(openAiClient.extractIngredients(any(byte[].class), anyString()))
                .willReturn(List.of(
                        new OpenAiClient.ParsedIngredient("삼겹살 500g", "삼겹살", null, 500.0, "g")
                ));
        given(ingredientRepository.findByName("삼겹살"))
                .willReturn(Optional.of(samgyeopsal));
        given(ingredientStorageRepository.findAllByIngredientId(25L))
                .willReturn(List.of(
                        createStorage(samgyeopsal, StorageType.냉장, 3, 5, Metric.Days),
                        createStorage(samgyeopsal, StorageType.냉동, 4, 6, Metric.Months)
                ));

        // when
        OcrParseResponse response = ocrService.parse(image);

        // then
        assertThat(response.candidates()).hasSize(1);
        var candidate = response.candidates().get(0);
        assertThat(candidate.matchStatus()).isEqualTo(MatchStatus.MATCHED);
        assertThat(candidate.ingredientId()).isEqualTo(25L);
        assertThat(candidate.ingredientName()).isEqualTo("삼겹살");
        assertThat(candidate.rawName()).isEqualTo("삼겹살 500g");
        assertThat(candidate.customName()).isNull();
        assertThat(candidate.quantity()).isEqualTo(500.0);
        assertThat(candidate.unit()).isEqualTo("g");
        assertThat(candidate.storageOptions()).hasSize(2);
        assertThat(candidate.storageOptions().get(0).minDays()).isEqualTo(3);
        assertThat(candidate.storageOptions().get(0).maxDays()).isEqualTo(5);
    }

    @Test
    @DisplayName("매칭되지 않는 식재료 → UNMATCHED 반환")
    void parse_noMatch_unmatchedStatus() {
        // given
        MockMultipartFile image = createMockImage();

        given(openAiClient.extractIngredients(any(byte[].class), anyString()))
                .willReturn(List.of(
                        new OpenAiClient.ParsedIngredient("풀무원 알수없는식재료", "알수없는식재료", "풀무원 알수없는식재료", null, null)
                ));
        given(ingredientRepository.findByName("알수없는식재료"))
                .willReturn(Optional.empty());

        // when
        OcrParseResponse response = ocrService.parse(image);

        // then
        assertThat(response.candidates()).hasSize(1);
        var candidate = response.candidates().get(0);
        assertThat(candidate.matchStatus()).isEqualTo(MatchStatus.UNMATCHED);
        assertThat(candidate.ingredientId()).isNull();
        assertThat(candidate.rawName()).isEqualTo("풀무원 알수없는식재료");
        assertThat(candidate.customName()).isEqualTo("풀무원 알수없는식재료");
        assertThat(candidate.storageOptions()).isEmpty();
    }

    @Test
    @DisplayName("OpenAI API 실패 → OCR_API_FAILURE 예외")
    void parse_apiFailure_throwsException() {
        // given
        MockMultipartFile image = createMockImage();

        given(openAiClient.extractIngredients(any(byte[].class), anyString()))
                .willThrow(new KeepEatException(ErrorCode.OCR_API_FAILURE));

        // when & then
        assertThatThrownBy(() -> ocrService.parse(image))
                .isInstanceOf(KeepEatException.class)
                .satisfies(e -> assertThat(((KeepEatException) e).getErrorCode())
                        .isEqualTo(ErrorCode.OCR_API_FAILURE));
    }

    @Test
    @DisplayName("추출 결과 없음 → 빈 후보 목록 반환")
    void parse_emptyResult_returnsEmptyList() {
        // given
        MockMultipartFile image = createMockImage();

        given(openAiClient.extractIngredients(any(byte[].class), anyString()))
                .willReturn(List.<OpenAiClient.ParsedIngredient>of());

        // when
        OcrParseResponse response = ocrService.parse(image);

        // then
        assertThat(response.candidates()).isEmpty();
    }

    private MockMultipartFile createMockImage() {
        return new MockMultipartFile(
                "image", "receipt.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );
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

    private IngredientStorage createStorage(Ingredient ingredient, StorageType storageType,
                                            int min, int max, Metric metric) {
        return IngredientStorage.builder()
                .ingredient(ingredient)
                .storageType(storageType)
                .min(min)
                .max(max)
                .metric(metric)
                .build();
    }
}
