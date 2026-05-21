package com.keepeat.backend.domain.recipe.entity;


import com.keepeat.backend.domain.user.entity.AppUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "user_recipe", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_user_recipe",
                columnNames = {"app_user_id", "recipe_id"}
        )},
        indexes = {
                @Index(name = "idx_user_recipe_recipe", columnList = "recipe_id")
        }
)
public class UserRecipe {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_user_id", nullable = false)
    private AppUser appUser;

    @Column(nullable = false)
    private LocalDate createdAt;

    public UserRecipe(Recipe recipe, AppUser appUser, LocalDate createdAt){
        this.recipe = recipe;
        this.appUser = appUser;
        this.createdAt = createdAt;
    }
}
