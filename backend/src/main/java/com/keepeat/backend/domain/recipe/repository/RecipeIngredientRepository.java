package com.keepeat.backend.domain.recipe.repository;

import com.keepeat.backend.domain.ingredient.Ingredient;
import com.keepeat.backend.domain.recipe.entity.RecipeIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredient, Long> {

    @Modifying
    @Query("UPDATE RecipeIngredient ri SET ri.ingredient.id = :targetId WHERE ri.ingredient.id = :pendingId")
    int updateIngredient(@Param("pendingId") Long pendingId, @Param("targetId") Long targetId);
}
