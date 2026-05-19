package org.ct.multiagentrecommendationsystem.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSummary {
    private String productId;
    private String sentiment; // positive / neutral / negative
    private double qualityScore; // 0-1
    private List<String> keyTags;
    private List<String> riskFlags;
    private double avgRating;
    private int reviewCount;
}
