package com.keepeat.backend.domain.notification.entity;

public enum NotificationType {
    NOTICE,         // notice (공지사항), SYSTEM_NOTICE
    EXPIRY_SOON,    // expiry_soon (소비기한 임박), INGREDIENT_EXPIRY
    WEEKLY_CHEER,   // weekly_cheer (주간 응원)
    RECIPE_READY    // recipe_ready (레시피 추천 완료), OCR_RESULT
}