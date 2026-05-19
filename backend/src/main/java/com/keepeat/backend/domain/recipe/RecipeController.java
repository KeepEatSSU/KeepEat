package com.keepeat.backend.domain.recipe;

import com.keepeat.backend.domain.recipe.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class RecipeController {
    private final RecipeService recipeService;
    private final RecipeAiService recipeAiService;

    @PostMapping("/api/v1/recipes/generate")
    public ResponseEntity<GeneratedRecipesResponseDto> getGeneratedRecipes(
            @AuthenticationPrincipal Long userId
    ){
        GeneratedRecipesResponseDto response = recipeAiService.generateRecipes(userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/v1/recipes")
    public ResponseEntity<Void> putRecipes(
            @RequestBody @Valid RegisteredRecipesRequestDto request,
            @AuthenticationPrincipal Long userId
    ){
        recipeService.saveRecipes(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/api/v1/my-recipes")
    public ResponseEntity<Void> deleteMyRecipes(
            @RequestParam List<Long> recipeIds,
            @AuthenticationPrincipal Long userId)
    {
        recipeService.deleteMyRecipes(userId, recipeIds);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/my-recipes")
    public ResponseEntity<MyRecipesResponseDto> getMyRecipesList(
            @AuthenticationPrincipal Long userId
    ){
        MyRecipesResponseDto response = recipeService.getMyRecipesByUserId(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/v1/recipes/detail/{recipeId}")
    public ResponseEntity<RecipeDetailResponseDto> getMyRecipeDetail(
            @PathVariable Long recipeId,
            @AuthenticationPrincipal Long userId
    ){
        RecipeDetailResponseDto response = recipeService.getRecipeDetail(userId,recipeId);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/v1/my-recipes")
    public ResponseEntity<Void> addMyRecipes(
            @Valid @RequestBody MyRecipesRegisterRequestDto request,
            @AuthenticationPrincipal Long userId
    ){
        recipeService.addUserRecipeByIds(userId, request.recipeIds());

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/api/v1/recipes/{recipeId}/cook")
    public ResponseEntity<String> completeCooking(
            @PathVariable Long recipeId,
            @AuthenticationPrincipal Long userId
    ){
        // 요리 완료 로직 실행 (알림 발송 포함)
        recipeService.completeCooking(userId, recipeId);

        return ResponseEntity.ok("요리 완료 처리가 성공적으로 기록되었습니다.");
    }

    @GetMapping("/api/v1/recipes")
    public ResponseEntity<RecipeListResponseDto> searchRecipes(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        RecipeListResponseDto response = recipeService.searchRecipes(keyword, cursor, size);
        return ResponseEntity.ok(response);
    }

}
