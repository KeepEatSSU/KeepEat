package com.keepeat.backend.domain.useringredient;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

import java.time.LocalDate;

public interface UserIngredientRepository extends JpaRepository<UserIngredient, Long> {

    @Query("SELECT ui FROM UserIngredient ui " +
            "JOIN FETCH ui.ingredient i " +
            "JOIN FETCH i.subCategory sc " +
            "JOIN FETCH sc.category c " +
            "WHERE ui.userId = :userId " +
            "ORDER BY ui.expiryDate ASC NULLS LAST")
    List<UserIngredient> findAllByUserIdOrderByExpiryDate(@Param("userId") Long userId);

    @Query("SELECT ui FROM UserIngredient ui " +
            "JOIN FETCH ui.ingredient i " +
            "JOIN FETCH i.subCategory sc " +
            "JOIN FETCH sc.category c " +
            "WHERE ui.userId = :userId " +
            "AND i.name LIKE %:q% " +
            "ORDER BY ui.expiryDate ASC NULLS LAST")
    List<UserIngredient> searchByIngredientName(
            @Param("userId") Long userId,
            @Param("q") String q
    );

    @Query("SELECT ui FROM UserIngredient ui " +
            "JOIN FETCH ui.ingredient i " +
            "JOIN FETCH i.subCategory sc " +
            "JOIN FETCH sc.category c " +
            "WHERE ui.id = :id AND ui.userId = :userId")
    Optional<UserIngredient> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    // 특정 날짜(예: 3일 뒤)가 소비기한인 식재료들을 모두 찾습니다.
    List<UserIngredient> findAllByExpiryDate(LocalDate expiryDate);
}
