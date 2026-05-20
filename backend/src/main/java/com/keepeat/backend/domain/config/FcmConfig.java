package com.keepeat.backend.domain.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;

import java.io.InputStream;

@Slf4j
@Configuration
public class FcmConfig {

    private static final String CLASSPATH_KEY = "firebase-service-key.json";

    @Value("${firebase.key-path:}")
    private String firebaseKeyPath;

    @PostConstruct
    public void init() {
        try {
            if (!FirebaseApp.getApps().isEmpty()) {
                return;
            }

            InputStream serviceAccount;
            if (firebaseKeyPath != null && !firebaseKeyPath.isBlank()) {
                serviceAccount = new FileSystemResource(firebaseKeyPath).getInputStream();
                log.info("FCM 키를 외부 파일 경로에서 로딩: {}", firebaseKeyPath);
            } else {
                serviceAccount = new ClassPathResource(CLASSPATH_KEY).getInputStream();
                log.info("FCM 키를 classpath에서 로딩 (로컬 개발 폴백)");
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            FirebaseApp.initializeApp(options);
            log.info("FCM 설정(FirebaseApp)이 성공적으로 초기화되었습니다.");
        } catch (Exception e) {
            log.error("FCM 초기화 중 에러가 발생했습니다: {}", e.getMessage());
        }
    }
}
