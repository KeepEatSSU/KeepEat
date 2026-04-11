package com.keepeat.backend.domain.user.controller;

import com.keepeat.backend.domain.user.dto.LoginRequest;
import com.keepeat.backend.domain.user.dto.SignUpRequest;
import com.keepeat.backend.domain.user.dto.TokenRefreshRequest;
import com.keepeat.backend.domain.user.dto.TokenResponse;
import com.keepeat.backend.domain.user.service.AppUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import com.keepeat.backend.domain.user.dto.UserResponse;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class AppUserController {

    private final AppUserService appUserService;

    // POST http://localhost:8080/api/users/signup
    @PostMapping("/signup")
    public ResponseEntity<String> signUp(@RequestBody SignUpRequest request) {

        appUserService.signUp(request);

        return ResponseEntity.ok("회원가입이 성공적으로 완료되었습니다.");
    }

    // POST http://localhost:8080/api/users/login
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {

        TokenResponse tokenResponse = appUserService.login(request);

        return ResponseEntity.ok(tokenResponse);
    }

    // POST http://localhost:8080/api/users/logout
    @PostMapping("/logout")
    public ResponseEntity<String> logout(@AuthenticationPrincipal Long userId) {
        appUserService.logout(userId);
        return ResponseEntity.ok("로그아웃되었습니다.");
    }


    // POST http://localhost:8080/api/users/refresh
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@RequestBody TokenRefreshRequest request) {
        // 서비스에게 재발급을 시키고 새로운 토큰 세트를 리턴
        TokenResponse newTokens = appUserService.refreshTokens(request);
        return ResponseEntity.ok(newTokens);
    }


    // GET http://localhost:8080/api/users/me
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