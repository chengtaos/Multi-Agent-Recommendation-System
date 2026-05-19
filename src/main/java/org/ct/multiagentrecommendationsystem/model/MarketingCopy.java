package org.ct.multiagentrecommendationsystem.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketingCopy {
    private String productId;
    private String copy;
    private String templateType;
}
