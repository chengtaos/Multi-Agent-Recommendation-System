package org.ct.multiagentrecommendationsystem.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Mock LLM implementation — no API key required, rule-based responses
 */
@Service
@Profile("!openai")
public class MockLlmService implements LlmService {

    private static final Random RANDOM = new Random(42);

    private static final List<String> VOCABULARY = List.of(
            "phone", "headphone", "laptop", "appliance", "game", "accessory", "wearable", "smart",
            "flagship", "budget", "premium", "business", "sports", "gaming", "office", "camera",
            "anc", "audio", "battery", "fastcharge", "portable", "bigscreen", "waterproof", "design",
            "apple", "samsung", "xiaomi", "huawei", "sony", "bose", "dyson",
            "5g", "ai", "oled", "miniled", "usbc", "bluetooth", "wifi", "nfc"
    );

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        if (userPrompt.contains("profile") || userPrompt.contains("RFM") || userPrompt.contains("segment")) {
            return mockProfileAnalysis(userPrompt);
        } else if (userPrompt.contains("review") || userPrompt.contains("sentiment") || userPrompt.contains("Reviews")) {
            return mockSentimentAnalysis(userPrompt);
        } else if (userPrompt.contains("rank") || userPrompt.contains("Rank")) {
            return mockRanking(userPrompt);
        } else if (userPrompt.contains("copy") || userPrompt.contains("Copy") || userPrompt.contains("marketing")) {
            return mockCopyGeneration(userPrompt);
        }
        return "{}";
    }

    @Override
    public float[] embed(String text) {
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

    private String mockProfileAnalysis(String prompt) {
        List<String> segments = new ArrayList<>();
        if (prompt.contains("price") || prompt.contains("budget")) {
            segments.add("price_sensitive");
        }
        if (prompt.contains("high") || prompt.contains("premium") || prompt.contains("flagship")) {
            segments.add("high_value");
        }
        segments.add("active");

        String segJson = segments.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(","));
        String catJson = pickRandomCategories().stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(","));
        return String.format("{\"segments\":[%s],\"preferredCategories\":[%s],\"priceRange\":[500,5000],\"realTimeTags\":[\"active_time:evening\"]}",
                segJson, catJson);
    }

    private String mockSentimentAnalysis(String prompt) {
        double quality = 0.5 + RANDOM.nextDouble() * 0.4;
        String sentiment = quality > 0.7 ? "positive" : quality > 0.4 ? "neutral" : "negative";
        List<String> tags = pickRandomTags();
        String tagsJson = tags.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(","));
        String riskJson = quality < 0.5 ? "[\"low_rating\"]" : "[]";
        return String.format("{\"sentiment\":\"%s\",\"qualityScore\":%.2f,\"keyTags\":[%s],\"riskFlags\":%s}",
                sentiment, quality, tagsJson, riskJson);
    }

    private String mockRanking(String prompt) {
        StringBuilder sb = new StringBuilder("[");
        double score = 1.0;
        for (int i = 0; i < 5; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format("{\"productId\":\"P%03d\",\"score\":%.2f}", RANDOM.nextInt(50) + 1, score));
            score -= 0.1 + RANDOM.nextDouble() * 0.05;
        }
        sb.append("]");
        return sb.toString();
    }

    private String mockCopyGeneration(String prompt) {
        return "Based on your preferences, we recommend this premium product at a limited-time offer price.";
    }

    private List<String> pickRandomCategories() {
        List<String> all = new ArrayList<>(List.of("phone", "headphone", "laptop", "appliance", "accessory", "wearable"));
        Collections.shuffle(all, RANDOM);
        return all.subList(0, 2 + RANDOM.nextInt(3));
    }

    private List<String> pickRandomTags() {
        List<String> all = new ArrayList<>(List.of("good quality", "great value", "nice design", "fast delivery",
                "good sound", "clear camera", "long battery", "comfortable", "good ANC", "powerful", "nice screen"));
        Collections.shuffle(all, RANDOM);
        return all.subList(0, 2 + RANDOM.nextInt(3));
    }
}
