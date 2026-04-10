package com.keepeat.backend.user.controller;

import com.keepeat.backend.user.dto.LoginRequest;
import com.keepeat.backend.user.dto.SignUpRequest;
import com.keepeat.backend.user.dto.TokenResponse;
import com.keepeat.backend.user.service.AppUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController // 이 클래스가 REST API 요청을 처리하는 컨트롤러임을 명시합니다.
@RequestMapping("/api/users") // 이 컨트롤러의 기본 주소를 설정합니다.
@RequiredArgsConstructor
public class AppUserController {

    private final AppUserService appUserService;

    // POST http://localhost:8080/api/users/signup 요청이 오면 이 메서드가 실행됩니다.
    @PostMapping("/signup")
    public ResponseEntity<String> signUp(@RequestBody SignUpRequest request) {

        // 1. 서비스 클래스에게 프론트엔드에서 받은 데이터(이메일, 비밀번호)를 넘겨주며 회원가입 처리를 시킵니다.
        appUserService.signUp(request);

        // 2. 에러 없이 무사히 완료되었다면, 상태 코드 200(OK)과 함께 성공 메시지를 보냅니다.
        return ResponseEntity.ok("회원가입이 성공적으로 완료되었습니다.");
    }

    // POST http://localhost:8080/api/users/login 요청이 오면 실행됩니다.
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {

        // 서비스에게 로그인을 시키고, 결과물(토큰들)을 받아옵니다.
        TokenResponse tokenResponse = appUserService.login(request);

        // 성공적으로 토큰이 발급되면 프론트엔드로 전달합니다!
        return ResponseEntity.ok(tokenResponse);
    }
}