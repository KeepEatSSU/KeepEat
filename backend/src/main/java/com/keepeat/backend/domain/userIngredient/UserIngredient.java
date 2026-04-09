package com.keepeat.backend.domain.userIngredient;

import com.keepeat.backend.domain.common.enums.StorageType;
import com.keepeat.backend.domain.ingredient.Ingredient;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.keepeat.backend.domain.userIngredient.dto.UserIngredientRequest;

import java.time.LocalDate;

@Entity
@Table(name = "user_ingredient")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false)
    private StorageType storageType;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    private Double quantity;

    private String unit;

    @Builder
    public UserIngredient(Long userId, Ingredient ingredient, StorageType storageType,
                          LocalDate purchaseDate, LocalDate expiryDate, Double quantity, String unit) {
        this.userId = userId;
        this.ingredient = ingredient;
        this.storageType = storageType;
        this.purchaseDate = purchaseDate;
        this.expiryDate = expiryDate;
        this.quantity = quantity;
        this.unit = unit;
    }

    public void update(UserIngredientRequest request) {
        if (request.storageType() != null) this.storageType = request.storageType();
        if (request.quantity() != null) this.quantity = request.quantity();
        if (request.unit() != null) this.unit = request.unit();
        if (request.expiryDate() != null) this.expiryDate = request.expiryDate();
        if (request.purchaseDate() != null) this.purchaseDate = request.purchaseDate();
    }
}
