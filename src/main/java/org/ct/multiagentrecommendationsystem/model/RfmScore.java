package org.ct.multiagentrecommendationsystem.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RfmScore {
    /** 最近购买 (Recency): 0-1，越近越高 */
    private double r;
    /** 购买频次 (Frequency): 0-1，越多越高 */
    private double f;
    /** 消费金额 (Monetary): 0-1，越高越高 */
    private double m;
}
