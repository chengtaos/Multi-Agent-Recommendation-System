package org.ct.multiagentrecommendationsystem.repository;

import java.util.List;
import java.util.Map;

public interface InventoryRepository {
    int getStock(String productId);
    Map<String, Integer> getStock(List<String> productIds);
    String getWarehouse(String productId);
}
