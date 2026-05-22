package com.keepeat.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepeat.backend.domain.category.Category;
import com.keepeat.backend.domain.category.CategoryRepository;
import com.keepeat.backend.domain.common.enums.CookingMethod;
import com.keepeat.backend.domain.common.enums.Difficulty;
import com.keepeat.backend.domain.common.enums.Metric;
import com.keepeat.backend.domain.common.enums.StorageType;
import com.keepeat.backend.domain.ingredient.Ingredient;
import com.keepeat.backend.domain.ingredient.IngredientAlias;
import com.keepeat.backend.domain.ingredient.IngredientAliasRepository;
import com.keepeat.backend.domain.ingredient.IngredientRepository;
import com.keepeat.backend.domain.ingredient.IngredientStorage;
import com.keepeat.backend.domain.ingredient.IngredientStorageRepository;
import com.keepeat.backend.domain.ocr.OpenAiClient;
import com.keepeat.backend.domain.recipe.entity.Recipe;
import com.keepeat.backend.domain.recipe.entity.RecipeIngredient;
import com.keepeat.backend.domain.recipe.repository.RecipeRepository;
import com.keepeat.backend.domain.subcategory.SubCategory;
import com.keepeat.backend.domain.subcategory.SubCategoryRepository;
import com.keepeat.backend.domain.useringredient.UserIngredient;
import com.keepeat.backend.domain.useringredient.UserIngredientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.core.type.TypeReference;
import com.keepeat.backend.domain.useringredient.dto.UserIngredientResponse;
import com.keepeat.backend.domain.notification.service.NotificationService;
import com.keepeat.backend.domain.user.entity.AppUser;
import com.keepeat.backend.domain.user.entity.Role;
import com.keepeat.backend.domain.user.repository.AppUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Transactional
class UserIngredientRecipeFlowIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private SubCategoryRepository subCategoryRepository;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private IngredientStorageRepository ingredientStorageRepository;

    @Autowired
    private IngredientAliasRepository ingredientAliasRepository;

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private UserIngredientRepository userIngredientRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @MockitoBean
    private OpenAiClient openAiClient;

    @MockitoBean
    private NotificationService notificationService;

    private Long user1Id;
    private Long user2Id;
    private Long onionId;
    private Long porkId;
    private Long saltId;
    private Long beefId;
    private Long recipeId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        // 사용자 2명
        AppUser user1 = new AppUser("user1", "user1@test.com", "google", "g1", Role.ROLE_USER);
        AppUser user2 = new AppUser("user2", "user2@test.com", "google", "g2", Role.ROLE_USER);
        appUserRepository.save(user1);
        appUserRepository.save(user2);
        user1Id = user1.getId();
        user2Id = user2.getId();

        // 마스터 데이터
        Category meat = categoryRepository.save(Category.builder().name("육류").build());
        Category vegetable = categoryRepository.save(Category.builder().name("채소").build());
        Category seasoning = categoryRepository.save(Category.builder().name("양념/소스").build());

        SubCategory porkSub = subCategoryRepository.save(SubCategory.builder().category(meat).name("돼지고기").build());
        SubCategory beefSub = subCategoryRepository.save(SubCategory.builder().category(meat).name("소고기").build());
        SubCategory rootSub = subCategoryRepository.save(SubCategory.builder().category(vegetable).name("뿌리채소").build());
        SubCategory saltSub = subCategoryRepository.save(SubCategory.builder().category(seasoning).name("소금/후추").build());

        Ingredient pork = ingredientRepository.save(Ingredient.builder().subCategory(porkSub).name("삼겹살").build());
        Ingredient beef = ingredientRepository.save(Ingredient.builder().subCategory(beefSub).name("소고기").build());
        Ingredient onion = ingredientRepository.save(Ingredient.builder().subCategory(rootSub).name("양파").build());
        Ingredient salt = ingredientRepository.save(Ingredient.builder().subCategory(saltSub).name("소금").build());
        porkId = pork.getId();
        beefId = beef.getId();
        onionId = onion.getId();
        saltId = salt.getId();

        ingredientStorageRepository.save(IngredientStorage.builder()
                .ingredient(pork).storageType(StorageType.냉장).min(3).max(5).metric(Metric.Days).build());
        ingredientStorageRepository.save(IngredientStorage.builder()
                .ingredient(beef).storageType(StorageType.냉장).min(2).max(3).metric(Metric.Days).build());
        ingredientStorageRepository.save(IngredientStorage.builder()
                .ingredient(onion).storageType(StorageType.냉장).min(10).max(14).metric(Metric.Days).build());
        ingredientStorageRepository.save(IngredientStorage.builder()
                .ingredient(salt).storageType(StorageType.실온).min(1).max(2).metric(Metric.Years).build());

        // 별칭: "우육" → 소고기
        ingredientAliasRepository.save(new IngredientAlias(beef, "우육"));

        // 레시피: 삼겹살 + 양파 + 소금
        Recipe recipe = Recipe.builder()
                .recipeName("간단 볶음")
                .difficulty(Difficulty.하)
                .cookingMethod(CookingMethod.볶음)
                .cookingTime("10분")
                .calories(500)
                .instructions("볶는다.")
                .createdAt(LocalDate.now())
                .build();
        recipe = recipeRepository.save(recipe);
        recipeId = recipe.getId();

        // RecipeIngredient는 cascade 없이 별도 저장이 안 되므로, recipe의 컬렉션에 추가하고 flush
        recipe.getRequiredIngredients().add(
                RecipeIngredient.builder().recipe(recipe).ingredient(pork).amount("300g").build());
        recipe.getRequiredIngredients().add(
                RecipeIngredient.builder().recipe(recipe).ingredient(onion).amount("1개").build());
        recipe.getRequiredIngredients().add(
                RecipeIngredient.builder().recipe(recipe).ingredient(salt).amount("약간").build());
        entityManager.flush();
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor auth(Long userId) {
        return authentication(new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @Test
    @DisplayName("시나리오 B — 요리 완료 흐름: 등록 → 레시피별 조회 → 다중 삭제")
    void cookingFlow_happyPath() throws Exception {
        // 1. 등록: 양파 2행, 삼겹살 1행, 커스텀 김치 1행
        String createBody = """
                {"items":[
                    {"ingredientId":%d,"storageType":"냉장","purchaseDate":"2026-05-22","quantity":1,"unit":"개"},
                    {"ingredientId":%d,"storageType":"냉장","purchaseDate":"2026-05-22","quantity":1,"unit":"개"},
                    {"ingredientId":%d,"storageType":"냉장","purchaseDate":"2026-05-22","quantity":300,"unit":"g"},
                    {"ingredientId":null,"customName":"자취표 김치","storageType":"냉장","purchaseDate":"2026-05-22","expiryDate":"2026-06-30","quantity":1,"unit":"통"}
                ]}
                """.formatted(onionId, onionId, porkId);

        MvcResult createResult = mockMvc.perform(post("/api/v1/user-ingredients")
                        .with(auth(user1Id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();

        List<UserIngredientResponse> created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                new TypeReference<List<UserIngredientResponse>>() {});
        assertThat(created).hasSize(4);

        // 2. 레시피별 보유 식재료 조회
        mockMvc.perform(get("/api/v1/user-ingredients/by-recipe/{recipeId}", recipeId)
                        .with(auth(user1Id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.name == '양파')].id").isArray())
                .andExpect(jsonPath("$[?(@.name == '삼겹살')].id").isArray())
                .andExpect(jsonPath("$[?(@.customName == '자취표 김치')]").isEmpty())
                .andExpect(jsonPath("$[?(@.name == '소금')]").isEmpty())
                .andExpect(jsonPath("$[*].isCustom").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(false))));

        // 3. 양파 한 개 + 삼겹살 삭제
        Long onionUi1 = created.stream().filter(r -> "양파".equals(r.name())).findFirst().get().id();
        Long porkUi = created.stream().filter(r -> "삼겹살".equals(r.name())).findFirst().get().id();

        mockMvc.perform(delete("/api/v1/user-ingredients")
                        .with(auth(user1Id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[%d,%d]}".formatted(onionUi1, porkUi)))
                .andExpect(status().isNoContent());

        // 4. 남은 행: 양파 1, 김치 1
        mockMvc.perform(get("/api/v1/user-ingredients")
                        .with(auth(user1Id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("시나리오 C — 다중 삭제: 타인 id 섞이면 전부 롤백되고 404")
    void deleteMany_otherUserMixed_404AndRollback() throws Exception {
        // user1: 양파 2행
        String user1Body = """
                {"items":[
                    {"ingredientId":%d,"storageType":"냉장","purchaseDate":"2026-05-22","quantity":1,"unit":"개"},
                    {"ingredientId":%d,"storageType":"냉장","purchaseDate":"2026-05-22","quantity":1,"unit":"개"}
                ]}
                """.formatted(onionId, onionId);
        MvcResult r1 = mockMvc.perform(post("/api/v1/user-ingredients")
                        .with(auth(user1Id))
                        .contentType(MediaType.APPLICATION_JSON).content(user1Body))
                .andExpect(status().isCreated()).andReturn();
        List<UserIngredientResponse> user1Items = objectMapper.readValue(
                r1.getResponse().getContentAsString(),
                new TypeReference<List<UserIngredientResponse>>() {});
        Long u1a = user1Items.get(0).id();
        Long u1b = user1Items.get(1).id();

        // user2: 삼겹살 1행
        String user2Body = """
                {"items":[
                    {"ingredientId":%d,"storageType":"냉장","purchaseDate":"2026-05-22","quantity":300,"unit":"g"}
                ]}
                """.formatted(porkId);
        MvcResult r2 = mockMvc.perform(post("/api/v1/user-ingredients")
                        .with(auth(user2Id))
                        .contentType(MediaType.APPLICATION_JSON).content(user2Body))
                .andExpect(status().isCreated()).andReturn();
        List<UserIngredientResponse> user2Items = objectMapper.readValue(
                r2.getResponse().getContentAsString(),
                new TypeReference<List<UserIngredientResponse>>() {});
        Long u2c = user2Items.get(0).id();

        // user1이 user2의 id를 섞어 삭제 요청 → 404
        mockMvc.perform(delete("/api/v1/user-ingredients")
                        .with(auth(user1Id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[%d,%d]}".formatted(u1a, u2c)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_INGREDIENT_NOT_FOUND"));

        // user1의 두 행 모두 살아있어야 함 (롤백)
        mockMvc.perform(get("/api/v1/user-ingredients").with(auth(user1Id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // user2의 행도 그대로
        mockMvc.perform(get("/api/v1/user-ingredients").with(auth(user2Id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("시나리오 C — 다중 삭제: 빈 ids → 400")
    void deleteMany_emptyIds_400() throws Exception {
        mockMvc.perform(delete("/api/v1/user-ingredients")
                        .with(auth(user1Id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("시나리오 D — 레시피별 조회: 존재하지 않는 recipeId → 404")
    void findByRecipe_recipeNotFound_404() throws Exception {
        mockMvc.perform(get("/api/v1/user-ingredients/by-recipe/999999")
                        .with(auth(user1Id)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECIPE_NOT_FOUND"));
    }

    @Test
    @DisplayName("시나리오 D — 레시피별 조회: 매칭 0건이면 204")
    void findByRecipe_noMatch_204() throws Exception {
        // user1: 커스텀 김치만 보유
        String body = """
                {"items":[
                    {"ingredientId":null,"customName":"자취표 김치","storageType":"냉장","purchaseDate":"2026-05-22","expiryDate":"2026-06-30","quantity":1,"unit":"통"}
                ]}
                """;
        mockMvc.perform(post("/api/v1/user-ingredients")
                        .with(auth(user1Id))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/user-ingredients/by-recipe/{recipeId}", recipeId)
                        .with(auth(user1Id)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("기능 1 E2E — OCR 별칭 매칭 시 응답은 마스터 식재료 ID/이름으로 치환")
    void ocr_aliasMatch_resolvedToMaster() throws Exception {
        // OpenAI client stub: 영수증에 "우육 500g" 한 줄이 있다고 가정
        given(openAiClient.extractIngredients(any(byte[].class), anyString()))
                .willReturn(List.of(
                        new OpenAiClient.ParsedIngredient("우육 500g", "우육", null, 500.0, "g")
                ));

        MockMultipartFile image = new MockMultipartFile(
                "image", "receipt.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/ocr/parse")
                        .file(image)
                        .with(auth(user1Id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates[0].matchStatus").value("MATCHED"))
                .andExpect(jsonPath("$.candidates[0].ingredientId").value(beefId.intValue()))
                .andExpect(jsonPath("$.candidates[0].ingredientName").value("소고기"))
                .andExpect(jsonPath("$.candidates[0].storageOptions[0].storageType").value("냉장"));
    }
}
