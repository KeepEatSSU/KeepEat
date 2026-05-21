package com.keepeat.backend.domain.recipe.repository;

import com.keepeat.backend.domain.recipe.dto.ReactionCountDto;
import com.keepeat.backend.domain.recipe.entity.UserRecipe;
import com.keepeat.backend.domain.useringredient.UserIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

import java.time.LocalDate;

public interface UserRecipeRepository extends JpaRepository<UserRecipe, Long> {
    @Modifying
    @Query("DELETE FROM UserRecipe ur WHERE ur.appUser.id = :userId AND ur.recipe.id IN :recipeIds")
    void deleteAllByAppUserIdAndRecipeIds(@Param("userId") Long userId, @Param("recipeIds") List<Long> recipeIds);

    @Query("SELECT ur FROM UserRecipe ur " +
            "JOIN FETCH ur.recipe r " +
            "WHERE ur.appUser.id = :userId")
    List<UserRecipe> findAllByUserId(@Param("userId") Long userId);


    Optional<UserRecipe> findByAppUserIdAndRecipeId(Long userId, Long recipeId);


    void deleteAllByAppUserId(Long userId);


    @Query("SELECT COUNT(ur) FROM UserRecipe ur WHERE ur.appUser.id = :userId AND ur.recipe.id IN :recipeIds")
    long countByAppUserIdAndRecipeIds(@Param("userId") Long userId, @Param("recipeIds") List<Long> recipeIds);


    // 특정 유저가 '특정 날짜 이후'로 요리를 몇 번 했는지 세어주는 쿼리
    int countByAppUser_IdAndCreatedAtAfter(Long userId, LocalDate date);

    @Query("""
    SELECT new com.keepeat.backend.domain.recipe.dto.ReactionCountDto(ur.recipe.id, COUNT(ur))
    FROM UserRecipe ur
    WHERE ur.recipe.id IN :recipeIds
    GROUP BY ur.recipe.id
    """)
    List<ReactionCountDto> countByRecipeIds(@Param("recipeIds") List<Long> recipeIds);

    long countByRecipeId(Long recipeId);


}
