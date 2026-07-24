package com.keepeat.backend.domain.ingredient;

import com.keepeat.backend.domain.category.Category;
import com.keepeat.backend.domain.category.CategoryRepository;
import com.keepeat.backend.domain.common.enums.IngredientStatus;
import com.keepeat.backend.domain.common.exception.ErrorCode;
import com.keepeat.backend.domain.common.exception.KeepEatException;
import com.keepeat.backend.domain.ingredient.dto.IngredientAiResponseDto;

import com.keepeat.backend.domain.ingredient.dto.IngredientInfoDto;
import com.keepeat.backend.domain.ingredient.dto.StorageTypeDto;
import com.keepeat.backend.domain.recipe.repository.RecipeRepository;
import com.keepeat.backend.domain.subcategory.SubCategory;
import com.keepeat.backend.domain.subcategory.SubCategoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class IngredientMappingService {

    private final ChatClient chatClient;
    private final IngredientRepository ingredientRepository;
    private final SubCategoryRepository subCategoryRepository;
    private final CategoryRepository categoryRepository;
    private final IngredientStorageRepository ingredientStorageRepository;
    private final IngredientAliasRepository ingredientAliasRepository;
    private final TransactionTemplate transactionTemplate;

    public IngredientMappingService(ChatClient.Builder chatClientBuilder,
                                    IngredientRepository ingredientRepository,
                                    SubCategoryRepository subCategoryRepository,
                                    CategoryRepository categoryRepository,
                                    IngredientStorageRepository ingredientStorageRepository,
                                    IngredientAliasRepository ingredientAliasRepository,
                                    TransactionTemplate transactionTemplate)
    {
        this.ingredientRepository = ingredientRepository;
        this.subCategoryRepository = subCategoryRepository;
        this.ingredientStorageRepository = ingredientStorageRepository;
        this.ingredientAliasRepository = ingredientAliasRepository;
        this.transactionTemplate = transactionTemplate;

        chatClient = chatClientBuilder
                .defaultOptions(GoogleGenAiChatOptions.builder()
                        .temperature(0.1)
                        .maxOutputTokens(5000)
                        .model("gemini-flash-lite-latest")
                        .responseMimeType("application/json")
                        .build())
                .defaultSystem("""
                            Role: 식품 저장·보관 전문가
                            
                            당신은 식재료의 분류와 보관 기한에 대한 전문 지식을 갖춘 전문가입니다.
                            주어진 식재료 이름 목록에 대해, 각 식재료의 카테고리 분류와 보관 방법별 유통기한 정보를 제공합니다.
                            
                            1. 분류 규칙
                              - categoryName: 반드시 아래 카테고리 목록 중 하나를 선택하세요.
                                [육류, 수산물, 채소, 과일/견과류, 유제품/달걀, 곡류/면류, 두부/콩류, 양념/소스, 음료/주류, 가공/즉석식품]
                              - subCategoryName: 반드시 아래 서브카테고리 목록에서 해당 카테고리에 속하는 것 중 하나를 선택하세요.
                                - 육류: [소고기, 돼지고기, 닭고기, 오리고기, 가공육]
                                - 수산물: [생선, 갑각류, 연체류, 패류, 해조류, 수산가공품]
                                - 채소: [잎채소, 줄기/파류, 뿌리채소, 열매채소, 버섯, 새싹/나물, 건채소/절임]
                                - 과일/견과류: [국내과일, 수입과일, 견과류/씨앗, 건과일/가공]
                                - 유제품/달걀: [유제품, 치즈류, 달걀류]
                                - 곡류/면류: [쌀/잡곡, 밀가루/가루류, 면류, 빵류, 떡류, 베이킹재료]
                                - 두부/콩류: [두부류, 콩류]
                                - 양념/소스: [장류, 기름류, 식초/당류, 소금/후추, 액젓/육수류, 소스류, 향신료]
                                - 음료/주류: [탄산음료, 주류, 기타음료]
                                - 가공/즉석식품: [통조림, 냉동/즉석식품, 김치/절임류, 묵류, 스프레드/잼, 기타가공품]
                            
                            2. 보관 정보(storageInfo) 규칙
                              - storageType: 실온, 냉장, 냉동 중 해당 식재료에 적합한 보관 방법만 포함하세요.
                                예) 생고기류는 실온 보관이 불가하므로 냉장, 냉동만 제공.
                                예) 소금, 설탕 등은 실온만 제공.
                              - min: 해당 보관 방법으로 보관 시 최소 보관 가능 기간 (보수적 추정)
                              - max: 해당 보관 방법으로 보관 시 최대 보관 가능 기간 (낙관적 추정)
                              - metric: 기간의 단위. 반드시 Days, Weeks, Months, Years 중 하나.
                                - Days: 1~30 범위의 일 단위 (1~4주 이내의 짧은 보관)
                                - Weeks: 1~4 범위의 주 단위 (1주~1개월 사이의 보관)
                                - Months: 1~24 범위의 월 단위 (1개월 이상의 보관)
                                - Years: 1~5 범위의 년 단위 (1년 이상 장기 보관)
                              - min과 max는 반드시 min <= max를 만족해야 합니다.
                              - 일반적인 가정용 보관 기준으로, 개봉 후 기준으로 작성하세요. 단, 통조림이나 밀봉 제품의 실온 보관은 개봉 전 기준입니다.
                            
                            3. 응답 제약 사항
                              - 요청받은 식재료 목록의 이름과 띄어쓰기까지 정확히 일치하는 이름을 ingredientName으로 반환하세요.
                            """)
                .build();
        this.categoryRepository = categoryRepository;
    }

    public Map<String, Ingredient> resolveIngredients(List<String> ingredientNames){

        Map<String, Ingredient> resultMap = new HashMap<>();

        List<String> uniqueIngredientNames = ingredientNames.stream().distinct().toList();

        List<Ingredient> foundIngredientsByName = ingredientRepository.findByNameIn(uniqueIngredientNames);

        for(Ingredient i : foundIngredientsByName){
            resultMap.put(i.getName(), i);
        }

        List<String> notFoundIngredientByName = uniqueIngredientNames.stream()
                .filter(name -> !resultMap.containsKey(name))
                .toList();

        if(!notFoundIngredientByName.isEmpty()){
            List<IngredientAlias> ingredientAliases = ingredientAliasRepository.findByAliasNameIn(notFoundIngredientByName);

            if (!ingredientAliases.isEmpty()) {
                log.info("별칭으로 매칭 성공: {}", ingredientAliases.stream().map(IngredientAlias::getAliasName).toList());
            }

            for(IngredientAlias ingredientAlias : ingredientAliases){
                resultMap.put(ingredientAlias.getAliasName(), ingredientAlias.getIngredient());
            }
        }


        List<String> notFoundIngredientNames = uniqueIngredientNames.stream()
                .filter(name -> !resultMap.containsKey(name))
                .toList();


        if (!notFoundIngredientNames.isEmpty()) {
            // 확인용 로그
            log.info("마스터 DB 미등록 식재료 → AI 생성 요청: {}", notFoundIngredientNames);

            List<Ingredient> createdIngredients = createIngredientDataByAi(notFoundIngredientNames);
            for (Ingredient i : createdIngredients) {
                resultMap.put(i.getName(), i);
            }
        }


        for (String originalName : uniqueIngredientNames) {
            if (!resultMap.containsKey(originalName)) {
                log.error("[최종 매핑 실패] AI가 요청된 식재료({})의 이름을 변형했거나 누락했습니다.", originalName);
                throw new KeepEatException(ErrorCode.AI_RESPONSE_PARSE_FAILURE);
            }
        }

        return resultMap;
    }


    private List<Ingredient> createIngredientDataByAi(List<String> ingredientNames){

        IngredientAiResponseDto responseFromAi;

         try{
             responseFromAi = chatClient.prompt()
                     .user("""
                        다음 식재료들의 분류와 보관 정보를 제공해주세요.
                        식재료 목록 : %s
                        """.formatted(ingredientNames.toString())
                     )
                     .call()
                     .entity(IngredientAiResponseDto.class);
         }catch (TransientAiException e){
             throw new KeepEatException(ErrorCode.AI_API_FAILURE, e);
         }catch (NonTransientAiException e){
             throw new KeepEatException(ErrorCode.AI_API_FAILURE, e);
         }catch (Exception e){
             throw new KeepEatException(ErrorCode.AI_RESPONSE_PARSE_FAILURE, e);
         }

        validateAiResponse(responseFromAi);


         return transactionTemplate.execute(status -> {
             List<Ingredient> createdIngredients = new ArrayList<>();

             for(IngredientInfoDto info : responseFromAi.ingredients()){


                 // 확인용 로그
                 StringBuilder storageDetails = new StringBuilder();
                 for (StorageTypeDto storage : info.storageInfo()) {
                     storageDetails.append(String.format("[%s: %d~%d %s] ",
                             storage.storageType(), storage.min(), storage.max(), storage.metric()));
                 }
                 log.info("▶ AI 새 식재료 생성: {} (분류: {} > {})",
                         info.ingredientName(), info.categoryName(), info.subCategoryName());
                 log.info("▶ 보관상세 정보: {}", storageDetails.toString());



                 Category category = categoryRepository.findByName(info.categoryName())
                         .orElseThrow(() -> new KeepEatException(ErrorCode.CATEGORY_NOT_FOUND));

                 SubCategory subCategory = subCategoryRepository.findByName(info.subCategoryName())
                         .orElseThrow(() -> new KeepEatException(ErrorCode.SUBCATEGORY_NOT_FOUND));

                 Ingredient ingredient = ingredientRepository.save(
                         Ingredient.builder()
                                 .name(info.ingredientName())
                                 .subCategory(subCategory)
                                 .status(IngredientStatus.PENDING)
                                 .build()
                 );

                 for (StorageTypeDto storage : info.storageInfo()) {
                     ingredientStorageRepository.save(
                             IngredientStorage.builder()
                                     .ingredient(ingredient)
                                     .storageType(storage.storageType())
                                     .min(storage.min())
                                     .max(storage.max())
                                     .metric(storage.metric())
                                     .build()
                     );
                 }
                 createdIngredients.add(ingredient);
             }

             return createdIngredients;
         });
    }



    private void validateAiResponse(IngredientAiResponseDto response) {
        if (response == null || response.ingredients() == null || response.ingredients().isEmpty()) {
            log.error("[AI 검증 실패] 응답 자체가 비어있거나 식재료 목록이 누락되었습니다.");
            throw new KeepEatException(ErrorCode.AI_RESPONSE_PARSE_FAILURE);
        }

        for (IngredientInfoDto info : response.ingredients()) {
            if (info.ingredientName() == null || info.ingredientName().isBlank()) {
                log.error("[AI 검증 실패] 식재료 이름이 누락되었습니다.");
                throw new KeepEatException(ErrorCode.AI_RESPONSE_PARSE_FAILURE);
            }


            if (info.storageInfo() == null || info.storageInfo().isEmpty()) {
                log.error("[AI 검증 실패] 보관 정보 누락 - 식재료: {}", info.ingredientName());
                throw new KeepEatException(ErrorCode.AI_RESPONSE_PARSE_FAILURE);
            }



            for (StorageTypeDto storage : info.storageInfo()) {
                if (storage.min() == null || storage.max() == null) {
                    log.error("[AI 검증 실패] 보관 기간(min/max) 누락 - 식재료: {}", info.ingredientName());
                    throw new KeepEatException(ErrorCode.AI_RESPONSE_PARSE_FAILURE);
                }
                if (storage.min() > storage.max()) {
                    log.error("[AI 검증 실패] 보관 기간 논리 오류 (min: {} > max: {}) - 식재료: {}",
                            storage.min(), storage.max(), info.ingredientName());
                    throw new KeepEatException(ErrorCode.AI_RESPONSE_PARSE_FAILURE);
                }

                if (storage.storageType() == null || storage.metric() == null) {
                    log.error("[AI 검증 실패] 보관 방법이나 기간 단위(metric) 누락 - 식재료: {}", info.ingredientName());
                    throw new KeepEatException(ErrorCode.AI_RESPONSE_PARSE_FAILURE);
                }
            }
        }
    }

}
