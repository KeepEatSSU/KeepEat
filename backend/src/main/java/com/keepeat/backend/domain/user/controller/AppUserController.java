package com.keepeat.backend.domain.user.controller;

import com.keepeat.backend.domain.user.dto.*;
import com.keepeat.backend.domain.user.service.AppUserService;
import com.keepeat.backend.domain.user.service.EmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import java.util.Map;

@Tag(name = "User API", description = "사용자 인증 및 관리 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class AppUserController {

    private final EmailService emailService;
    private final AppUserService appUserService;
    private final EmailService emailService;

    // POST http://localhost:8080/api/users/signup
    @Operation(summary = "회원가입", description = "이메일, 비밀번호, 닉네임으로 회원가입을 진행합니다.")
    @PostMapping("/signup")
    public ResponseEntity<Long> signUp(@Valid @RequestBody SignUpRequest request) {

        Long savedUserId = appUserService.signUp(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(savedUserId);
    }

    // POST http://localhost:8080/api/users/login
    @Operation(summary = "로그인", description = "이메일과 비밀번호로 로그인하여 JWT 토큰을 발급받습니다.")
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {

        TokenResponse tokenResponse = appUserService.login(request);

        return ResponseEntity.ok(tokenResponse);
    }

    // POST http://localhost:8080/api/users/logout
    @Operation(summary = "로그아웃", description = "현재 사용자의 Refresh Token을 만료시킵니다.")
    @PostMapping("/logout")
    public ResponseEntity<String> logout(@AuthenticationPrincipal Long userId) {
        appUserService.logout(userId);
        return ResponseEntity.ok("로그아웃되었습니다.");
    }


    // POST http://localhost:8080/api/users/refresh
    @Operation(summary = "토큰 재발급", description = "Refresh Token을 사용하여 새로운 토큰 세트를 발급받습니다.")
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@RequestBody TokenRefreshRequest request) {
        // 서비스에게 재발급을 시키고 새로운 토큰 세트를 리턴
        TokenResponse newTokens = appUserService.refreshTokens(request);
        return ResponseEntity.ok(newTokens);
    }


    // GET http://localhost:8080/api/users/me
    @Operation(summary = "내 정보 조회", description = "현재 로그인된 사용자의 정보를 조회합니다.")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMyInfo(@AuthenticationPrincipal Long userId) {
        UserResponse userInfo = appUserService.getUserInfo(userId);
        return ResponseEntity.ok(userInfo);
    }


    @Operation(summary = "알림 수신 설정 변경", description = "전체 푸시 알림의 수신 여부를 토글(ON/OFF)합니다.")
    @PatchMapping("/me/notification-setting")
    public ResponseEntity<String> toggleNotificationSetting(@AuthenticationPrincipal Long userId) {

        boolean isEnabled = appUserService.toggleNotification(userId);
        String message = isEnabled ? "알림 수신이 켜졌습니다." : "알림 수신이 꺼졌습니다.";

        return ResponseEntity.ok(message);
    }

    // POST /api/v1/users/password/find
    @PostMapping("/password/find")
    public ResponseEntity<String> findPassword(@Valid @RequestBody PasswordFindRequest request) {

        appUserService.sendTemporaryPassword(request.email());

        return ResponseEntity.ok("입력하신 이메일로 임시 비밀번호가 발송되었습니다.");
    }

    @Operation(summary = "비밀번호 변경 (로그인 상태)", description = "로그인한 사용자가 현재 비밀번호를 인증한 후 새로운 비밀번호로 변경합니다.")
    @PatchMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Long userId, // JWT 토큰에서 유저 ID를 뽑아옵니다.
            @Valid @RequestBody PasswordChangeRequest request
    ) {
        appUserService.changePassword(userId, request);

        // 성공 시 데이터 반환 없이 204 No Content를 반환하는 것이 RESTful 규격에 맞습니다.
        return ResponseEntity.noContent().build();
    }


    // 인증번호 발송 API
    @Operation(summary = "회원가입 이메일 인증번호 전송", description = "회원가입 시 로그인 인증을 진행합니다.")
    @PostMapping("/send")
    public ResponseEntity<String> sendEmailAuth(@Valid @RequestBody EmailSendRequest request) {
        emailService.sendVerificationCode(request.email());
        return ResponseEntity.ok("인증번호가 발송되었습니다.");
    }

    //  인증번호 확인 API
    @Operation(summary = "회원가입 이메일 인증번호 확인", description = "이메일 인증번호를 확인합니다.")
    @PostMapping("/verify")
    public ResponseEntity<String> verifyEmailAuth(@Valid @RequestBody EmailVerifyRequest request) {
        emailService.verifyAuthCode(request.email(), request.code());
        return ResponseEntity.ok("이메일 인증이 완료되었습니다.");
    }
}