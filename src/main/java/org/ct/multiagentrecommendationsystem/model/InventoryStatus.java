package org.ct.multiagentrecommendationsystem.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryStatus {
    private String productId;
    private int stock;
    private String status; // normal / low / critical / out_of_stock
    private int maxPurchaseQty; // 限购数量
    private String alertMsg;
}
