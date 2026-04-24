package com.keepeat.backend.domain.recipe.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "user_recipe")
public class UserRecipe {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;


    //여기는 User 테이블과 연결될 컬럼임
    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDate createdAt;

    public UserRecipe(Recipe recipe, long userId, LocalDate createdAt){
        this.recipe = recipe;
        this.userId = userId;
        this.createdAt = createdAt;
    }
}
