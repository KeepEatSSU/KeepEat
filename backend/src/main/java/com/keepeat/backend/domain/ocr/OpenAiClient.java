package com.keepeat.backend.domain.ocr;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.keepeat.backend.domain.common.exception.ErrorCode;
import com.keepeat.backend.domain.common.exception.KeepEatException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final int maxTokens;

    public OpenAiClient(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.model}") String model,
            @Value("${openai.max-tokens}") int maxTokens,
            ObjectMapper objectMapper) {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = objectMapper;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    public record ParsedIngredient(
            String raw,
            String name,
            String customName,
            Double quantity,
            String unit
    ) {}

    public List<ParsedIngredient> extractIngredients(byte[] imageBytes, String mimeType) {
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        String imageUrl = "data:" + mimeType + ";base64," + base64Image;

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "response_format", Map.of("type", "json_object"),
                "max_tokens", maxTokens,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", List.of(
                                Map.of("type", "image_url", "image_url", Map.of("url", imageUrl))
                        ))
                )
        );

        try {
            String responseBody = webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            return parseResponse(responseBody);
        } catch (WebClientResponseException e) {
            throw new KeepEatException(ErrorCode.OCR_API_FAILURE, e);
        } catch (Exception e) {
            if (e instanceof KeepEatException) {
                throw (KeepEatException) e;
            }
            throw new KeepEatException(ErrorCode.OCR_API_FAILURE, e);
        }
    }

    private List<ParsedIngredient> parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("choices").get(0).path("message").path("content").asText();
            JsonNode parsed = objectMapper.readTree(content);
            JsonNode ingredientsNode = parsed.path("ingredients");

            List<ParsedIngredient> ingredients = new ArrayList<>();
            if (ingredientsNode.isArray()) {
                for (JsonNode node : ingredientsNode) {
                    String raw = node.path("raw").asText("").trim();
                    String name = node.path("name").asText("").trim();
                    if (name.isEmpty()) continue;

                    String customName = node.path("customName").isNull() ? null
                            : node.path("customName").asText("").trim();
                    if (customName != null && customName.isEmpty()) customName = null;

                    Double quantity = node.path("quantity").isNull() ? null
                            : node.path("quantity").asDouble();

                    String unit = node.path("unit").isNull() ? null
                            : node.path("unit").asText("").trim();
                    if (unit != null && unit.isEmpty()) unit = null;

                    ingredients.add(new ParsedIngredient(raw, name, customName, quantity, unit));
                }
            }
            return ingredients;
        } catch (Exception e) {
            throw new KeepEatException(ErrorCode.OCR_PARSE_FAILURE);
        }
    }

    private static final String SYSTEM_PROMPT = """
            당신은 식료품 영수증 및 온라인 쇼핑몰 결제내역을 분석하는 파서입니다.
            이미지에서 식재료 정보를 추출하세요.

            규칙:
            - 각 식재료에 대해 다음 정보를 추출:
              - raw: 영수증/내역에 적힌 원본 텍스트 그대로
              - name: 브랜드명, 수량, 단위를 제거한 일반 식재료명 (예: "풀무원 두부 300g" → "두부")
              - customName: 브랜드가 포함된 경우 브랜드+식재료명 (수량/단위 제외). 브랜드가 없으면 null
              - quantity: 수량 (숫자만, 없으면 null)
              - unit: 단위 (g, kg, ml, L, 개 등, 없으면 null)
            - 한국어 식재료명으로 반환
            - 중복 제거
            - 식재료가 아닌 항목 제외 (가격, 매장명, 할인 정보 등)

            JSON 형식:
            {"ingredients": [
              {"raw": "풀무원 두부 300g", "name": "두부", "customName": "풀무원 두부", "quantity": 300, "unit": "g"},
              {"raw": "삼겹살 500g", "name": "삼겹살", "customName": null, "quantity": 500, "unit": "g"},
              {"raw": "서울우유 1000ml", "name": "우유", "customName": "서울우유", "quantity": 1000, "unit": "ml"}
            ]}

            식재료가 없으면: {"ingredients": []}
            """;
}
