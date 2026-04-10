package com.keepeat.backend.domain.recipe;

import com.keepeat.backend.domain.common.enums.CookingMethod;
import com.keepeat.backend.domain.common.enums.Difficulty;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recipe {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String recipeName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CookingMethod cookingMethod;

    @Column(nullable = false)
    private String cookingTime;

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RecipeIngredient> requiredIngredients = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String instructions;

    @Column
    private LocalDate createdAt;

    @Builder
    public Recipe(String recipeName, Difficulty difficulty, CookingMethod cookingMethod, String cookingTime, String instructions, LocalDate createdAt){
        this.recipeName = recipeName;
        this.difficulty = difficulty;
        this.cookingMethod = cookingMethod;
        this.cookingTime = cookingTime;
        this.instructions = instructions;
        this.createdAt = createdAt;
    }
}
