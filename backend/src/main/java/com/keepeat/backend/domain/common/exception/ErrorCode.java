package com.keepeat.backend.domain.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    CATEGORY_NOT_FOUND("카테고리를 찾을 수 없습니다."),
    INGREDIENT_NOT_FOUND("식재료를 찾을 수 없습니다."),
    INGREDIENT_STORAGE_NOT_FOUND("식재료 보관 정보를 찾을 수 없습니다."),
    USER_INGREDIENT_NOT_FOUND("사용자 식재료를 찾을 수 없습니다."),
    USER_INGREDIENT_ACCESS_DENIED("해당 식재료에 접근할 수 없습니다."),
    USER_INGREDIENT_ALREADY_EXISTS("이미 등록된 식재료입니다.");

    private final String message;
}
