package com.keepeat.backend.domain.ingredient;

import com.keepeat.backend.domain.common.enums.IngredientStatus;
import com.keepeat.backend.domain.subcategory.SubCategory;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "ingredient")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_category_id", nullable = false)
    private SubCategory subCategory;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'ACTIVE'")
    private IngredientStatus status;

    @Builder
    public Ingredient(SubCategory subCategory, String name, IngredientStatus status) {
        this.subCategory = subCategory;
        this.name = name;
        this.status = (status != null) ? status : IngredientStatus.ACTIVE;
    }

    public void update(SubCategory subCategory, String name) {
        this.subCategory = subCategory;
        this.name = name;
    }
}
