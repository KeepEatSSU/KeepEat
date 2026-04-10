package com.keepeat.backend.user.service;

import com.keepeat.backend.security.JwtProvider;
import com.keepeat.backend.user.dto.LoginRequest;
import com.keepeat.backend.user.dto.TokenResponse;
import com.keepeat.backend.user.dto.SignUpRequest;
import com.keepeat.backend.user.entity.AppUser;
import com.keepeat.backend.user.entity.Role;
import com.keepeat.backend.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor // final이 붙은 객체들을 자동으로 연결(주입)해 줍니다.
public class AppUserService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional // 이 메서드 안의 작업들은 하나의 트랜잭션으로 묶여서, 중간에 에러가 나면 전부 취소(Rollback) 됩니다.
    public Long signUp(SignUpRequest request) {

        // 1. 이메일 중복 검사 (이미 가입된 사람인지 확인)
        if (appUserRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        // 2. 비밀번호 암호화 (평문 -> 복잡한 해시 문자열)
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // 3. 저장할 새로운 유저 객체(Entity) 생성
        AppUser newUser = new AppUser(
                request.getEmail(),
                encodedPassword,
                Role.ROLE_USER // 가입하는 사람은 기본적으로 일반 유저(USER) 권한을 줍니다.
        );

        // 4. DB에 저장하고, 저장된 유저의 고유 ID(PK)를 반환
        AppUser savedUser = appUserRepository.save(newUser);
        return savedUser.getId();
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        // 1. 이메일로 유저가 있는지 찾기
        AppUser user = appUserRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("가입되지 않은 이메일입니다."));

        // 2. 비밀번호가 맞는지 확인 (평문 비밀번호와 DB의 암호화된 비밀번호를 비교)
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        // 3. 비밀번호까지 맞다면, 토큰 2개 발급
        String accessToken = jwtProvider.createAccessToken(user.getEmail());
        String refreshToken = jwtProvider.createRefreshToken(user.getEmail());

        // 4. 발급된 토큰들을 TokenResponse 바구니에 담아서 반환
        return new TokenResponse(accessToken, refreshToken);
    }
}