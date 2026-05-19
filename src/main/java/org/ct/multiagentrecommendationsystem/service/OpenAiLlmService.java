package org.ct.multiagentrecommendationsystem.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * OpenAI-compatible API implementation — activate via spring.profiles.active=openai
 * Uses RestClient for direct API calls, no Spring AI abstractions needed.
 */
@Service
@Profile("openai")
public class OpenAiLlmService implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmService.class);

    private static final Random RANDOM = new Random(42);

    private static final List<String> VOCABULARY = List.of(
            "phone", "headphone", "laptop", "appliance", "game", "accessory", "wearable", "smart",
            "flagship", "budget", "premium", "business", "sports", "gaming", "office", "camera",
            "anc", "audio", "battery", "fastcharge", "portable", "bigscreen", "waterproof", "design",
            "apple", "samsung", "xiaomi", "huawei", "sony", "bose", "dyson",
            "5g", "ai", "oled", "miniled", "usbc", "bluetooth", "wifi", "nfc"
    );

    private final RestClient chatClient;
    private final RestClient embeddingClient;
    private final String chatModel;
    private final String embeddingModel;
    private final String embeddingPath;

    public OpenAiLlmService(
            @Value("${spring.ai.openai.base-url:https://api.openai.com}") String chatBaseUrl,
            @Value("${spring.ai.openai.api-key:}") String chatApiKey,
            @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String chatModel,
            @Value("${spring.ai.openai.embedding.base-url:${spring.ai.openai.base-url:https://api.openai.com}}") String embeddingBaseUrl,
            @Value("${spring.ai.openai.embedding.api-key:${spring.ai.openai.api-key:}}") String embeddingApiKey,
            @Value("${spring.ai.openai.embedding.path:/v1/embeddings}") String embeddingPath,
            @Value("${spring.ai.openai.embedding.options.model:text-embedding-3-small}") String embeddingModel) {
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.embeddingPath = embeddingPath;
        this.chatClient = RestClient.builder()
                .baseUrl(chatBaseUrl)
                .defaultHeader("Authorization", "Bearer " + chatApiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
        this.embeddingClient = RestClient.builder()
                .baseUrl(embeddingBaseUrl)
                .defaultHeader("Authorization", "Bearer " + embeddingApiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
        log.info("OpenAiLlmService initialized: chat=[{}] model={}, embedding=[{}{}] model={}",
                chatBaseUrl, chatModel, embeddingBaseUrl, embeddingPath, embeddingModel);
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", chatModel,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "temperature", 0.7
            );

            Map<String, Object> response = chatClient.post()
                    .uri("/v1/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.containsKey("choices")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String content = (String) message.get("content");
                    return stripMarkdownFences(content);
                }
            }
            return "{}";
        } catch (Exception e) {
            log.error("OpenAI chat API call failed: {}", e.getMessage());
            return "{}";
        }
    }

    @Override
    public float[] embed(String text) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", embeddingModel,
                    "input", text
            );

            Map<String, Object> response = embeddingClient.post()
                    .uri(embeddingPath)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.containsKey("data")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
                if (!data.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    List<Number> embedding = (List<Number>) data.get(0).get("embedding");
                    float[] result = new float[embedding.size()];
                    for (int i = 0; i < embedding.size(); i++) {
                        result[i] = embedding.get(i).floatValue();
                    }
                    return result;
                }
            }
            return tfidfEmbed(text);
        } catch (Exception e) {
            log.warn("Embedding API 调用失败，降级到本地 TF-IDF: {}", e.getMessage());
            return tfidfEmbed(text);
        }
    }

    /**
     * 去除 LLM 返回的 markdown 代码块包裹（```json ... ```）
     */
    private String stripMarkdownFences(String content) {
        if (content == null) return "{}";
        String trimmed = content.trim();
        // 去掉开头的 ```json 或 ```
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
        }
        // 去掉结尾的 ```
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
        }
        return trimmed.isEmpty() ? "{}" : trimmed;
    }

    /**
     * 本地 TF-IDF 风格向量（与 MockLlmService 一致的降级方案）
     */
    private float[] tfidfEmbed(String text) {
        float[] vector = new float[VOCABULARY.size()];
        String lower = text.toLowerCase();
        for (int i = 0; i < VOCABULARY.size(); i++) {
            if (lower.contains(VOCABULARY.get(i).toLowerCase())) {
                vector[i] = 1.0f;
            }
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] += (RANDOM.nextFloat() - 0.5f) * 0.1f;
        }
        double norm = 0;
        for (float v : vector) norm += v * v;
        if (norm > 0) {
            float invNorm = (float) (1.0 / Math.sqrt(norm));
            for (int i = 0; i < vector.length; i++) {
                vector[i] *= invNorm;
            }
        }
        return vector;
    }
}
