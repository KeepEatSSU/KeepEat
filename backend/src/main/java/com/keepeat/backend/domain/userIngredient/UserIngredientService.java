package com.keepeat.backend.domain.userIngredient;

import com.keepeat.backend.domain.common.exception.ErrorCode;
import com.keepeat.backend.domain.common.exception.KeepEatException;
import com.keepeat.backend.domain.userIngredient.dto.UserIngredientRequest;
import com.keepeat.backend.domain.userIngredient.dto.UserIngredientResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserIngredientService {

    private final UserIngredientRepository userIngredientRepository;

    public List<UserIngredientResponse> findAll(Long userId) {
        return userIngredientRepository.findAllByUserIdOrderByExpiryDate(userId)
                .stream()
                .map(ui -> UserIngredientResponse.of(ui, calculateDaysLeft(ui.getExpiryDate())))
                .toList();
    }

    public List<UserIngredientResponse> search(Long userId, String q) {
        return userIngredientRepository.searchByIngredientName(userId, q)
                .stream()
                .map(ui -> UserIngredientResponse.of(ui, calculateDaysLeft(ui.getExpiryDate())))
                .toList();
    }

    @Transactional
    public UserIngredientResponse update(Long userId, Long id, UserIngredientRequest request) {
        UserIngredient ui = userIngredientRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new KeepEatException(ErrorCode.USER_INGREDIENT_NOT_FOUND));

        ui.update(request);

        return UserIngredientResponse.of(ui, calculateDaysLeft(ui.getExpiryDate()));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        UserIngredient ui = userIngredientRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new KeepEatException(ErrorCode.USER_INGREDIENT_NOT_FOUND));

        userIngredientRepository.delete(ui);
    }

    private Long calculateDaysLeft(LocalDate expiryDate) {
        if (expiryDate == null) return null;
        return ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
    }
}
