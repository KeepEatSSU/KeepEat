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
    OCR_PARSE_FAILURE(HttpStatus.UNPROCESSABLE_ENTITY, "이미지에서 식재료를 추출할 수 없습니다."),

    // 레시피
    RECIPE_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 레시피를 찾을 수 없습니다."),
    USER_RECIPE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 레시피에 접근할 권한이 없습니다."),
    DUPLICATE_RECIPE(HttpStatus.CONFLICT, "이미 보관함에 저장된 레시피입니다."),
    INSUFFICIENT_INGREDIENTS(HttpStatus.BAD_REQUEST, "레시피 생성을 위해 최소 3개 이상의 식재료와 3개 이상의 조미료가 필요합니다."),
    AI_API_FAILURE(HttpStatus.BAD_GATEWAY, "AI 레시피 생성에 실패했습니다. 잠시 후 다시 시도해주세요."),
    AI_RESPONSE_PARSE_FAILURE(HttpStatus.INTERNAL_SERVER_ERROR, "AI 응답을 처리하는 중 오류가 발생했습니다."),

    // 유저
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 유저를 찾을 수 없습니다."),

    // 어드민
    ADMIN_ACCESS_DENIED(HttpStatus.FORBIDDEN, "어드민 권한이 필요합니다."),
    INGREDIENT_NAME_DUPLICATED(HttpStatus.CONFLICT, "이미 존재하는 식재료입니다."),
    INGREDIENT_NOT_PENDING(HttpStatus.CONFLICT, "해당 식재료는 PENDING 상태가 아닙니다.");

    private final HttpStatus status;
    private final String message;
}
