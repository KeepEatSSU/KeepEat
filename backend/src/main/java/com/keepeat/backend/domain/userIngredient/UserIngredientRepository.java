package com.keepeat.backend.domain.userIngredient;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserIngredientRepository extends JpaRepository<UserIngredient, Long> {

    @Query("SELECT ui FROM UserIngredient ui " +
            "JOIN FETCH ui.ingredient i " +
            "WHERE ui.userId = :userId " +
            "ORDER BY ui.expiryDate ASC NULLS LAST")
    List<UserIngredient> findAllByUserIdOrderByExpiryDate(@Param("userId") Long userId);

    @Query("SELECT ui FROM UserIngredient ui " +
            "JOIN FETCH ui.ingredient i " +
            "WHERE ui.userId = :userId " +
            "AND i.name LIKE %:q% " +
            "ORDER BY ui.expiryDate ASC NULLS LAST")
    List<UserIngredient> searchByIngredientName(
            @Param("userId") Long userId,
            @Param("q") String q
    );



    Optional<UserIngredient> findByIdAndUserId(Long id, Long userId);
}
