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
public class UserBehavior {
    private String behaviorId;
    private String userId;
    private String productId;
    private String action; // click, purchase, view, add_cart
    private LocalDateTime timestamp;
    private double amount; // 购买金额（purchase 时有值）
}
