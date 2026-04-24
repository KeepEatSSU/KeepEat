package com.keepeat.backend.domain.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다."),
    SUBCATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "서브카테고리를 찾을 수 없습니다."),
    INGREDIENT_NOT_FOUND(HttpStatus.NOT_FOUND, "식재료를 찾을 수 없습니다."),
    INGREDIENT_STORAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "식재료 보관 정보를 찾을 수 없습니다."),
    USER_INGREDIENT_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자 식재료를 찾을 수 없습니다."),
    USER_INGREDIENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 식재료에 접근할 수 없습니다."),
    // OCR
    OCR_API_FAILURE(HttpStatus.BAD_GATEWAY, "OCR API 호출에 실패했습니다."),
    OCR_INVALID_IMAGE(HttpStatus.BAD_REQUEST, "유효하지 않은 이미지 형식입니다."),
    OCR_PARSE_FAILURE(HttpStatus.UNPROCESSABLE_ENTITY, "이미지에서 식재료를 추출할 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
