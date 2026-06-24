package com.keepeat.backend.domain.recipe;

import com.keepeat.backend.domain.recipe.dto.*;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Recipe", description = "레시피 생성/저장/검색/상세/반응 API")
@RestController
@RequiredArgsConstructor
public class RecipeController {
    private final RecipeService recipeService;
    private final RecipeAiService recipeAiService;
    private final RecipeReactionService recipeReactionService;

    @Operation(
            summary = "AI 레시피 생성 요청",
            description = "로그인 사용자의 보유 식재료를 기반으로 AI 레시피 생성을 **백그라운드에서 시작**한다. " +
                    "요청 즉시 202를 반환하며, 생성이 완료되면 푸시 알림(RECIPE_READY)이 발송된다. " +
                    "결과는 `/api/v1/recipes/result`로 조회한다. " +
                    "Zero Waste 3개 + Level Up 2개 구성, 식재료 3개 이상 + 조미료 3개 이상 보유 시에만 호출 가능."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "생성 요청 접수 (백그라운드 처리 시작)"),
            @ApiResponse(responseCode = "400", description = "식재료/조미료 부족 (INSUFFICIENT_INGREDIENTS)"),
            @ApiResponse(responseCode = "409", description = "이미 생성 진행 중 (RECIPE_GENERATING)")
    })
    @PostMapping("/api/v1/recipes/generate")
    public ResponseEntity<Void> getGeneratedRecipes(
            @AuthenticationPrincipal Long userId
    ){
        recipeAiService.generateRecipes(userId);
        return ResponseEntity.accepted().build();
    }

    @Operation(
            summary = "AI 레시피 생성 결과 조회",
            description = "백그라운드에서 생성된 레시피 결과를 조회한다. 푸시 알림(RECIPE_READY) 수신 후 호출한다. " +
                    "생성이 아직 진행 중이면 409, 생성 이력이 없으면 404, 생성에 실패했으면 502/500을 반환한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "레시피 5개 조회 성공"),
            @ApiResponse(responseCode = "409", description = "아직 생성 중 (RECIPE_GENERATING)"),
            @ApiResponse(responseCode = "404", description = "생성 요청 내역 없음 (RECIPE_JOB_NOT_FOUND)"),
            @ApiResponse(responseCode = "502", description = "AI 호출 실패 (AI_API_FAILURE)"),
            @ApiResponse(responseCode = "500", description = "AI 응답 파싱 실패 (AI_RESPONSE_PARSE_FAILURE)")
    })
    @GetMapping("/api/v1/recipes/result")
    public ResponseEntity<GeneratedRecipesResponseDto> getGenerationResult(
            @AuthenticationPrincipal Long userId
    ){
        GeneratedRecipesResponseDto response = recipeAiService.getGenerationResult(userId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "레시피 등록 (벌크)",
            description = "AI가 생성한 레시피 목록을 DB에 저장한다. 저장과 동시에 사용자의 관심 레시피(UserRecipe)로 자동 등록된다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "레시피 등록 성공"),
            @ApiResponse(responseCode = "400", description = "요청 본문 검증 실패"),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음 (USER_NOT_FOUND)")
    })
    @PostMapping("/api/v1/recipes")
    public ResponseEntity<Void> putRecipes(
            @RequestBody @Valid RegisteredRecipesRequestDto request,
            @AuthenticationPrincipal Long userId
    ){
        recipeService.saveRecipes(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(
            summary = "내 레시피 삭제 (벌크)",
            description = "관심 레시피 목록에서 지정한 레시피 ID들을 제거한다. " +
                    "본인이 관심 레시피로 등록한 ID만 삭제 가능하며, 목록에 본인 소유가 아닌 ID가 하나라도 포함되면 전체 요청이 거부된다. " +
                    "(레시피 엔티티 자체는 삭제되지 않음)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "403", description = "본인이 등록하지 않은 레시피 ID 포함 (USER_RECIPE_ACCESS_DENIED)")
    })
    @DeleteMapping("/api/v1/my-recipes")
    public ResponseEntity<Void> deleteMyRecipes(
            @Parameter(description = "삭제할 레시피 ID 목록 (콤마 구분)", example = "1,2,3")
            @RequestParam List<Long> recipeIds,
            @AuthenticationPrincipal Long userId)
    {
        recipeService.deleteMyRecipes(userId, recipeIds);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "내 레시피 목록 조회",
            description = "사용자가 관심 레시피로 등록한 전체 목록을 좋아요/싫어요 수, 본인 반응(myReaction)과 함께 반환한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/api/v1/my-recipes")
    public ResponseEntity<MyRecipesResponseDto> getMyRecipesList(
            @AuthenticationPrincipal Long userId
    ){
        MyRecipesResponseDto response = recipeService.getMyRecipesByUserId(userId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "레시피 상세 조회",
            description = "레시피 ID로 상세 정보를 조회한다. " +
                    "사용자 보유 식재료 매칭 여부(isOwned), 식재료 상태(ACTIVE/PENDING), " +
                    "관심 레시피 등록 여부(isRegisteredMyRecipe), 본인의 좋아요/싫어요 반응(myReaction)을 포함한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "레시피를 찾을 수 없음 (RECIPE_NOT_FOUND)")
    })
    @GetMapping("/api/v1/recipes/detail/{recipeId}")
    public ResponseEntity<RecipeDetailResponseDto> getMyRecipeDetail(
            @Parameter(description = "조회할 레시피 ID", example = "1")
            @PathVariable Long recipeId,
            @AuthenticationPrincipal Long userId
    ){
        RecipeDetailResponseDto response = recipeService.getRecipeDetail(userId,recipeId);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "내 레시피 추가",
            description = "이미 존재하는 레시피들을 사용자의 관심 레시피(UserRecipe) 목록에 추가한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "추가 성공"),
            @ApiResponse(responseCode = "404", description = "레시피 또는 사용자를 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "이미 등록된 레시피 (DUPLICATE_RECIPE)")
    })
    @PostMapping("/api/v1/my-recipes")
    public ResponseEntity<Void> addMyRecipes(
            @Valid @RequestBody MyRecipesRegisterRequestDto request,
            @AuthenticationPrincipal Long userId
    ){
        recipeService.addUserRecipeByIds(userId, request.recipeIds());

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(
            summary = "요리 완료 처리",
            description = "관심 레시피로 등록된 레시피의 요리 완료를 기록한다. 알림이 함께 발송된다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "요리 완료 기록 성공"),
            @ApiResponse(responseCode = "403", description = "내 레시피로 등록되지 않은 레시피 (RECIPE_NOT_BOOKMARKED)"),
            @ApiResponse(responseCode = "404", description = "레시피를 찾을 수 없음 (RECIPE_NOT_FOUND)")
    })
    @PostMapping("/api/v1/recipes/{recipeId}/cook")
    public ResponseEntity<String> completeCooking(
            @Parameter(description = "요리 완료할 레시피 ID", example = "1")
            @PathVariable Long recipeId,
            @AuthenticationPrincipal Long userId
    ){
        // 요리 완료 로직 실행 (알림 발송 포함)
        recipeService.completeCooking(userId, recipeId);

        return ResponseEntity.ok("요리 완료 처리가 성공적으로 기록되었습니다.");
    }

    @Operation(
            summary = "레시피 검색 (커서 페이징)",
            description = "레시피명/식재료명/별칭을 LIKE로 검색한다. 최신순(createdAt DESC, id DESC) 정렬, 커서 기반 페이징. " +
                    "첫 호출은 cursor 파라미터를 생략하고, 이후에는 응답의 nextCursor 값을 그대로 전달한다. " +
                    "hasNext=false면 더 이상 호출하지 않는다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "검색 결과"),
            @ApiResponse(responseCode = "400", description = "잘못된 커서 형식 (INVALID_CURSOR)")
    })
    @GetMapping("/api/v1/recipes")
    public ResponseEntity<RecipeListResponseDto> searchRecipes(
            @Parameter(description = "검색어 (레시피명/식재료명/별칭). 비우면 전체 조회", example = "김치")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "이전 응답의 nextCursor 값. 첫 호출은 비움. 형식: {yyyy-MM-dd}_{recipeId}", example = "2026-05-21_123")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (1~100, 범위 외는 자동 보정)", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Long userId
    ) {
        RecipeListResponseDto response = recipeService.searchRecipes(keyword, cursor, size, userId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "레시피 좋아요/싫어요 토글",
            description = "레시피에 LIKE 또는 DISLIKE 반응을 토글한다. " +
                    "같은 반응을 다시 보내면 해제(OFF), 반대 반응을 보내면 기존 반응이 교체된다. " +
                    "한 사용자당 한 레시피에 최대 1개의 반응만 유지된다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "반응 처리 성공"),
            @ApiResponse(responseCode = "404", description = "레시피 또는 사용자를 찾을 수 없음")
    })
    @PostMapping("/api/v1/recipes/{recipeId}/reaction")
    public ResponseEntity<Void> reactToRecipe(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "반응할 레시피 ID", example = "1")
            @PathVariable Long recipeId,
            @Valid @RequestBody RecipeReactionRequestDto request
    ){
        recipeReactionService.react(userId, recipeId, request.reactionType());

        return ResponseEntity.noContent().build();
    }

}
