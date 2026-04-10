package com.keepeat.backend.domain.recipe;

import com.keepeat.backend.domain.recipe.dto.GeneratedRecipesResponseDto;
import com.keepeat.backend.domain.recipe.dto.RegisteredRecipesRequestDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RecipeController {
    private final RecipeService recipeService;

    @GetMapping("/api/recipes/generate")
    public ResponseEntity<GeneratedRecipesResponseDto> getGeneratedRecipes(){
        GeneratedRecipesResponseDto response= recipeService.generateRecipes();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/recipes")
    public ResponseEntity<Void> putRecipes(@RequestBody @Valid RegisteredRecipesRequestDto request){
        recipeService.saveRecipes(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
