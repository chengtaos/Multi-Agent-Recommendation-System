package org.ct.multiagentrecommendationsystem.repository.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ct.multiagentrecommendationsystem.repository.InventoryRepository;
import org.springframework.stereotype.Repository;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class JsonInventoryRepository implements InventoryRepository {

    private final Map<String, Integer> stockMap = new HashMap<>();
    private final Map<String, String> warehouseMap = new HashMap<>();

    public JsonInventoryRepository() {
        load();
    }

    private void load() {
        try {
            ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
            InputStream is = getClass().getClassLoader().getResourceAsStream("data/inventory.json");
            List<JsonNode> inventory = mapper.readValue(is, new TypeReference<List<JsonNode>>() {});
            for (JsonNode node : inventory) {
                String productId = node.get("productId").asText();
                int stock = node.get("stock").asInt();
                String warehouse = node.get("warehouse").asText();
                stockMap.put(productId, stock);
                warehouseMap.put(productId, warehouse);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load inventory.json", e);
        }
    }

    @Override
    public int getStock(String productId) {
        return stockMap.getOrDefault(productId, 0);
    }

    @Override
    public Map<String, Integer> getStock(List<String> productIds) {
        return productIds.stream()
                .collect(Collectors.toMap(id -> id, this::getStock));
    }

    @Override
    public String getWarehouse(String productId) {
        return warehouseMap.getOrDefault(productId, "未知");
    }
}
