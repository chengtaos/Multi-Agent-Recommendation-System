package org.ct.multiagentrecommendationsystem.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ct.multiagentrecommendationsystem.model.*;
import org.ct.multiagentrecommendationsystem.service.ComplianceService;
import org.ct.multiagentrecommendationsystem.service.LlmService;

import java.io.InputStream;
import java.time.Duration;
import java.util.*;

public class MarketingCopyAgent extends BaseAgent {

    private final LlmService llmService;
    private final ComplianceService complianceService;
    private final ObjectMapper objectMapper;
    private Map<String, JsonNode> templates;

    public MarketingCopyAgent(LlmService llmService, ComplianceService complianceService) {
        super("marketing_copy", Duration.ofSeconds(15), 2, 500);
        this.llmService = llmService;
        this.complianceService = complianceService;
        this.objectMapper = new ObjectMapper();
        loadTemplates();
    }

    private void loadTemplates() {
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("data/copy_templates.json");
            JsonNode root = objectMapper.readTree(is);
            templates = new HashMap<>();
            for (JsonNode t : root.get("templates")) {
                templates.put(t.get("type").asText(), t);
            }
        } catch (Exception e) {
            log.error("Failed to load copy templates", e);
            templates = Map.of();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected AgentResult doExecute(Map<String, Object> input) {
        long startTime = System.currentTimeMillis();
        UserProfile profile = (UserProfile) input.get("userProfile");
        List<Product> products = (List<Product>) input.get("products");

        if (products == null || products.isEmpty()) {
            return AgentResult.success(name, Map.of("marketingCopies", List.of()), System.currentTimeMillis() - startTime);
        }

        String templateType = selectTemplateType(profile);
        JsonNode template = templates.getOrDefault(templateType, templates.get("active"));

        List<MarketingCopy> copies = new ArrayList<>();

        for (Product product : products) {
            try {
                String templateStr = template != null ? template.toString() : "generic template";
                String tagsStr = product.getTags() != null ? String.join(",", product.getTags()) : "";
                String segmentsStr = profile != null && profile.getSegments() != null ?
                        String.join(",", profile.getSegments()) : "regular user";

                String prompt = String.format(
                        "Template type: %s\nTemplate ref: %s\n" +
                                "Product: %s | price: %.0f | brand: %s | desc: %s | tags: %s\n" +
                                "User preferences: %s\n\n" +
                                "Generate a 30-50 word personalized recommendation copy. Avoid superlative claims.",
                        templateType, templateStr,
                        product.getName(), product.getPrice(), product.getBrand(),
                        product.getDescription(), tagsStr, segmentsStr);

                String generated = llmService.chat(
                        "You are an e-commerce marketing copywriter. Generate attractive product copy.",
                        prompt);

                String filtered = complianceService.filter(generated);

                copies.add(MarketingCopy.builder()
                        .productId(product.getProductId())
                        .copy(filtered)
                        .templateType(templateType)
                        .build());
            } catch (Exception e) {
                log.warn("[marketing] Copy generation failed for {}: {}", product.getProductId(), e.getMessage());
                String desc = product.getDescription() != null ?
                        product.getDescription().substring(0, Math.min(20, product.getDescription().length())) : "";
                copies.add(MarketingCopy.builder()
                        .productId(product.getProductId())
                        .copy(String.format("Recommended: %s, %s, only %.0f CNY",
                                product.getName(), desc, product.getPrice()))
                        .templateType("fallback")
                        .build());
            }
        }

        long latency = System.currentTimeMillis() - startTime;
        return AgentResult.success(name, Map.of("marketingCopies", copies), latency);
    }

    private String selectTemplateType(UserProfile profile) {
        if (profile == null || profile.getSegments() == null) return "active";

        for (String seg : profile.getSegments()) {
            if (seg.contains("new_user")) return "new_user";
            if (seg.contains("high_value")) return "high_value";
            if (seg.contains("price_sensitive")) return "price_sensitive";
            if (seg.contains("churn_risk")) return "churn_risk";
        }
        return "active";
    }
}
