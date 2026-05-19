package org.ct.multiagentrecommendationsystem.agent;

import org.ct.multiagentrecommendationsystem.model.AgentResult;
import org.ct.multiagentrecommendationsystem.model.InventoryStatus;
import org.ct.multiagentrecommendationsystem.model.Product;
import org.ct.multiagentrecommendationsystem.repository.InventoryRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class InventoryAgent extends BaseAgent {

    private final InventoryRepository inventoryRepository;

    public InventoryAgent(InventoryRepository inventoryRepository) {
        super("inventory", Duration.ofSeconds(5), 1, 300);
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected AgentResult doExecute(Map<String, Object> input) {
        long startTime = System.currentTimeMillis();
        List<Product> products = (List<Product>) input.get("products");

        if (products == null || products.isEmpty()) {
            return AgentResult.success(name, Map.of(
                    "availableProducts", List.of(),
                    "alerts", List.of(),
                    "purchaseLimits", Map.of()
            ), System.currentTimeMillis() - startTime);
        }

        List<String> productIds = products.stream().map(Product::getProductId).toList();
        Map<String, Integer> stockMap = inventoryRepository.getStock(productIds);

        List<String> availableProducts = new ArrayList<>();
        List<InventoryStatus> alerts = new ArrayList<>();
        Map<String, Integer> purchaseLimits = new java.util.HashMap<>();

        for (String pid : productIds) {
            int stock = stockMap.getOrDefault(pid, 0);
            InventoryStatus status = InventoryStatus.builder().productId(pid).stock(stock).build();

            if (stock <= 0) {
                status.setStatus("out_of_stock");
                status.setAlertMsg("该商品已缺货");
                status.setMaxPurchaseQty(0);
                alerts.add(status);
                // 不加入 availableProducts
            } else if (stock <= 50) {
                status.setStatus("critical");
                status.setAlertMsg("库存紧急，仅剩" + stock + "件");
                status.setMaxPurchaseQty(1);
                alerts.add(status);
                purchaseLimits.put(pid, 1);
                availableProducts.add(pid);
            } else if (stock <= 100) {
                status.setStatus("low");
                status.setAlertMsg("库存紧张");
                status.setMaxPurchaseQty(2);
                alerts.add(status);
                purchaseLimits.put(pid, 2);
                availableProducts.add(pid);
            } else {
                status.setStatus("normal");
                status.setMaxPurchaseQty(5);
                availableProducts.add(pid);
            }
        }

        long latency = System.currentTimeMillis() - startTime;
        return AgentResult.success(name, Map.of(
                "availableProducts", availableProducts,
                "alerts", alerts,
                "purchaseLimits", purchaseLimits,
                "inventoryStatuses", buildStatusMap(alerts, productIds, stockMap)
        ), latency);
    }

    private Map<String, InventoryStatus> buildStatusMap(List<InventoryStatus> alerts,
                                                        List<String> allIds,
                                                        Map<String, Integer> stockMap) {
        Map<String, InventoryStatus> map = alerts.stream()
                .collect(Collectors.toMap(InventoryStatus::getProductId, s -> s));
        for (String pid : allIds) {
            map.putIfAbsent(pid, InventoryStatus.builder()
                    .productId(pid)
                    .stock(stockMap.getOrDefault(pid, 0))
                    .status("normal")
                    .maxPurchaseQty(5)
                    .build());
        }
        return map;
    }
}
