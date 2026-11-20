package com.keepeat.backend.domain.ocr;

import com.keepeat.backend.domain.category.Category;
import com.keepeat.backend.domain.common.enums.Metric;
import com.keepeat.backend.domain.common.enums.StorageType;
import com.keepeat.backend.domain.common.exception.ErrorCode;
import com.keepeat.backend.domain.common.exception.KeepEatException;
import com.keepeat.backend.domain.ingredient.Ingredient;
import com.keepeat.backend.domain.ingredient.IngredientAlias;
import com.keepeat.backend.domain.ingredient.IngredientAliasRepository;
import com.keepeat.backend.domain.ingredient.IngredientRepository;
import com.keepeat.backend.domain.ingredient.IngredientStorage;
import com.keepeat.backend.domain.ingredient.IngredientStorageRepository;
import com.keepeat.backend.domain.ocr.dto.OcrParseResponse;
import com.keepeat.backend.domain.ocr.dto.OcrParseResponse.MatchStatus;
import com.keepeat.backend.domain.subcategory.SubCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OcrServiceTest {

    private OcrService ocrService;

    /** 공급자 구현 대신 Spring AI ChatModel을 mock으로 둔다. 실제 AI 호출은 하지 않는다 */
    @Mock
    private ChatModel chatModel;

    @Mock
    private IngredientRepository ingredientRepository;

    @Mock
    private IngredientAliasRepository ingredientAliasRepository;

    @Mock
    private IngredientStorageRepository ingredientStorageRepository;

    @BeforeEach
    void setUp() {
        ocrService = new OcrService(ChatClient.builder(chatModel).build(),
                ingredientRepository, ingredientAliasRepository, ingredientStorageRepository);
    }

    @Test
    @DisplayName("유효한 이미지 → 매칭된 식재료 후보 반환")
    void parse_validImage_matchedCandidates() {
        // given
        MockMultipartFile image = createMockImage();
        Ingredient samgyeopsal = createIngredient(25L, "삼겹살");

        givenAiReturns("""
                {"ingredients": [
                  {"raw": "삼겹살 500g", "name": "삼겹살", "customName": null, "quantity": 500, "unit": "g"}
                ]}
                """);
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

        givenAiReturns("""
                {"ingredients": [
                  {"raw": "풀무원 알수없는식재료", "name": "알수없는식재료", "customName": "풀무원 알수없는식재료", "quantity": null, "unit": null}
                ]}
                """);
        given(ingredientRepository.findByName("알수없는식재료"))
                .willReturn(Optional.empty());
        given(ingredientAliasRepository.findByAliasNameIn(List.of("알수없는식재료")))
                .willReturn(List.of());

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
    @DisplayName("마스터 직매칭 실패 → 별칭 매칭 성공 → 마스터 식재료로 치환되어 MATCHED 반환")
    void parse_aliasMatch_resolvedToMaster() {
        // given
        MockMultipartFile image = createMockImage();
        Ingredient beef = createIngredient(30L, "소고기");
        IngredientAlias alias = createAlias(beef, "우육");

        givenAiReturns("""
                {"ingredients": [
                  {"raw": "우육 500g", "name": "우육", "customName": null, "quantity": 500, "unit": "g"}
                ]}
                """);
        given(ingredientRepository.findByName("우육"))
                .willReturn(Optional.empty());
        given(ingredientAliasRepository.findByAliasNameIn(List.of("우육")))
                .willReturn(List.of(alias));
        given(ingredientStorageRepository.findAllByIngredientId(30L))
                .willReturn(List.of(
                        createStorage(beef, StorageType.냉장, 2, 3, Metric.Days)
                ));

        // when
        OcrParseResponse response = ocrService.parse(image);

        // then
        assertThat(response.candidates()).hasSize(1);
        var candidate = response.candidates().get(0);
        assertThat(candidate.matchStatus()).isEqualTo(MatchStatus.MATCHED);
        // 별칭이 가리키는 마스터 식재료로 치환되어야 함
        assertThat(candidate.ingredientId()).isEqualTo(30L);
        assertThat(candidate.ingredientName()).isEqualTo("소고기");
        assertThat(candidate.storageOptions()).hasSize(1);
        assertThat(candidate.storageOptions().get(0).storageType()).isEqualTo(StorageType.냉장);
    }

    @Test
    @DisplayName("마스터에도 별칭에도 없음 → UNMATCHED 회귀 방지")
    void parse_neitherMasterNorAlias_unmatched() {
        // given
        MockMultipartFile image = createMockImage();

        givenAiReturns("""
                {"ingredients": [
                  {"raw": "외계식재료", "name": "외계식재료", "customName": null, "quantity": null, "unit": null}
                ]}
                """);
        given(ingredientRepository.findByName("외계식재료"))
                .willReturn(Optional.empty());
        given(ingredientAliasRepository.findByAliasNameIn(List.of("외계식재료")))
                .willReturn(List.of());

        // when
        OcrParseResponse response = ocrService.parse(image);

        // then
        assertThat(response.candidates()).hasSize(1);
        assertThat(response.candidates().get(0).matchStatus()).isEqualTo(MatchStatus.UNMATCHED);
        assertThat(response.candidates().get(0).ingredientId()).isNull();
    }

    @Test
    @DisplayName("마스터 직매칭과 별칭 매칭 모두 동일한 마스터 ingredientId 반환")
    void parse_masterAndAlias_returnSameIngredientId() {
        // given
        MockMultipartFile image = createMockImage();
        Ingredient beef = createIngredient(30L, "소고기");
        IngredientAlias alias = createAlias(beef, "우육");

        givenAiReturns("""
                {"ingredients": [
                  {"raw": "소고기 200g", "name": "소고기", "customName": null, "quantity": 200, "unit": "g"},
                  {"raw": "우육 300g", "name": "우육", "customName": null, "quantity": 300, "unit": "g"}
                ]}
                """);
        given(ingredientRepository.findByName("소고기"))
                .willReturn(Optional.of(beef));
        given(ingredientRepository.findByName("우육"))
                .willReturn(Optional.empty());
        given(ingredientAliasRepository.findByAliasNameIn(List.of("우육")))
                .willReturn(List.of(alias));
        given(ingredientStorageRepository.findAllByIngredientId(30L))
                .willReturn(List.of(createStorage(beef, StorageType.냉장, 2, 3, Metric.Days)));

        // when
        OcrParseResponse response = ocrService.parse(image);

        // then
        assertThat(response.candidates()).hasSize(2);
        assertThat(response.candidates().get(0).ingredientId()).isEqualTo(30L);
        assertThat(response.candidates().get(1).ingredientId()).isEqualTo(30L);
        assertThat(response.candidates().get(0).ingredientName()).isEqualTo("소고기");
        assertThat(response.candidates().get(1).ingredientName()).isEqualTo("소고기");
    }

    @Test
    @DisplayName("AI API 실패 → OCR_API_FAILURE 예외")
    void parse_apiFailure_throwsException() {
        // given
        MockMultipartFile image = createMockImage();

        given(chatModel.call(any(Prompt.class)))
                .willThrow(new NonTransientAiException("401 Unauthorized"));

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

        givenAiReturns("{\"ingredients\": []}");

        // when
        OcrParseResponse response = ocrService.parse(image);

        // then
        assertThat(response.candidates()).isEmpty();
    }

    @Test
    @DisplayName("AI 요청 → 기존 시스템 프롬프트와 이미지(Media)가 함께 전달된다")
    void parse_sendsSystemPromptAndImageMedia() {
        // given
        MockMultipartFile image = createMockImage();
        givenAiReturns("{\"ingredients\": []}");

        // when
        ocrService.parse(image);

        // then
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        Prompt prompt = captor.getValue();
        assertThat(prompt.getSystemMessage().getText())
                .contains("식료품 영수증 및 온라인 쇼핑몰 결제내역을 분석하는 파서입니다.");
        UserMessage userMessage = prompt.getUserMessage();
        assertThat(userMessage.getMedia()).hasSize(1);
        assertThat(userMessage.getMedia().get(0).getMimeType().toString()).isEqualTo("image/jpeg");
        assertThat(userMessage.getMedia().get(0).getDataAsByteArray()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("코드 블록으로 감싼 응답 → 파싱 후 정리된다 (빈 이름 제외, 빈 문자열은 null)")
    void parse_fencedResponse_normalized() {
        // given
        MockMultipartFile image = createMockImage();
        givenAiReturns("""
                ```json
                {"ingredients": [
                  {"raw": " 서울우유 1000ml ", "name": " 우유 ", "customName": " ", "quantity": 1000, "unit": ""},
                  {"raw": "할인", "name": "", "customName": null, "quantity": null, "unit": null}
                ]}
                ```
                """);
        given(ingredientRepository.findByName("우유")).willReturn(Optional.empty());
        given(ingredientAliasRepository.findByAliasNameIn(List.of("우유"))).willReturn(List.of());

        // when
        OcrParseResponse response = ocrService.parse(image);

        // then
        assertThat(response.candidates()).hasSize(1);
        var candidate = response.candidates().get(0);
        assertThat(candidate.rawName()).isEqualTo("서울우유 1000ml");
        assertThat(candidate.customName()).isNull();
        assertThat(candidate.quantity()).isEqualTo(1000.0);
        assertThat(candidate.unit()).isNull();
    }

    @Test
    @DisplayName("JSON이 아닌 응답 → OCR_PARSE_FAILURE 예외")
    void parse_invalidJson_throwsParseFailure() {
        // given
        MockMultipartFile image = createMockImage();
        givenAiReturns("식재료를 찾을 수 없습니다.");

        // when & then
        assertThatThrownBy(() -> ocrService.parse(image))
                .isInstanceOf(KeepEatException.class)
                .satisfies(e -> assertThat(((KeepEatException) e).getErrorCode())
                        .isEqualTo(ErrorCode.OCR_PARSE_FAILURE));
    }

    @Test
    @DisplayName("허용되지 않은 이미지 형식 → AI 호출 없이 OCR_INVALID_IMAGE 예외")
    void parse_invalidMimeType_throwsInvalidImage() {
        MockMultipartFile image = new MockMultipartFile("image", "a.gif", "image/gif", new byte[]{1});

        assertThatThrownBy(() -> ocrService.parse(image))
                .isInstanceOf(KeepEatException.class)
                .satisfies(e -> assertThat(((KeepEatException) e).getErrorCode())
                        .isEqualTo(ErrorCode.OCR_INVALID_IMAGE));
        verify(chatModel, never()).call(any(Prompt.class));
    }

    private void givenAiReturns(String content) {
        given(chatModel.call(any(Prompt.class)))
                .willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(content)))));
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

    private IngredientAlias createAlias(Ingredient ingredient, String aliasName) {
        return new IngredientAlias(ingredient, aliasName);
    }
}
