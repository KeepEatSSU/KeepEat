package com.keepeat.backend.domain.user.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import com.keepeat.backend.domain.security.JwtProvider;
import com.keepeat.backend.domain.user.dto.*;
import com.keepeat.backend.domain.user.entity.AppUser;
import com.keepeat.backend.domain.user.entity.Role;
import com.keepeat.backend.domain.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppUserService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional
    public Long signUp(SignUpRequest request) {

        // 이메일 중복 검사
        if (appUserRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        // 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // 저장할 새로운 유저 객체(Entity) 생성
        AppUser newUser = new AppUser(
                request.getEmail(),
                encodedPassword,
                Role.ROLE_USER // 가입하는 사람은 기본적으로 일반 유저(USER) 권한
        );

        // DB에 저장하고, 저장된 유저의 고유 ID(PK)를 반환
        AppUser savedUser = appUserRepository.save(newUser);
        return savedUser.getId();
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        // 이메일로 유저가 있는지 찾기
        AppUser user = appUserRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("가입되지 않은 이메일입니다."));

        // 비밀번호가 맞는지 확인 (평문 비밀번호와 DB의 암호화된 비밀번호를 비교)
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        // 비밀번호까지 맞다면, 토큰 2개 발급
        String accessToken = jwtProvider.createAccessToken(user.getEmail(), user.getId());
        String rawRefreshToken = jwtProvider.createRefreshToken(user.getEmail(), user.getId());

        String hashedRefreshToken = hashToken(rawRefreshToken);
        user.updateRefreshToken(hashedRefreshToken);

        // 발급된 토큰들을 TokenResponse 바구니에 담아서 반환
        return new TokenResponse(accessToken, rawRefreshToken);
    }

    @Transactional
    public void logout(Long userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));
        user.clearRefreshToken();
    }

    @Transactional
    public TokenResponse refreshTokens(TokenRefreshRequest request) {
        // 프론트엔드에서 보낸 '원본' Refresh Token
        String rawRefreshToken = request.getRefreshToken();

        // 1차 검증: 토큰 자체가 위조되지 않았고, 만료되지 않았는지 확인
        if (!jwtProvider.validateToken(rawRefreshToken)) {
            throw new IllegalArgumentException("유효하지 않거나 만료된 Refresh Token입니다. 다시 로그인해주세요.");
        }

        // 토큰에서 이메일을 꺼내 유저 검색
        String email = jwtProvider.getEmail(rawRefreshToken);
        AppUser user = appUserRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        // 잘 되고 있는지 확인용
        /*
        System.out.println("=== 토큰 비교 수사 시작 ===");
        System.out.println("1. 프론트가 보낸 원본 토큰: " + rawRefreshToken);
        System.out.println("2. 그걸 서버에서 해싱한 값: " + hashToken(rawRefreshToken));
        System.out.println("3. DB(AppUser)에 저장된 값: " + user.getRefreshToken());
        System.out.println("===========================");
        */

        // 2차 검증: 프론트가 보낸 원본 토큰과 DB의 암호화된 토큰이 짝이 맞는지 확인
        if (user.getRefreshToken() == null || !hashToken(rawRefreshToken).equals(user.getRefreshToken())) {
            throw new IllegalArgumentException("이미 로그아웃 되었거나 무효화된 토큰입니다. 다시 로그인해주세요.");
        }

        // 검증을 모두 통과했으니 새로운 토큰 세트를 발급
        String newAccessToken = jwtProvider.createAccessToken(email, user.getId());
        String newRawRefreshToken = jwtProvider.createRefreshToken(email, user.getId());

        // 새로 발급한 Refresh Token도 암호화해서 DB를 업데이트 (Token Rotation)
        String newHashedRefreshToken = hashToken(newRawRefreshToken);
        user.updateRefreshToken(newHashedRefreshToken);

        return new TokenResponse(newAccessToken, newRawRefreshToken);
    }

    // 내 정보 조회 로직, 혹시 몰라서 만들어 둠
    public UserResponse getUserInfo(Long userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));
        return new UserResponse(user.getId(), user.getEmail(), user.getRole());
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("해싱 알고리즘을 찾을 수 없습니다.", e);
        }
    }
}