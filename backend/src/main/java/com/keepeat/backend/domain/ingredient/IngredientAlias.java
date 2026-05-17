package com.keepeat.backend.domain.ingredient;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "ingredient_alias")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IngredientAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Column(nullable = false, unique = true)
    private String aliasName;

    public IngredientAlias(Ingredient ingredient, String aliasName){
        this.ingredient = ingredient;
        this.aliasName = aliasName;
    }
}
