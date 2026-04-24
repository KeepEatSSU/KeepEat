package com.keepeat.backend.domain.recipe.repository;

import com.keepeat.backend.domain.recipe.entity.UserRecipe;
import com.keepeat.backend.domain.userIngredient.UserIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRecipeRepository extends JpaRepository<UserRecipe, Long> {
    @Modifying
    @Query("DELETE FROM UserRecipe ur WHERE ur.appUser.id = :userId AND ur.recipe.id IN :recipeIds")
    void deleteAllByAppUserIdAndRecipeIds(@Param("userId") Long userId, @Param("recipeIds") List<Long> recipeIds);

    @Query("SELECT ur FROM UserRecipe ur " +
            "JOIN FETCH ur.recipe r " +
            "WHERE ur.appUser.id = :userId")
    List<UserRecipe> findAllByUserId(@Param("userId") Long userId);

    Optional<UserRecipe> findByAppUserIdAndRecipeId(Long userId, Long recipeId);


    @Query("SELECT COUNT(ur) FROM UserRecipe ur WHERE ur.appUser.id = :userId AND ur.recipe.id IN :recipeIds")
    long countByAppUserIdAndRecipeIds(@Param("userId") Long userId, @Param("recipeIds") List<Long> recipeIds);
}
