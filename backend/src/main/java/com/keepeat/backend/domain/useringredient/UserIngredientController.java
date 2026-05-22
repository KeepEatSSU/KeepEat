package com.keepeat.backend.domain.useringredient;

import com.keepeat.backend.domain.useringredient.dto.UserIngredientBulkCreateRequest;
import com.keepeat.backend.domain.useringredient.dto.UserIngredientBulkDeleteRequest;
import com.keepeat.backend.domain.useringredient.dto.UserIngredientRequest;
import com.keepeat.backend.domain.useringredient.dto.UserIngredientResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    @Operation(summary = "식재료 등록", description = "식재료를 벌크 등록. expiryDate가 null이면 보관 정보 기반 자동 계산")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공"),
            @ApiResponse(responseCode = "404", description = "식재료 또는 보관 정보를 찾을 수 없음")
    })
    @PostMapping
    public ResponseEntity<List<UserIngredientResponse>> create(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid UserIngredientBulkCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userIngredientService.create(userId, request.items()));
    }

    @Operation(summary = "냉장고 목록 조회", description = "로그인 사용자의 전체 식재료 목록을 남은 소비기한 오름차순으로 반환")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "204", description = "보유 식재료 없음 (빈 냉장고)")
    })
    @GetMapping
    public ResponseEntity<List<UserIngredientResponse>> findAll(@AuthenticationPrincipal Long userId) {
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
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "검색어 (식재료명)", example = "우유") @RequestParam String q) {
        List<UserIngredientResponse> result = userIngredientService.search(userId, q);
        if (result.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "레시피별 보유 식재료 조회",
            description = "특정 레시피의 마스터 식재료와 매칭되는 사용자 보유 UserIngredient 행을 중복 보존하여 반환. " +
                    "커스텀 식재료(ingredient=NULL)는 제외. 요리 완료 후 다 쓴 식재료를 선택해 삭제하는 UX에서 사용.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "204", description = "매칭되는 보유 식재료 없음"),
            @ApiResponse(responseCode = "404", description = "레시피를 찾을 수 없음 (RECIPE_NOT_FOUND)")
    })
    @GetMapping("/by-recipe/{recipeId}")
    public ResponseEntity<List<UserIngredientResponse>> findByRecipe(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "레시피 ID", example = "1") @PathVariable Long recipeId) {
        List<UserIngredientResponse> result = userIngredientService.findByRecipe(userId, recipeId);
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
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestBody UserIngredientRequest request) {
        return ResponseEntity.ok(userIngredientService.update(userId, id, request));
    }

    @Operation(summary = "식재료 삭제")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "404", description = "식재료를 찾을 수 없음")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        userIngredientService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "식재료 다중 삭제",
            description = "여러 개의 UserIngredient를 한 번에 삭제. ids 중 하나라도 사용자 소유가 아니면 전체 롤백.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "400", description = "ids 목록이 비어 있음"),
            @ApiResponse(responseCode = "404", description = "ids 중 사용자 소유가 아닌 항목 존재 (USER_INGREDIENT_NOT_FOUND)")
    })
    @DeleteMapping
    public ResponseEntity<Void> deleteMany(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid UserIngredientBulkDeleteRequest request) {
        userIngredientService.deleteMany(userId, request.ids());
        return ResponseEntity.noContent().build();
    }
}