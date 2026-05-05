package com.keepeat.backend.domain.user.controller;

import com.keepeat.backend.domain.user.dto.LoginRequest;
import com.keepeat.backend.domain.user.dto.SignUpRequest;
import com.keepeat.backend.domain.user.dto.TokenRefreshRequest;
import com.keepeat.backend.domain.user.dto.TokenResponse;
import com.keepeat.backend.domain.user.service.AppUserService;
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
import com.keepeat.backend.domain.user.dto.UserResponse;

@Tag(name = "User API", description = "사용자 인증 및 관리 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class AppUserController {

    private final AppUserService appUserService;

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

    /*
    // ********************************************************************************
    // user의 id 사용하는 법
    // 예시. 그냥 임시로 refrigerator라고 해둠. 이거 삭제 or 수정 ㄱㅊ. 난 안씀
    // GET http://localhost:8080/api/users/refrigerator
    @GetMapping("/refrigerator")
    public ResponseEntity<String> getMyRefrigerator(@AuthenticationPrincipal Long userId) {
        // 아래는 예시, IngredientDto랑 refrigeratorService.getMyIngredients에 사용하는 곳에 맞춰서 사용.
        List<IngredientDto> result = refrigeratorService.getMyIngredients(userId);
        return ResponseEntity.ok(result);
    }
    // ***********************************************************************************
    */
}