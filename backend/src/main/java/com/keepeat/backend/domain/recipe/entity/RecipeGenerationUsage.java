package com.keepeat.backend.domain.recipe.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(
        name = "recipe_generation_usage",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_recipe_generation_usage_user_date",
                        columnNames = {"user_id", "usage_date"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecipeGenerationUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    public RecipeGenerationUsage(Long userId, LocalDate usageDate) {
        this.userId = userId;
        this.usageDate = usageDate;
    }
}
