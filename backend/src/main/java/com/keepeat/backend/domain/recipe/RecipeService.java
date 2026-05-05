package com.keepeat.backend.domain.recipe;

import com.keepeat.backend.domain.common.exception.ErrorCode;
import com.keepeat.backend.domain.common.exception.KeepEatException;
import com.keepeat.backend.domain.ingredient.Ingredient;
import com.keepeat.backend.domain.ingredient.IngredientMappingService;
import com.keepeat.backend.domain.recipe.dto.*;
import com.keepeat.backend.domain.recipe.entity.Recipe;
import com.keepeat.backend.domain.recipe.entity.RecipeIngredient;
import com.keepeat.backend.domain.recipe.entity.UserRecipe;
import com.keepeat.backend.domain.recipe.repository.RecipeRepository;
import com.keepeat.backend.domain.recipe.repository.UserRecipeRepository;

import com.keepeat.backend.domain.user.entity.AppUser;
import com.keepeat.backend.domain.user.repository.AppUserRepository;

import com.keepeat.backend.domain.useringredient.UserIngredient;
import com.keepeat.backend.domain.useringredient.UserIngredientRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final UserIngredientRepository userIngredientRepository;
    private final UserRecipeRepository userRecipeRepository;
    private final AppUserRepository appUserRepository;
    private final IngredientMappingService ingredientMappingService;
    private final TransactionTemplate transactionTemplate;

    // 레시피 데이터들 받아서 레시피 저장 하고 관심 레시피 등록
    public void saveRecipes(RegisteredRecipesRequestDto requestRecipes, Long userId){

        List<Recipe> recipeList = new ArrayList<>();

        List<String> allIngredientNames = requestRecipes.recipes().stream()
                .flatMap(recipe -> recipe.requiredIngredients().stream())
                .map(ingredient -> ingredient.name())
                .toList();

        Map<String, Ingredient> ingredientMap = ingredientMappingService.resolveIngredients(allIngredientNames);

        transactionTemplate.executeWithoutResult(status -> {
            for(RegisteredRecipeDto requestRecipe : requestRecipes.recipes()){
                Recipe recipe = Recipe.builder()
                        .recipeName(requestRecipe.recipeName())
                        .cookingMethod(requestRecipe.cookingMethod())
                        .cookingTime(requestRecipe.cookingTime())
                        .calories(requestRecipe.calories())
                        .difficulty(requestRecipe.difficulty())
                        .instructions(String.join("\n", requestRecipe.instructions()))
                        .createdAt(LocalDate.now())
                        .build();

                for(RecipeIngredientDto requestIngredient : requestRecipe.requiredIngredients()){
                    Ingredient ingredient = ingredientMap.get(requestIngredient.name());
                    RecipeIngredient recipeIngredient = new RecipeIngredient(ingredient, requestIngredient.amount(), recipe);
                    recipe.getRequiredIngredients().add(recipeIngredient);
                }
                recipeList.add(recipe);
            }
            recipeRepository.saveAll(recipeList);

            addUserRecipeByRecipes(userId, recipeList);
        });

    }


    // 레시피 데이터들 받아서 관심 레시피 저장 하는 헬퍼 메서드
    private void addUserRecipeByRecipes(Long userId, List<Recipe> recipes){
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new KeepEatException(ErrorCode.USER_NOT_FOUND));

        List<UserRecipe> userRecipeList = new ArrayList<>();

        for(Recipe recipe : recipes){
            userRecipeList.add(new UserRecipe(recipe, user, LocalDate.now()));
        }

        userRecipeRepository.saveAll(userRecipeList);
    }


    // 관심 레시피 등록하는 메서드
    // 추후 레시피 검색 기능과 연계되서 사용될 예정
    @Transactional
    public void addUserRecipeByIds(Long userId, List<Long> recipeIds){
        List<Long> distinctRecipeIds = recipeIds.stream().distinct().toList();

        List<Recipe> recipes = recipeRepository.findAllById(distinctRecipeIds);

        if(recipes.size() != distinctRecipeIds.size()){
            throw new KeepEatException(ErrorCode.RECIPE_NOT_FOUND);
        }

        try {
            addUserRecipeByRecipes(userId, recipes);
        }catch (DataIntegrityViolationException e){
            throw new KeepEatException(ErrorCode.DUPLICATE_RECIPE);
        }
    }

    // 관심 레시피 등록 해제 메서드
    @Transactional
    public void deleteMyRecipes(Long userId, List<Long> recipeIds){

        List<Long> distinctRecipeIds = recipeIds.stream().distinct().toList();

        long matchingCountForValid = userRecipeRepository.countByAppUserIdAndRecipeIds(userId, distinctRecipeIds);

        if(matchingCountForValid != distinctRecipeIds.size()){
            throw new KeepEatException(ErrorCode.USER_RECIPE_ACCESS_DENIED);
        }

        userRecipeRepository.deleteAllByAppUserIdAndRecipeIds(userId, distinctRecipeIds);
    }


    // 관심 레시피 목록 조회 메서드
    @Transactional(readOnly = true)
    public MyRecipesResponseDto getMyRecipesByUserId(Long userId){
        List<UserRecipe> userRecipes = userRecipeRepository.findAllByUserId(userId);

        List<MyRecipeDto> myRecipes = new ArrayList<>();

        for(UserRecipe userRecipe : userRecipes){
            Recipe recipe = userRecipe.getRecipe();
            myRecipes.add(new MyRecipeDto(
                    recipe.getId(),
                    recipe.getRecipeName(),
                    recipe.getDifficulty(),
                    recipe.getCookingMethod(),
                    recipe.getCookingTime(),
                    recipe.getCalories(),
                    userRecipe.getCreatedAt())
            );
        }
        return new MyRecipesResponseDto(myRecipes);
    }

    // 관심 레시피 세부 정보 조회 메서드
    @Transactional(readOnly = true)
    public RecipeDetailResponseDto getDetailOfMyRecipe(Long userId, Long recipeId){

        Recipe recipe = recipeRepository.findByIdWithIngredient(recipeId)
                .orElseThrow(() -> new KeepEatException(ErrorCode.RECIPE_NOT_FOUND));

        UserRecipe userRecipe = userRecipeRepository.findByAppUserIdAndRecipeId(userId, recipeId)
                .orElseThrow(() -> new KeepEatException(ErrorCode.USER_RECIPE_ACCESS_DENIED));


        List<String> instructionList = (recipe.getInstructions() == null || recipe.getInstructions().isBlank())
                ? new ArrayList<>()
                : Arrays.asList(recipe.getInstructions().split("\n"));

        List<UserIngredient> userIngredients = userIngredientRepository.findAllByUserIdOrderByExpiryDate(userId);

        Set<Long> userIngredientSet = userIngredients.stream()
                .map(ingredient -> ingredient.getIngredient().getId())
                .collect(Collectors.toSet());

        List<RecipeIngredientDetailDto> recipeIngredientList = new ArrayList<>();

        for(RecipeIngredient ingredient : recipe.getRequiredIngredients()){
            boolean isOwned = userIngredientSet.contains(ingredient.getIngredient().getId());
            recipeIngredientList.add(new RecipeIngredientDetailDto(
                    ingredient.getIngredient().getName(), ingredient.getAmount(), isOwned));
        }

        RecipeDetailResponseDto response = RecipeDetailResponseDto.builder()
                .recipeId(recipe.getId())
                .recipeName(recipe.getRecipeName())
                .difficulty(recipe.getDifficulty())
                .cookingTime(recipe.getCookingTime())
                .calories(recipe.getCalories())
                .instructions(instructionList)
                .cookingMethod(recipe.getCookingMethod())
                .requiredIngredients(recipeIngredientList)
                .createdAt(userRecipe.getCreatedAt())
                .build();

        return response;
    }


}
