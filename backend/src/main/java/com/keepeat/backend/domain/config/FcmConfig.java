package com.keepeat.backend.domain.config; // 본인 패키지 경로에 맞게 수정!

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;

@Slf4j
@Configuration
public class FcmConfig {

    // 아까 resources 폴더에 넣은 JSON 파일의 이름을 정확히 적어주세요.
    private final String FIREBASE_KEY_PATH = "firebase-service-key.json";

    @PostConstruct
    public void init() {
        try {
            // 이미 초기화되어 있다면 다시 초기화하지 않음
            if (FirebaseApp.getApps().isEmpty()) {
                // resources 폴더 안의 키 파일을 읽어옴
                InputStream serviceAccount = new ClassPathResource(FIREBASE_KEY_PATH).getInputStream();

                // 파이어베이스 옵션 설정 (신분증 등록)
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();

                // 파이어베이스 앱 초기화
                FirebaseApp.initializeApp(options);
                log.info("FCM 설정(FirebaseApp)이 성공적으로 초기화되었습니다.");
            }
        } catch (Exception e) {
            log.error("FCM 초기화 중 에러가 발생했습니다: {}", e.getMessage());
        }
    }
}