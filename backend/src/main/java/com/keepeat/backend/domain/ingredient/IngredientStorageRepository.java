package com.keepeat.backend.domain.ingredient;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;

public interface IngredientStorageRepository extends JpaRepository<IngredientStorage, Long> {

    List<IngredientStorage> findAllByIngredientId(Long ingredientId);

    List<IngredientStorage> findAllByIngredientIdIn(List<Long> ingredientIds);

    @Modifying
    void deleteByIngredientId(Long ingredientId);
}
