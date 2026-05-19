package org.ct.multiagentrecommendationsystem.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    private String productId;
    private String name;
    private String category;
    private String subCategory;
    private double price;
    private String brand;
    private String description;
    private List<String> tags;
    private Map<String, String> attributes;
    private double rating;
    private int reviewCount;
    private int salesRank;
    private String releaseDate;

    // 召回来源标注（多路召回时标记）
    private String recallSource;
    // 排序得分（RankingAgent 设置）
    private double score;
}
