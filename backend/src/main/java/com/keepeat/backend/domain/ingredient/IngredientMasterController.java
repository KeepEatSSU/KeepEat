package com.keepeat.backend.domain.ingredient;

import com.keepeat.backend.domain.ingredient.IngredientMasterService.MasterPayload;
import com.keepeat.backend.domain.ingredient.dto.IngredientMasterResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ingredients")
@RequiredArgsConstructor
@Tag(name = "Ingredient Master", description = "마스터 식재료 전체 조회 (프론트 동기화용)")
public class IngredientMasterController {

    private final IngredientMasterService ingredientMasterService;

    @Operation(
            summary = "마스터 식재료 전체 조회",
            description = "카테고리/서브카테고리/식재료/보관옵션을 한 번에 반환합니다. " +
                    "응답에 ETag 헤더가 포함되며 If-None-Match와 일치하면 304를 반환해 본문을 생략합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공, ETag 헤더 포함"),
            @ApiResponse(responseCode = "304", description = "ETag 일치 — 본문 없음")
    })
    @GetMapping("/master")
    public ResponseEntity<IngredientMasterResponse> getMaster(
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch
    ) {
        MasterPayload payload = ingredientMasterService.getMaster();
        String etag = payload.etag();

        if (ifNoneMatch != null && matchesEtag(ifNoneMatch, etag)) {
            return ResponseEntity.status(304).eTag(etag).build();
        }

        return ResponseEntity.ok().eTag(etag).body(payload.body());
    }

    private boolean matchesEtag(String ifNoneMatch, String etag) {
        String stripped = ifNoneMatch.trim();
        if (stripped.startsWith("W/")) {
            stripped = stripped.substring(2).trim();
        }
        if (stripped.startsWith("\"") && stripped.endsWith("\"") && stripped.length() >= 2) {
            stripped = stripped.substring(1, stripped.length() - 1);
        }
        return stripped.equals(etag);
    }
}
