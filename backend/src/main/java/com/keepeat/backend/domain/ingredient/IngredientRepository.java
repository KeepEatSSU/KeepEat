package com.keepeat.backend.domain.ingredient;

import com.keepeat.backend.domain.common.enums.IngredientStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    Optional<Ingredient> findByName(String name);

    List<Ingredient> findByNameIn(List<String> names);

    @Query("""
            SELECT i FROM Ingredient i
            JOIN FETCH i.subCategory sc
            JOIN FETCH sc.category
            WHERE (:keyword IS NULL OR :keyword = '' OR i.name LIKE CONCAT('%', :keyword, '%'))
              AND (:subCategoryId IS NULL OR sc.id = :subCategoryId)
              AND (:status IS NULL OR i.status = :status)
            """)
    Page<Ingredient> searchForAdmin(@Param("keyword") String keyword,
                                    @Param("subCategoryId") Long subCategoryId,
                                    @Param("status") IngredientStatus status,
                                    Pageable pageable);

}
