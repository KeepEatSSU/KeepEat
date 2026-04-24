package com.keepeat.backend.domain.ingredient;

import com.keepeat.backend.domain.common.enums.StorageType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IngredientStorageRepository extends JpaRepository<IngredientStorage, Long> {

    Optional<IngredientStorage> findByIngredientIdAndStorageType(Long ingredientId, StorageType storageType);

    List<IngredientStorage> findAllByIngredientId(Long ingredientId);
}
