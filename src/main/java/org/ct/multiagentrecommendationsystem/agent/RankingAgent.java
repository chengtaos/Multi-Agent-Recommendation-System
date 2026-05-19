package org.ct.multiagentrecommendationsystem.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ct.multiagentrecommendationsystem.model.*;
import org.ct.multiagentrecommendationsystem.service.LlmService;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class RankingAgent extends BaseAgent {

    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    public RankingAgent(LlmService llmService) {
        super("ranking", Duration.ofSeconds(15), 2, 500);
        this.llmService = llmService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected AgentResult doExecute(Map<String, Object> input) {
        long startTime = System.currentTimeMillis();
        List<Product> candidates = (List<Product>) input.get("candidates");
        UserProfile profile = (UserProfile) input.get("userProfile");
        Map<String, ReviewSummary> reviewMap = (Map<String, ReviewSummary>) input.get("reviewSummaryMap");

        if (candidates == null || candidates.isEmpty()) {
            return AgentResult.success(name, Map.of("ranked", List.of()), System.currentTimeMillis() - startTime);
        }

        int numItems = (int) input.getOrDefault("numItems", 5);

        try {
            StringBuilder productInfo = new StringBuilder();
            for (Product p : candidates) {
                ReviewSummary rs = reviewMap != null ? reviewMap.get(p.getProductId()) : null;
                productInfo.append(String.format(
                        "ID:%s | %s | %s | %s%.0f | %s:%s | %s:%.1f | %s:%s | %s\n",
                        p.getProductId(), p.getName(), p.getCategory(),
                        "CNY", p.getPrice(), "brand", p.getBrand(), "rating", p.getRating(),
                        "tags", p.getTags() != null ? String.join(",", p.getTags()) : "",
                        rs != null ? String.format("sentiment:%.2f/%s", rs.getQualityScore(), rs.getSentiment()) : ""));
            }

            StringBuilder profileStr = new StringBuilder();
            if (profile != null) {
                profileStr.append(String.format("preferredCategories: %s | budget: %.0f-%.0f | segments: %s",
                        profile.getPreferredCategories(),
                        profile.getPriceRange() != null ? profile.getPriceRange()[0] : 0,
                        profile.getPriceRange() != null ? profile.getPriceRange()[1] : 10000,
                        profile.getSegments()));
            } else {
                profileStr.append("no profile info");
            }

            String prompt = String.format(
                    "User profile:\n%s\n\nCandidate products:\n%s\n\nRank top-%d by:\n" +
                            "1. Profile match (category/price/preference)\n" +
                            "2. Product attributes (price/brand/tags/rating)\n" +
                            "3. Review quality (sentiment/risks)\n\n" +
                            "Output JSON array: [{\"productId\":\"ID\",\"score\":0.95},...]",
                    profileStr, productInfo, numItems);

            String llmResponse = llmService.chat(
                    "You are an e-commerce ranking expert. Rank products based on user profile and product info.",
                    prompt);
            List<Map<String, Object>> rankings = objectMapper.readValue(llmResponse,
                    new TypeReference<List<Map<String, Object>>>() {});

            Map<String, Double> scoreMap = new LinkedHashMap<>();
            for (Map<String, Object> r : rankings) {
                String pid = (String) r.get("productId");
                double score = ((Number) r.get("score")).doubleValue();
                scoreMap.put(pid, score);
            }

            List<Product> ranked = new ArrayList<>();
            for (Map.Entry<String, Double> entry : scoreMap.entrySet()) {
                candidates.stream()
                        .filter(p -> p.getProductId().equals(entry.getKey()))
                        .findFirst()
                        .ifPresent(p -> {
                            p.setScore(entry.getValue());
                            ranked.add(p);
                        });
            }

            for (Product p : candidates) {
                if (!scoreMap.containsKey(p.getProductId())) {
                    p.setScore(0.5);
                    ranked.add(p);
                }
            }

            long latency = System.currentTimeMillis() - startTime;
            return AgentResult.success(name, Map.of("ranked", ranked), latency);
        } catch (Exception e) {
            log.warn("[ranking] LLM ranking failed, using rule-based: {}", e.getMessage());
            List<Product> ranked = ruleBasedRanking(candidates, profile, reviewMap, numItems);
            long latency = System.currentTimeMillis() - startTime;
            return AgentResult.success(name, Map.of("ranked", ranked), latency);
        }
    }

    private List<Product> ruleBasedRanking(List<Product> candidates, UserProfile profile,
                                           Map<String, ReviewSummary> reviewMap, int numItems) {
        return candidates.stream()
                .sorted((a, b) -> {
                    double scoreA = computeRuleScore(a, profile, reviewMap);
                    double scoreB = computeRuleScore(b, profile, reviewMap);
                    return Double.compare(scoreB, scoreA);
                })
                .peek(p -> {
                    double s = computeRuleScore(p, profile, reviewMap);
                    p.setScore(Math.min(1.0, s));
                })
                .collect(Collectors.toList());
    }

    private double computeRuleScore(Product product, UserProfile profile, Map<String, ReviewSummary> reviewMap) {
        double score = 0.5;

        if (profile != null && profile.getPreferredCategories() != null &&
                profile.getPreferredCategories().contains(product.getCategory())) {
            score += 0.2;
        }
        if (profile != null && profile.getPriceRange() != null && profile.getPriceRange().length >= 2) {
            if (product.getPrice() >= profile.getPriceRange()[0] && product.getPrice() <= profile.getPriceRange()[1]) {
                score += 0.1;
            }
        }
        ReviewSummary rs = reviewMap != null ? reviewMap.get(product.getProductId()) : null;
        if (rs != null) {
            score += rs.getQualityScore() * 0.2;
            if (rs.getRiskFlags() != null && !rs.getRiskFlags().isEmpty()) {
                score -= 0.2;
            }
        }
        return Math.max(0, score);
    }
}
