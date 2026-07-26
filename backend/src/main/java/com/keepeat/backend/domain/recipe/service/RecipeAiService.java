package com.keepeat.backend.domain.recipe.service;
import com.keepeat.backend.domain.common.exception.ErrorCode;
import com.keepeat.backend.domain.common.exception.KeepEatException;
import com.keepeat.backend.domain.recipe.dto.GeneratedRecipesResponseDto;
import com.keepeat.backend.domain.recipe.entity.RecipeGenerationJob;
import com.keepeat.backend.domain.recipe.repository.RecipeGenerationJobRepository;
import com.keepeat.backend.domain.recipe.repository.RecipeGenerationUsageRepository;
import com.keepeat.backend.domain.useringredient.UserIngredient;
import com.keepeat.backend.domain.useringredient.UserIngredientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeAiService {
    private final UserIngredientRepository userIngredientRepository;
    private final AsyncRecipeService asyncRecipeService;
    private final RecipeGenerationJobRepository recipeGenerationJobRepository;
    private final RecipeGenerationUsageRepository recipeGenerationUsageRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;


    public void generateRecipes(Long userId) {
        LocalDate usageDate = LocalDate.now(ZoneId.of("Asia/Seoul"));

        String userPrompt = transactionTemplate.execute(status -> prepareGeneration(userId, usageDate));

        asyncRecipeService.processGeneration(userId, userPrompt, usageDate);
    }


    private String prepareGeneration(Long userId, LocalDate usageDate){

        List<String> userCondiment = new ArrayList<>();
        List<UserIngredient> userIngredients = userIngredientRepository.findAllByUserIdOrderByExpiryDate(userId);
        Map<String, Map<String, Object>> ingredientByName = new LinkedHashMap<>();

        // 만료일 빠른 순으로 정렬되어 들어오므로, 같은 이름은 가장 임박한 것이 먼저 등장
        for (UserIngredient ui : userIngredients) {

            // 커스텀 식재료(ingredient_id IS NULL)는 레시피 생성 후보에서 제외
            if (ui.getIngredient() == null) {
                continue;
            }
            // 소비기한이 이미 지난 식재료는 레시피 생성 후보에서 제외 (NULL은 무기한 취급)
            if (ui.getExpiryDate() != null && ui.getExpiryDate().isBefore(LocalDate.now())) {
                continue;
            }

            String categoryName = ui.getIngredient().getSubCategory().getCategory().getName();
            String name = ui.getIngredient().getName();

            if("양념/소스".equals(categoryName)){
                if(!userCondiment.contains(name)) {
                    userCondiment.add(name);
                }

            }else if(!ingredientByName.containsKey(name)){
                long daysLeft = calculateDaysLeft(ui.getExpiryDate());
                Map<String, Object> data = new LinkedHashMap<>();

                data.put("name", name);
                data.put("days_left", daysLeft);

                ingredientByName.put(name, data);
            }
        }
        List<Map<String, Object>> ingredientList = new ArrayList<>(ingredientByName.values());

        // 사용자 보유 식재료 확인용 로그
        log.debug(ingredientList.toString());
        log.debug(userCondiment.toString());

        if(ingredientList.size() < 3 || userCondiment.size() < 3){
            throw new KeepEatException(ErrorCode.INSUFFICIENT_INGREDIENTS);
        }

        int affectedRows = recipeGenerationJobRepository.tryStartGeneration(userId, usageDate);
        if(affectedRows == 0){
            throw new KeepEatException(ErrorCode.RECIPE_GENERATING);
        }

        boolean alreadyGenerated = recipeGenerationUsageRepository.existsByUserIdAndUsageDate(userId, usageDate);
        if (alreadyGenerated) {
            throw new KeepEatException(ErrorCode.RECIPE_DAILY_LIMIT_EXCEEDED);
        }

        String userPrompt = """        
                        보유 식재료
                        %s
                        
                        보유 조미료 (Seasonings)
                        %s
                        """.formatted(ingredientList.toString(), userCondiment.toString());

        return userPrompt;
    }

    private Long calculateDaysLeft(LocalDate expiryDate) {
        if (expiryDate == null) return 999L;
        return ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
    }

    public GeneratedRecipesResponseDto getGenerationResult(Long userId){
        RecipeGenerationJob job = recipeGenerationJobRepository.findByUserId(userId)
                .orElseThrow(() -> new KeepEatException(ErrorCode.RECIPE_JOB_NOT_FOUND));

        switch (job.getStatus()) {
            case DONE -> {
                try {
                    return objectMapper.readValue(job.getResultJson(), GeneratedRecipesResponseDto.class);
                } catch (Exception e) {
                    throw new KeepEatException(ErrorCode.AI_RESPONSE_PARSE_FAILURE, e);
                }
            }
            case FAILED -> throw new KeepEatException(job.getErrorCode());
            case PENDING -> throw new KeepEatException(ErrorCode.RECIPE_GENERATING);
            default -> throw new KeepEatException(ErrorCode.AI_RESPONSE_PARSE_FAILURE);
        }
    }
}
