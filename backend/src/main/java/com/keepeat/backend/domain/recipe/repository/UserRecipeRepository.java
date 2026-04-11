package com.keepeat.backend.domain.recipe.repository;

import com.keepeat.backend.domain.recipe.entity.UserRecipe;
import com.keepeat.backend.domain.userIngredient.UserIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRecipeRepository extends JpaRepository<UserRecipe, Long> {
    @Modifying
    @Query("DELETE FROM UserRecipe ur WHERE ur.userId = :userId AND ur.recipe.id IN :recipeIds")
    void deleteAllByUserIdAndRecipeIds(@Param("userId") Long userId, @Param("recipeIds") List<Long> recipeIds);

    @Query("SELECT ur FROM UserRecipe ur " +
            "JOIN FETCH ur.recipe r " +
            "WHERE ur.userId = :userId")
    List<UserRecipe> findAllByUserId(@Param("userId") Long userId);

    boolean existsByUserIdAndRecipeId(Long userId, Long recipeId);
}
