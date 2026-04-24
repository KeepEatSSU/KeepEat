package com.keepeat.backend.domain.ocr;

import com.keepeat.backend.domain.ocr.dto.OcrParseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "OCR", description = "영수증/장보기 내역 OCR 파싱")
@RestController
@RequestMapping("/api/v1/ocr")
@RequiredArgsConstructor
public class OcrController {

    private final OcrService ocrService;

    @Operation(summary = "영수증 OCR 파싱", description = "이미지에서 식재료명을 추출하고 DB 매칭 결과를 반환")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "파싱 성공"),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 이미지"),
            @ApiResponse(responseCode = "422", description = "식재료 추출 실패"),
            @ApiResponse(responseCode = "502", description = "OCR API 호출 실패")
    })
    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OcrParseResponse> parse(@RequestParam("image") MultipartFile image) {
        return ResponseEntity.ok(ocrService.parse(image));
    }
}
