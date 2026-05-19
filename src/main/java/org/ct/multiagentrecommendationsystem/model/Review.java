package org.ct.multiagentrecommendationsystem.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Review {
    private String reviewId;
    private String productId;
    private String userId;
    private double rating; // 1-5
    private String content;
    private LocalDateTime createdAt;
}
