package com.keepeat.backend.domain.recipe.entity;

import com.keepeat.backend.domain.common.enums.ReactionType;
import com.keepeat.backend.domain.user.entity.AppUser;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "recipe_reaction", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_recipe_reaction_user_recipe",
                columnNames = {"app_user_id", "recipe_id"}
        )},
        indexes = {
                @Index(name = "idx_recipe_reaction_recipe", columnList = "recipe_id"),
                @Index(name = "idx_recipe_reaction_recipe_type", columnList = "recipe_id, reaction_type")
        }
)
public class RecipeReaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_user_id", nullable = false)
    private AppUser appUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReactionType reactionType;

    @Column(nullable = false)
    private LocalDate createdAt;


    @Builder
    public RecipeReaction(Recipe recipe, AppUser appUser, ReactionType reactionType, LocalDate createdAt){
        this.recipe = recipe;
        this.appUser = appUser;
        this.reactionType = reactionType;
        this.createdAt = createdAt;
    }

    public void changeType(ReactionType newType){
        this.reactionType = newType;
    }
}
