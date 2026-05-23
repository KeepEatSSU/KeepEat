package com.keepeat.backend.domain.useringredient;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import java.time.LocalDate;

public interface UserIngredientRepository extends JpaRepository<UserIngredient, Long> {

    @Query("SELECT ui FROM UserIngredient ui " +
            "LEFT JOIN FETCH ui.ingredient i " +
            "LEFT JOIN FETCH i.subCategory sc " +
            "LEFT JOIN FETCH sc.category c " +
            "WHERE ui.userId = :userId " +
            "ORDER BY ui.expiryDate ASC NULLS LAST")
    List<UserIngredient> findAllByUserIdOrderByExpiryDate(@Param("userId") Long userId);

    @Query("SELECT ui FROM UserIngredient ui " +
            "LEFT JOIN FETCH ui.ingredient i " +
            "LEFT JOIN FETCH i.subCategory sc " +
            "LEFT JOIN FETCH sc.category c " +
            "WHERE ui.userId = :userId " +
            "AND (i.name LIKE %:q% OR ui.customName LIKE %:q%) " +
            "ORDER BY ui.expiryDate ASC NULLS LAST")
    List<UserIngredient> searchByIngredientName(
            @Param("userId") Long userId,
            @Param("q") String q
    );

    @Query("SELECT ui FROM UserIngredient ui " +
            "LEFT JOIN FETCH ui.ingredient i " +
            "LEFT JOIN FETCH i.subCategory sc " +
            "LEFT JOIN FETCH sc.category c " +
            "WHERE ui.id = :id AND ui.userId = :userId")
    Optional<UserIngredient> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    // 소비기한이 [from, to] 범위에 속하는 식재료를 모두 찾습니다. (D-3 ~ D-day 알림용)
    @Query("SELECT ui FROM UserIngredient ui JOIN AppUser u ON ui.userId = u.id " +
            "WHERE ui.expiryDate BETWEEN :from AND :to AND u.deletedAt IS NULL")
    List<UserIngredient> findAllByExpiryDateBetweenAndUserNotDeleted(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    @Query("SELECT ui FROM UserIngredient ui " +
            "LEFT JOIN FETCH ui.ingredient i " +
            "LEFT JOIN FETCH i.subCategory sc " +
            "LEFT JOIN FETCH sc.category c " +
            "WHERE ui.userId = :userId " +
            "AND ui.ingredient IS NOT NULL " +
            "AND ui.ingredient.id IN (" +
            "  SELECT ri.ingredient.id FROM RecipeIngredient ri WHERE ri.recipe.id = :recipeId" +
            ") " +
            "ORDER BY ui.expiryDate ASC NULLS LAST")
    List<UserIngredient> findAllByUserIdAndRecipeId(@Param("userId") Long userId,
                                                    @Param("recipeId") Long recipeId);

    long countByIdInAndUserId(Collection<Long> ids, Long userId);

    @Modifying
    @Query("DELETE FROM UserIngredient ui WHERE ui.id IN :ids AND ui.userId = :userId")
    void deleteAllByIdInAndUserId(@Param("ids") Collection<Long> ids, @Param("userId") Long userId);
}
