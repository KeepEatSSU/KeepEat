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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcrService {

    private final OpenAiClient openAiClient;
    private final IngredientRepository ingredientRepository;
    private final IngredientAliasRepository ingredientAliasRepository;
    private final IngredientStorageRepository ingredientStorageRepository;

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

        List<OpenAiClient.ParsedIngredient> parsed = openAiClient.extractIngredients(imageBytes, image.getContentType());

        List<OcrIngredientCandidate> candidates = parsed.stream()
                .map(this::matchIngredient)
                .toList();

        return new OcrParseResponse(candidates);
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

    private OcrIngredientCandidate matchIngredient(OpenAiClient.ParsedIngredient parsed) {
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
}
