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
    INSUFFICIENT_INGREDIENTS(HttpStatus.BAD_REQUEST, "레시피 생성을 위해 최소 세 종류 이상의 식재료와 세 종류 이상의 조미료가 필요합니다."),
    AI_API_FAILURE(HttpStatus.BAD_GATEWAY, "AI 레시피 생성에 실패했습니다. 잠시 후 다시 시도해주세요."),
    AI_RESPONSE_PARSE_FAILURE(HttpStatus.INTERNAL_SERVER_ERROR, "AI 응답을 처리하는 중 오류가 발생했습니다."),
    RECIPE_NOT_BOOKMARKED(HttpStatus.FORBIDDEN, "내 레시피에 등록되지 않은 레시피 입니다."),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "잘못된 커서 형식입니다."),
    RECIPE_GENERATING(HttpStatus.CONFLICT, "레시피 생성 중에는 레시피 생성 요청을 할 수 없습니다."),
    RECIPE_JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "진행 중이거나 완료된 레시피 생성 작업이 없습니다."),

    // 유저
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 유저를 찾을 수 없습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 일치하지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 토큰입니다."),
    OAUTH_ACCOUNT_CONFLICT(HttpStatus.CONFLICT, "동일한 이메일로 가입된 다른 로그인 방식의 계정이 있습니다."),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),

    // 알림
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),

    // 어드민
    ADMIN_ACCESS_DENIED(HttpStatus.FORBIDDEN, "어드민 권한이 필요합니다."),
    INGREDIENT_NAME_DUPLICATED(HttpStatus.CONFLICT, "이미 존재하는 식재료입니다."),
    INGREDIENT_NOT_PENDING(HttpStatus.CONFLICT, "해당 식재료는 PENDING 상태가 아닙니다."),
    REPLACE_TARGET_NOT_ACTIVE(HttpStatus.CONFLICT, "교체 대상은 ACTIVE 상태의 식재료여야 합니다."),
    REPLACE_TARGET_SAME(HttpStatus.BAD_REQUEST, "교체 대상은 자기 자신과 같을 수 없습니다."),
    RECIPE_HAS_BOOKMARK(HttpStatus.CONFLICT, "사용자가 내 레시피로 등록한 레시피는 삭제할 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
