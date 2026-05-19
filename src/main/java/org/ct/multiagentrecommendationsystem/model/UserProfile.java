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
public class UserProfile {
    private String userId;
    private String gender;
    private int age;
    private String city;
    private List<String> segments;
    private List<String> preferredCategories;
    private double[] priceRange; // [min, max]
    private RfmScore rfmScore;
    private List<String> realTimeTags;
    private List<String> recentPurchases;
    // 行为统计
    private int totalOrders;
    private double avgOrderValue;
}
