package org.ct.multiagentrecommendationsystem.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationResponse {
    private String requestId;
    private String userId;
    private String experimentGroup;
    private List<ProductItem> products;
    private List<MarketingCopy> marketingCopies;
    private Map<String, AgentResult> agentResults;
    private long totalLatencyMs;
    private LocalDateTime timestamp;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductItem {
        private String productId;
        private String name;
        private String category;
        private double price;
        private String brand;
        private String description;
        private double score;
        private String recallSource;
        private ReviewSummary reviewSummary;
        private InventoryStatus inventoryStatus;
    }
}
