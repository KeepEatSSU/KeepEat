package com.keepeat.backend.domain.user.service;

/**
 * 인증번호 상태 변경은 커밋하되 클라이언트에는 검증 실패를 알리기 위한 예외입니다.
 */
public class EmailVerificationException extends IllegalArgumentException {

    public EmailVerificationException(String message) {
        super(message);
    }
}
