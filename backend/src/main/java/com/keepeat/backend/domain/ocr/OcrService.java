package com.keepeat.backend.domain.ocr;

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
import com.keepeat.backend.domain.ocr.dto.OcrParseResponse.OcrIngredientCandidate;
import com.keepeat.backend.domain.ocr.dto.OcrParseResponse.StorageOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class OcrService {

    /** AI 호출 전체(내부 재시도 포함)의 상한. 기존 OpenAI 직접 호출의 30초 타임아웃과 같다 */
    static final Duration AI_TIMEOUT = Duration.ofSeconds(30);
    private static final ExecutorService AI_CALL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    private static final BeanOutputConverter<OcrAiResponse> OUTPUT_CONVERTER = new BeanOutputConverter<>(OcrAiResponse.class);

    private final ChatClient chatClient;
    private final IngredientRepository ingredientRepository;
    private final IngredientAliasRepository ingredientAliasRepository;
    private final IngredientStorageRepository ingredientStorageRepository;

    public OcrService(@Qualifier("ocrChatClient") ChatClient chatClient,
                      IngredientRepository ingredientRepository,
                      IngredientAliasRepository ingredientAliasRepository,
                      IngredientStorageRepository ingredientStorageRepository) {
        this.chatClient = chatClient;
        this.ingredientRepository = ingredientRepository;
        this.ingredientAliasRepository = ingredientAliasRepository;
        this.ingredientStorageRepository = ingredientStorageRepository;
    }

    record ParsedIngredient(String raw, String name, String customName, Double quantity, String unit) {}

    record OcrAiResponse(List<ParsedIngredient> ingredients) {}

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of("image/jpeg", "image/png");
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    public OcrParseResponse parse(MultipartFile image) {
        validateImage(image);

        byte[] imageBytes;
        try {
            imageBytes = image.getBytes();
        } catch (IOException e) {
            throw new KeepEatException(ErrorCode.OCR_INVALID_IMAGE);
        }

        List<ParsedIngredient> parsed = extractIngredients(imageBytes, image.getContentType());

        List<OcrIngredientCandidate> candidates = parsed.stream()
                .map(this::matchIngredient)
                .toList();

        return new OcrParseResponse(candidates);
    }

    private List<ParsedIngredient> extractIngredients(byte[] imageBytes, String mimeType) {
        String content = callAi(imageBytes, mimeType);
        OcrAiResponse response;
        try {
            response = OUTPUT_CONVERTER.convert(content);
        } catch (Exception e) {
            throw new KeepEatException(ErrorCode.OCR_PARSE_FAILURE, e);
        }
        if (response == null) {
            throw new KeepEatException(ErrorCode.OCR_PARSE_FAILURE);
        }

        List<ParsedIngredient> ingredients = new ArrayList<>();
        if (response.ingredients() == null) {
            return ingredients;
        }
        for (ParsedIngredient item : response.ingredients()) {
            if (item == null) continue;
            String name = trimToNull(item.name());
            if (name == null) continue;
            String raw = item.raw() == null ? "" : item.raw().trim();
            ingredients.add(new ParsedIngredient(raw, name, trimToNull(item.customName()), item.quantity(), trimToNull(item.unit())));
        }
        return ingredients;
    }

    /** 이미지를 넣어 AI를 호출하고 응답 본문을 돌려준다. 호출 실패와 타임아웃은 OCR_API_FAILURE */
    private String callAi(byte[] imageBytes, String mimeType) {
        Future<String> call = AI_CALL_EXECUTOR.submit(() -> chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(u -> u.text(OUTPUT_CONVERTER.getFormat())
                        .media(MimeTypeUtils.parseMimeType(mimeType), new ByteArrayResource(imageBytes)))
                .call()
                .content());
        try {
            String content = call.get(AI_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (content == null || content.isBlank()) {
                throw new KeepEatException(ErrorCode.OCR_PARSE_FAILURE);
            }
            return content;
        } catch (TimeoutException e) {
            call.cancel(true);
            throw new KeepEatException(ErrorCode.OCR_API_FAILURE, e);
        } catch (ExecutionException e) {
            throw new KeepEatException(ErrorCode.OCR_API_FAILURE, e.getCause());
        } catch (InterruptedException e) {
            call.cancel(true);
            Thread.currentThread().interrupt();
            throw new KeepEatException(ErrorCode.OCR_API_FAILURE, e);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void validateImage(MultipartFile image) {
        if (image.isEmpty()) {
            throw new KeepEatException(ErrorCode.OCR_INVALID_IMAGE);
        }

        String contentType = image.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType)) {
            throw new KeepEatException(ErrorCode.OCR_INVALID_IMAGE);
        }

        if (image.getSize() > MAX_FILE_SIZE) {
            throw new KeepEatException(ErrorCode.OCR_INVALID_IMAGE);
        }
    }

    private OcrIngredientCandidate matchIngredient(ParsedIngredient parsed) {
        Optional<Ingredient> master = ingredientRepository.findByName(parsed.name());

        if (master.isEmpty()) {
            List<IngredientAlias> aliases = ingredientAliasRepository.findByAliasNameIn(List.of(parsed.name()));
            if (!aliases.isEmpty()) {
                Ingredient resolved = aliases.get(0).getIngredient();
                log.info("OCR 별칭 매칭 → 마스터 치환: '{}' → '{}'(id={})",
                        parsed.name(), resolved.getName(), resolved.getId());
                master = Optional.of(resolved);
            }
        }

        if (master.isEmpty()) {
            return new OcrIngredientCandidate(
                    parsed.raw(), parsed.customName(), parsed.quantity(), parsed.unit(),
                    MatchStatus.UNMATCHED, null, null, List.of()
            );
        }

        Ingredient ingredient = master.get();
        List<IngredientStorage> storages = ingredientStorageRepository.findAllByIngredientId(ingredient.getId());

        List<StorageOption> storageOptions = storages.stream()
                .map(s -> new StorageOption(
                        s.getStorageType(),
                        s.getMin() * s.getMetric().toDays(),
                        s.getMax() * s.getMetric().toDays()
                ))
                .toList();

        return new OcrIngredientCandidate(
                parsed.raw(), parsed.customName(), parsed.quantity(), parsed.unit(),
                MatchStatus.MATCHED, ingredient.getId(), ingredient.getName(), storageOptions
        );
    }

    private static final String SYSTEM_PROMPT = """
            당신은 식료품 영수증 및 온라인 쇼핑몰 결제내역을 분석하는 파서입니다.
            이미지에서 식재료 정보를 추출하세요.

            규칙:
            - 각 식재료에 대해 다음 정보를 추출:
              - raw: 영수증/내역에 적힌 원본 텍스트 그대로
              - name: 브랜드명, 수량, 단위를 제거한 일반 식재료명 (예: "풀무원 두부 300g" → "두부")
              - customName: 브랜드가 포함된 경우 브랜드+식재료명 (수량/단위 제외). 브랜드가 없으면 null
              - quantity: 수량 (숫자만, 없으면 null)
              - unit: 단위 (g, kg, ml, L, 개 등, 없으면 null)
            - 한국어 식재료명으로 반환
            - 중복 제거
            - 식재료가 아닌 항목 제외 (가격, 매장명, 할인 정보 등)

            JSON 형식:
            {"ingredients": [
              {"raw": "풀무원 두부 300g", "name": "두부", "customName": "풀무원 두부", "quantity": 300, "unit": "g"},
              {"raw": "삼겹살 500g", "name": "삼겹살", "customName": null, "quantity": 500, "unit": "g"},
              {"raw": "서울우유 1000ml", "name": "우유", "customName": "서울우유", "quantity": 1000, "unit": "ml"}
            ]}

            식재료가 없으면: {"ingredients": []}
            """;
}
