package com.keepeat.backend.domain.ingredient;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface IngredientAliasRepository extends JpaRepository<IngredientAlias, Long> {

    @EntityGraph(attributePaths = "ingredient")
    List<IngredientAlias> findByAliasNameIn(Collection<String> aliasNames);
}
