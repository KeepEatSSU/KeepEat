package com.keepeat.backend.domain.recipe.repository;

import com.keepeat.backend.domain.admin.dto.AdminRecipeListItemDto;
import com.keepeat.backend.domain.recipe.dto.RecipeListItemDto;
import com.keepeat.backend.domain.recipe.entity.Recipe;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    @Query("SELECT r FROM Recipe r " +
            "LEFT JOIN FETCH r.requiredIngredients ri " +
            "JOIN FETCH ri.ingredient " +
            "WHERE r.id = :recipeId")
    Optional<Recipe> findByIdWithIngredient(@Param("recipeId") Long recipeId);

    // LIKE 조회로 인해 성능이 안좋다면 postgre의 n-gram? 이거 쓰는 것 까지 생각해놓음.
    @Query("""
    SELECT new com.keepeat.backend.domain.recipe.dto.RecipeListItemDto(
        r.id, r.recipeName, r.difficulty, r.cookingMethod,
        r.cookingTime, r.calories, r.createdAt
    )
    FROM Recipe r
    WHERE (cast(:keyword as string) IS NULL OR r.id IN (
            SELECT DISTINCT r2.id
            FROM Recipe r2
            LEFT JOIN r2.requiredIngredients ri
            LEFT JOIN ri.ingredient i
            LEFT JOIN IngredientAlias a ON a.ingredient = i
            WHERE LOWER(r2.recipeName) LIKE LOWER(CONCAT('%', cast(:keyword as string), '%'))
               OR LOWER(i.name)        LIKE LOWER(CONCAT('%', cast(:keyword as string), '%'))
               OR LOWER(a.aliasName)   LIKE LOWER(CONCAT('%', cast(:keyword as string), '%'))
          ))
      AND (cast(:lastCreatedAt as date) IS NULL
           OR r.createdAt < :lastCreatedAt
           OR (r.createdAt = :lastCreatedAt AND r.id < :lastId))
    ORDER BY r.createdAt DESC, r.id DESC
    """)
    List<RecipeListItemDto> searchRecipes(
            @Param("keyword") String keyword,
            @Param("lastCreatedAt") LocalDate lastCreatedAt,
            @Param("lastId") Long lastId,
            Pageable pageable
    );


    @Query(value = """
    SELECT new com.keepeat.backend.domain.admin.dto.AdminRecipeListItemDto(
        r.id, r.recipeName, r.createdAt
    )
    FROM Recipe r
    WHERE (cast(:keyword as string) IS NULL OR LOWER(r.recipeName) LIKE LOWER(CONCAT('%', cast(:keyword as string), '%')))
      AND (:onlyDeletable = false OR NOT EXISTS (
          SELECT 1 FROM UserRecipe ur WHERE ur.recipe = r
      ))
    ORDER BY r.id DESC
""",
            countQuery = """
    SELECT COUNT(r) FROM Recipe r
    WHERE (cast(:keyword as string) IS NULL OR LOWER(r.recipeName) LIKE LOWER(CONCAT('%', cast(:keyword as string), '%')))
      AND (:onlyDeletable = false OR NOT EXISTS (
          SELECT 1 FROM UserRecipe ur WHERE ur.recipe = r
      ))
""")
    Page<AdminRecipeListItemDto> searchForAdmin(
            @Param("keyword") String keyword,
            @Param("onlyDeletable") boolean onlyDeletable,
            Pageable pageable
    );

    @Query(value = """
    SELECT new com.keepeat.backend.domain.admin.dto.AdminRecipeListItemDto(
        r.id, r.recipeName, r.createdAt
    )
    FROM Recipe r
    LEFT JOIN RecipeReaction rr
           ON rr.recipe = r
          AND rr.reactionType = com.keepeat.backend.domain.common.enums.ReactionType.LIKE
    WHERE (cast(:keyword as string) IS NULL OR LOWER(r.recipeName) LIKE LOWER(CONCAT('%', cast(:keyword as string), '%')))
      AND (:onlyDeletable = false OR NOT EXISTS (
          SELECT 1 FROM UserRecipe ur WHERE ur.recipe = r
      ))
    GROUP BY r.id, r.recipeName, r.createdAt
    ORDER BY COUNT(rr.id) DESC, r.id DESC
    """,
            countQuery = """
    SELECT COUNT(r) FROM Recipe r
    WHERE (cast(:keyword as string) IS NULL OR LOWER(r.recipeName) LIKE LOWER(CONCAT('%', cast(:keyword as string), '%')))
      AND (:onlyDeletable = false OR NOT EXISTS (
          SELECT 1 FROM UserRecipe ur WHERE ur.recipe = r
      ))
    """)
    Page<AdminRecipeListItemDto> searchForAdminOrderByLikes(
            @Param("keyword") String keyword,
            @Param("onlyDeletable") boolean onlyDeletable,
            Pageable pageable
    );

    @Query(value = """
    SELECT new com.keepeat.backend.domain.admin.dto.AdminRecipeListItemDto(
        r.id, r.recipeName, r.createdAt
    )
    FROM Recipe r
    LEFT JOIN RecipeReaction rr
           ON rr.recipe = r
          AND rr.reactionType = com.keepeat.backend.domain.common.enums.ReactionType.DISLIKE
    WHERE (cast(:keyword as string) IS NULL OR LOWER(r.recipeName) LIKE LOWER(CONCAT('%', cast(:keyword as string), '%')))
      AND (:onlyDeletable = false OR NOT EXISTS (
          SELECT 1 FROM UserRecipe ur WHERE ur.recipe = r
      ))
    GROUP BY r.id, r.recipeName, r.createdAt
    ORDER BY COUNT(rr.id) DESC, r.id DESC
    """,
            countQuery = """
    SELECT COUNT(r) FROM Recipe r
    WHERE (cast(:keyword as string) IS NULL OR LOWER(r.recipeName) LIKE LOWER(CONCAT('%', cast(:keyword as string), '%')))
      AND (:onlyDeletable = false OR NOT EXISTS (
          SELECT 1 FROM UserRecipe ur WHERE ur.recipe = r
      ))
    """)
    Page<AdminRecipeListItemDto> searchForAdminOrderByDislikes(
            @Param("keyword") String keyword,
            @Param("onlyDeletable") boolean onlyDeletable,
            Pageable pageable
    );

    @Query(value = """
    SELECT new com.keepeat.backend.domain.admin.dto.AdminRecipeListItemDto(
        r.id, r.recipeName, r.createdAt
    )
    FROM Recipe r
    LEFT JOIN UserRecipe ur2 ON ur2.recipe = r
    WHERE (cast(:keyword as string) IS NULL OR LOWER(r.recipeName) LIKE LOWER(CONCAT('%', cast(:keyword as string), '%')))
      AND (:onlyDeletable = false OR NOT EXISTS (
          SELECT 1 FROM UserRecipe ur WHERE ur.recipe = r
      ))
    GROUP BY r.id, r.recipeName, r.createdAt
    ORDER BY COUNT(ur2.id) ASC, r.id DESC
    """, countQuery = """
    SELECT COUNT(r) FROM Recipe r
    WHERE (cast(:keyword as string) IS NULL OR LOWER(r.recipeName) LIKE LOWER(CONCAT('%', cast(:keyword as string), '%')))
      AND (:onlyDeletable = false OR NOT EXISTS (
          SELECT 1 FROM UserRecipe ur WHERE ur.recipe = r
      ))
    """)
    Page<AdminRecipeListItemDto> searchForAdminOrderByBookmarks(
            @Param("keyword") String keyword,
            @Param("onlyDeletable") boolean onlyDeletable,
            Pageable pageable
    );
}

