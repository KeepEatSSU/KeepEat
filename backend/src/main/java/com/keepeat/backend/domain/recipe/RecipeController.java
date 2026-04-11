package com.keepeat.backend.domain.recipe;

import com.keepeat.backend.domain.recipe.dto.GeneratedRecipesResponseDto;
import com.keepeat.backend.domain.recipe.dto.MyRecipesResponseDto;
import com.keepeat.backend.domain.recipe.dto.RecipeDetailResponseDto;
import com.keepeat.backend.domain.recipe.dto.RegisteredRecipesRequestDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class RecipeController {
    private final RecipeService recipeService;

    @PostMapping("/api/v1/recipes/generate")
    public ResponseEntity<GeneratedRecipesResponseDto> getGeneratedRecipes(){
        GeneratedRecipesResponseDto response= recipeService.generateRecipes();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/v1/recipes")
    public ResponseEntity<Void> putRecipes(@RequestBody @Valid RegisteredRecipesRequestDto request){
        recipeService.saveRecipes(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/api/v1/my-recipes")
    public ResponseEntity<Void> deleteMyRecipes(@RequestParam List<Long> recipeIds){
        recipeService.deleteMyRecipes(1L, recipeIds);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/my-recipes")
    public ResponseEntity<MyRecipesResponseDto> getMyRecipesList(){
        MyRecipesResponseDto response = recipeService.getMyRecipesByUserId(1L);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/v1/my-recipe/{recipeId}")
    public ResponseEntity<?> getMyRecipeDetail(@PathVariable Long recipeId){
        RecipeDetailResponseDto response = recipeService.getDetailOfMyRecipe(1L,recipeId );

        return ResponseEntity.ok(response);
    }

}
