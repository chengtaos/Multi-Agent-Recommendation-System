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

/**
 * OpenAI-compatible API implementation — activate via spring.profiles.active=openai
 * Uses RestClient for direct API calls, no Spring AI abstractions needed.
 */
@Service
@Profile("openai")
public class OpenAiLlmService implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmService.class);

    private final RestClient restClient;
    private final String chatModel;
    private final String embeddingModel;

    public OpenAiLlmService(
            @Value("${spring.ai.openai.base-url:https://api.openai.com}") String baseUrl,
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String chatModel,
            @Value("${spring.ai.openai.embedding.options.model:text-embedding-3-small}") String embeddingModel) {
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
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

            Map<String, Object> response = restClient.post()
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
                    return (String) message.get("content");
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

            Map<String, Object> response = restClient.post()
                    .uri("/v1/embeddings")
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
            return new float[0];
        } catch (Exception e) {
            log.error("OpenAI embedding API call failed: {}", e.getMessage());
            return new float[0];
        }
    }
}
