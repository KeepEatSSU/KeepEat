package com.keepeat.backend.domain.user.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

import com.keepeat.backend.domain.recipe.repository.UserRecipeRepository;
import com.keepeat.backend.domain.security.JwtProvider;
import com.keepeat.backend.domain.user.dto.*;
import com.keepeat.backend.domain.user.entity.AppUser;
import com.keepeat.backend.domain.user.entity.Role;
import com.keepeat.backend.domain.user.entity.EmailAuth;
import com.keepeat.backend.domain.user.repository.AppUserRepository;
import com.keepeat.backend.domain.user.repository.EmailAuthRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppUserService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final UserRecipeRepository userRecipeRepository;
    private final EmailAuthRepository emailAuthRepository;

    @Transactional
    public Long signUp(SignUpRequest request) {

        EmailAuth emailAuth = emailAuthRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("이메일 인증을 진행해 주세요."));

        if (!emailAuth.isVerified()) {
            throw new IllegalArgumentException("이메일 인증이 완료되지 않았습니다.");
        }

        // 이메일 중복 검사
        if (appUserRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        // 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(request.password());

        // 저장할 새로운 유저 객체(Entity) 생성
        AppUser newUser = new AppUser(
                request.nickname(),
                request.email(),
                encodedPassword,
                Role.ROLE_USER // 가입하는 사람은 기본적으로 일반 유저(USER) 권한
        );

        emailAuthRepository.delete(emailAuth);
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
        String accessToken = jwtProvider.createAccessToken(user.getEmail(), user.getId(), user.getRole());
        String rawRefreshToken = jwtProvider.createRefreshToken(user.getEmail(), user.getId(), user.getRole());

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
        String newAccessToken = jwtProvider.createAccessToken(email, user.getId(), user.getRole());
        String newRawRefreshToken = jwtProvider.createRefreshToken(email, user.getId(), user.getRole());

        // 새로 발급한 Refresh Token도 암호화해서 DB를 업데이트 (Token Rotation)
        String newHashedRefreshToken = hashToken(newRawRefreshToken);
        user.updateRefreshToken(newHashedRefreshToken);

        return new TokenResponse(newAccessToken, newRawRefreshToken);
    }

    // 내 정보 조회 로직, 혹시 몰라서 만들어 둠
    public UserResponse getUserInfo(Long userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));
        return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.getProvider());
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

    @Transactional
    public void deleteUser(Long userId) {

        // 유저 삭제
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));
        appUserRepository.delete(user);
    }

    @Transactional
    public boolean toggleNotification(Long userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        // 현재 상태의 반대로 뒤집기 (true -> false, false -> true)
        user.toggleNotification(!user.isNotificationEnabled());

        return user.isNotificationEnabled(); // 변경된 상태 반환
    }

    private final EmailService emailService;

    @Transactional
    public void sendTemporaryPassword(String email) {
        // 1. 해당 이메일로 가입한 유저가 있는지 검증
        AppUser user = appUserRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("해당 이메일로 가입된 유저를 찾을 수 없습니다."));

        // 2. 임시 비밀번호 생성 (8자리 영문 대소문자 + 숫자 혼합 형태)
        String tempPassword = generateRandomPassword();

        // 3. DB에 암호화된 임시 비밀번호 저장
        // (AppUser 엔티티 내부에 비밀번호 변경용 메서드가 있다면 그걸 쓰셔도 좋습니다)
        String encodedPassword = passwordEncoder.encode(tempPassword);
        user.updatePassword(encodedPassword);

        // 4. 이메일 발송 (이메일 실패 시 전체 트랜잭션이 롤백되도록 구성)
        emailService.sendTemporaryPassword(email, tempPassword);
    }

    // 8자리 랜덤 비밀번호 생성 헬퍼 메서드
    private String generateRandomPassword() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    @Transactional
    public void changePassword(Long userId, PasswordChangeRequest request) {
        // 로그인한 유저 정보 조회
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        // 입력한 현재 비밀번호가 DB에 저장된 암호화된 비밀번호와 일치하는지 검증
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }

        // 3. 새 비밀번호 암호화 후 엔티티 메서드를 통해 반영 (아까 만든 updatePassword 사용)
        String encodedPassword = passwordEncoder.encode(request.newPassword());
        user.updatePassword(encodedPassword);

        log.info("🔐 유저 ID [{}]의 비밀번호가 성공적으로 변경되었습니다.", userId);
    }

}