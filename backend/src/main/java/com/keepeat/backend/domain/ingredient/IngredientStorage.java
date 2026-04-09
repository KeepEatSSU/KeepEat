package com.keepeat.backend.domain.ingredient;

import com.keepeat.backend.domain.common.enums.Metric;
import com.keepeat.backend.domain.common.enums.StorageType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ingredient_storage", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"ingredient_id", "storage_type"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IngredientStorage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false)
    private StorageType storageType;

    @Column(nullable = false)
    private Integer min;

    @Column(nullable = false)
    private Integer max;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Metric metric;

    @Builder
    public IngredientStorage(Ingredient ingredient, StorageType storageType, Integer min, Integer max, Metric metric) {
        this.ingredient = ingredient;
        this.storageType = storageType;
        this.min = min;
        this.max = max;
        this.metric = metric;
    }
}
