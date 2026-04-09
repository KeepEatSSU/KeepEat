package com.keepeat.backend.domain.userIngredient;

import com.keepeat.backend.domain.userIngredient.dto.UserIngredientRequest;
import com.keepeat.backend.domain.userIngredient.dto.UserIngredientResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "UserIngredient", description = "사용자 보유 식재료 관리")
@RestController
@RequestMapping("/api/v1/user-ingredients")
@RequiredArgsConstructor
public class UserIngredientController {

    private final UserIngredientService userIngredientService;

    @Operation(summary = "냉장고 목록 조회", description = "로그인 사용자의 전체 식재료 목록을 남은 소비기한 오름차순으로 반환")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "204", description = "보유 식재료 없음 (빈 냉장고)")
    })
    @GetMapping
    public ResponseEntity<List<UserIngredientResponse>> findAll() {
        Long userId = getCurrentUserId();
        List<UserIngredientResponse> result = userIngredientService.findAll(userId);
        if (result.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "식재료 검색", description = "Ingredient.name LIKE 검색, 남은 소비기한 오름차순 정렬")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "검색 결과"),
            @ApiResponse(responseCode = "204", description = "검색 결과 없음")
    })
    @GetMapping("/search")
    public ResponseEntity<List<UserIngredientResponse>> search(
            @Parameter(description = "검색어 (식재료명)", example = "우유") @RequestParam String q) {
        Long userId = getCurrentUserId();
        List<UserIngredientResponse> result = userIngredientService.search(userId, q);
        if (result.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "식재료 수정", description = "변경할 필드만 포함하여 부분 수정")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "404", description = "식재료를 찾을 수 없음")
    })
    @PatchMapping("/{id}")
    public ResponseEntity<UserIngredientResponse> update(
            @PathVariable Long id,
            @RequestBody UserIngredientRequest request) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(userIngredientService.update(userId, id, request));
    }

    @Operation(summary = "식재료 삭제")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "404", description = "식재료를 찾을 수 없음")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        userIngredientService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    private Long getCurrentUserId() {
        // Spring Security 연동 후 실제 인증 사용자 ID로 교체
        return 1L;
    }
}
