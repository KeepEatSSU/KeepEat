package com.keepeat.backend.domain.recipe.repository;

import com.keepeat.backend.domain.recipe.dto.RecipeListItemDto;
import com.keepeat.backend.domain.recipe.entity.Recipe;
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
    WHERE (:keyword IS NULL OR r.id IN (
            SELECT DISTINCT r2.id
            FROM Recipe r2
            LEFT JOIN r2.requiredIngredients ri
            LEFT JOIN ri.ingredient i
            LEFT JOIN IngredientAlias a ON a.ingredient = i
            WHERE LOWER(r2.recipeName) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(i.name)        LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(a.aliasName)   LIKE LOWER(CONCAT('%', :keyword, '%'))
          ))
      AND (:lastCreatedAt IS NULL
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
}

