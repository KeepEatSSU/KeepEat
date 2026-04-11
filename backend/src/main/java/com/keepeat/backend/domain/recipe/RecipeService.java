package com.keepeat.backend.domain.recipe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepeat.backend.domain.recipe.dto.*;
import com.keepeat.backend.domain.recipe.entity.Recipe;
import com.keepeat.backend.domain.recipe.entity.RecipeIngredient;
import com.keepeat.backend.domain.recipe.entity.UserRecipe;
import com.keepeat.backend.domain.recipe.repository.RecipeRepository;
import com.keepeat.backend.domain.recipe.repository.UserRecipeRepository;
import com.keepeat.backend.domain.userIngredient.UserIngredient;
import com.keepeat.backend.domain.userIngredient.UserIngredientRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class RecipeService {
    private final ChatClient chatClient;
    private final RecipeRepository recipeRepository;
    private final UserIngredientRepository userIngredientRepository;
    private final UserRecipeRepository userRecipeRepository;

    public RecipeService(ChatClient.Builder chatClientBuilder,
                         RecipeRepository recipeRepository,
                         UserIngredientRepository userIngredientRepository,
                         UserRecipeRepository userRecipeRepository){

        this.recipeRepository = recipeRepository;
        this.userIngredientRepository = userIngredientRepository;
        this.userRecipeRepository = userRecipeRepository;

        this.chatClient = chatClientBuilder
                .defaultOptions(GoogleGenAiChatOptions.builder()
                        .temperature(0.2)
                        .maxOutputTokens(8000)
                        .model("gemini-2.5-pro")
                        .responseMimeType("application/json")
                        .build())
                .defaultSystem("""
                        Role: 제로 웨이스트 전문 셰프
                        당신은 냉장고 재료 활용을 극대화하여 버려지는 식재료를 최소화하고, 미식적 완성도가 높은 '진짜 요리'를 설계하는 1인분 요리 전문가입니다.
                        
                        Mission
                        제공된 보유 식재료와 조미료 리스트를 바탕으로 총 5개의 레시피를 제안하세요.
                        
                        1. 레시피 구성 및 우선순위
                        레시피 1~3 (Zero Waste): 오직 보유 재료와 조미료만 사용.
                        레시피 4~5 (Level Up): 보유 재료를 베이스로 하되, 식재료 또는 조미료 1~3개를 추가 구매하여 요리의 질을 높임.
                        최우선 순위: `days_left`가 짧은 재료를 우선적으로 활용하되, 자연스러운 조합이 아닌 경우 다른 레시피에 나누어 배치하세요.
                        핵심 재료 체크: 요리 제목에 들어가는 주재료(밥, 면, 고기 등)가 없을 경우 1~3번에서 제외하거나, 보유한 재료로 대체 가능한 제목으로 수정할 것.
                        실존 요리 원칙: 모든 레시피는 대중적으로 검증된 레시피여야 한다. 검색 시 관련된 비슷한 레시피가 있을 정도의 레시피여야 한다.
                        
                        
                        2. 조리 디테일 (Quality Control)
                        음식의 완성도: 단순 혼합이 아닌, 식재료의 특성을 살린 조리법을 적용한 실제 레시피여야 함.
                        상세 가이드: `instructions`는 반드시 5단계 이상으로 구성하며, 각 단계의 시작은 항상 숫자와 마침표로 시작하세요 (예: "1. 팬을 달구고...", "2. 고기를 볶아줍니다.").
                        필수 정보: 단계별로 정확한 불 세기(강/중/약불), 조리 시간(분/초), 투입하는 양념의 양을 반드시 명시할 것.
                        계량 및 속성 필수: requiredIngredients 배열은 재료명(name)과 계량 수치(amount)만을 속성으로 갖는 객체 리스트로 구성하세요. (예: [{"name": "생삼겹살", "amount": "150g"}]) 추가로 구매해야 하는 식재료가 포함되어도 절대 "(추가 구매)"와 같은 별도의 표시나 마킹을 하지 말고 평범한 재료명만 적으세요.
                        다양성: 5개 레시피의 `cookingMethod`는 중복되지 않게 골고루 배분할 것.
                        추가 정보: 각 레시피의 예상 칼로리를 계산하여 `calories` 필드에 숫자(kcal 단위)로 제공하세요.
                        """)
                .build();
    }


    public GeneratedRecipesResponseDto generateRecipes(){

        ObjectMapper objectMapper = new ObjectMapper();

        // 유저 식별 로직(추후)
        Long userId = 1L;

        String userIngredientJsonString = "";

        // null일 경우 예외처리 로직 필요(추후)
        List<UserIngredient> userIngredients = userIngredientRepository.findAllByUserIdOrderByExpiryDate(userId);

        List<Map<String, Object>> ingredientList = new ArrayList<>();

        for(UserIngredient ui : userIngredients){
            long daysLeft = calculateDaysLeft(ui.getExpiryDate());
            Map<String, Object> data = new HashMap<>();

            data.put("name", ui.getIngredient().getName());
            data.put("days_left", daysLeft);

            ingredientList.add(data);
        }

        // 임시 예외 처리 로직(추후 수정)
        try{
             userIngredientJsonString = objectMapper.writeValueAsString(ingredientList);
        }catch (JsonProcessingException e){
            throw new RuntimeException("프롬프트에 사용할 데이터를 JSON으로 변환하는 중에 에러가 발생했습니다.", e);
        }


        // 식재료 까지는 프롬프트에 넣을 수 있게 변환 되었음.
        // 나중에 사용자가 보유중인 조미료만 따로 가져오는 로직이 추가로 필요함.


        GeneratedRecipesResponseDto responseFromAi = chatClient.prompt()
                .user("""        
                        보유 식재료
                        [
                        {"name": "생삼겹살", "days_left": 1},
                        {"name": "생굴", "days_left": 1},
                        {"name": "시금치", "days_left": 1},
                        {"name": "고사리", "days_left": 2},
                        {"name": "두부", "days_left": 2},
                        {"name": "무", "days_left": 10},
                        {"name": "대파", "days_left": 5},
                        {"name": "느타리버섯", "days_left": 2},
                        {"name": "냉동 차돌박이", "days_left": 100},
                        {"name": "묵은지", "days_left": 60},
                        {"name": "콩나물", "days_left": 2},
                        {"name": "애호박", "days_left": 4}
                        ]
                        보유 조미료 (Seasonings)
                        ["고추장", "된장", "간장", "고춧가루", "다진 마늘", "참기름", "식용유", "설탕"]
                        """)
                .call()
                .entity(GeneratedRecipesResponseDto.class);

        return responseFromAi;
    }


    // 레시피 DB 뿐만 아니라 사용자 식별해서 해당 사용자랑 매칭 시켜주는 로직도 필요함.
    // 추후에 USER 엔티티 받으면 Recipe_User 테이블 만들어서 거기에 저장하는 로직도 추가해야함.
    // 그리고 이건 그 실제로 레시피 DB에 레시피를 저장하는 로직임.
    // 그래서 사용자가 내 레시피에 등록하고 해제하는 로직은 따로 만들어야 할 듯.
    // 이거는 사용자가 레시피 추천 기능에서 내 레시피에 등록하는 레시피를 DB에 저장할 때 사용하는 로직임.
    @Transactional
    public void saveRecipes(RegisteredRecipesRequestDto requestRecipes){
        // 유저 가져오는 로직
        Long userId = 1L;


        List<Recipe> recipeList = new ArrayList<>();

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
                RecipeIngredient recipeIngredient = new RecipeIngredient(requestIngredient.name(), requestIngredient.amount(), recipe);
                recipe.getRequiredIngredients().add(recipeIngredient);
            }
            recipeList.add(recipe);
        }
        recipeRepository.saveAll(recipeList);

        addUserRecipeByRecipes(userId, recipeList);
    }


    // 추후 예외 처리 필요
    @Transactional
    public void addUserRecipeByRecipes(Long userId, List<Recipe> recipes){
        List<UserRecipe> userRecipeList = new ArrayList<>();

        for(Recipe recipe : recipes){
            userRecipeList.add(new UserRecipe(recipe, userId, LocalDate.now()));
        }

        userRecipeRepository.saveAll(userRecipeList);
    }

    // 추후 예외 처리 필요
    // 이거는 추후에 레시피 DB가 쌓이면 사용할 서비스 메서드임
    // 레시피를 직접 검색한 후 내 레시피에 등록할 때 사용하는 메서드임. 미리 만들어둠.
    @Transactional
    public void addUserRecipeByIds(Long userId, List<Long> recipeIds){
        List<Recipe> recipes = recipeRepository.findAllById(recipeIds);
        addUserRecipeByRecipes(userId, recipes);

    }

    @Transactional
    public void deleteMyRecipes(Long userId, List<Long> recipeIds){
        userRecipeRepository.deleteAllByUserIdAndRecipeIds(userId, recipeIds);
    }


    //또한 예외 처리 필요함.
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

    // 예외처리 필요
    // 사용자 보유 재료와 레시피 재료 비교해서 나눠서 주는 로직 추가 필요
    // 지금은 ingredient 테이블의 ID와 연결하지 않아서 아직 로직 추가 안했음.
    @Transactional(readOnly = true)
    public RecipeDetailResponseDto getDetailOfMyRecipe(Long userId, Long recipeId){

        UserRecipe userRecipe = userRecipeRepository.findByUserIdAndRecipeId(userId, recipeId)
                .orElseThrow(() -> new RuntimeException("당신에게 그런 레시피 없어용. ㄲㅈ셈"));

        Recipe recipe = recipeRepository.findByIdWithIngredient(recipeId)
                .orElseThrow(() -> new RuntimeException("해당레시피 찾을 수 없으셈. ㄲㅈ셈"));

        List<RecipeIngredient> recipeIngredients = recipe.getRequiredIngredients();

        List<String> instructionList = (recipe.getInstructions() == null || recipe.getInstructions().isBlank())
                ? new ArrayList<>()
                : Arrays.asList(recipe.getInstructions().split("\n"));


        List<RecipeIngredientDto> recipeIngredientList = new ArrayList<>();
        for(RecipeIngredient ingredient : recipe.getRequiredIngredients()){
            recipeIngredientList.add(new RecipeIngredientDto(ingredient.getName(), ingredient.getAmount()));
        }


        RecipeDetailResponseDto response = RecipeDetailResponseDto.builder()
                .recipeId(recipe.getId())
                .recipeName(recipe.getRecipeName())
                .difficulty(recipe.getDifficulty())
                .cookingTime(recipe.getCookingTime())
                .calories(recipe.getCalories())
                .instructions(instructionList)
                .requiredIngredients(recipeIngredientList)
                .createdAt(userRecipe.getCreatedAt())
                .build();

        return response;
    }


    private Long calculateDaysLeft(LocalDate expiryDate) {
        if (expiryDate == null) return 999L;
        return ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
    }



}
