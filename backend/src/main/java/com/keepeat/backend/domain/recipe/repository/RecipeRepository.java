package com.keepeat.backend.domain.recipe.repository;

import com.keepeat.backend.domain.recipe.entity.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    @Query("SELECT r FROM Recipe r " +
            "LEFT JOIN FETCH r.requiredIngredients i " +
            "WHERE r.id = :recipeId")
    Optional<Recipe> findByIdWithIngredient(@Param("recipeId") Long recipeId);
}

