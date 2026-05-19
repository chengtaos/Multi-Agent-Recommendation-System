package org.ct.multiagentrecommendationsystem.repository.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ct.multiagentrecommendationsystem.model.Product;
import org.ct.multiagentrecommendationsystem.repository.ProductRepository;
import org.springframework.stereotype.Repository;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class JsonProductRepository implements ProductRepository {

    private final Map<String, Product> productMap = new HashMap<>();
    private List<Product> allProducts;

    public JsonProductRepository() {
        load();
    }

    private void load() {
        try {
            ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
            InputStream is = getClass().getClassLoader().getResourceAsStream("data/products.json");
            List<Product> products = mapper.readValue(is, new TypeReference<List<Product>>() {});
            allProducts = products;
            for (Product p : products) {
                productMap.put(p.getProductId(), p);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load products.json", e);
        }
    }

    @Override
    public Optional<Product> findById(String productId) {
        return Optional.ofNullable(productMap.get(productId));
    }

    @Override
    public List<Product> findByCategory(String category) {
        return allProducts.stream()
                .filter(p -> p.getCategory().equals(category))
                .collect(Collectors.toList());
    }

    @Override
    public List<Product> findByIds(List<String> productIds) {
        return productIds.stream()
                .map(productMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<Product> findHotProducts(int limit) {
        return allProducts.stream()
                .sorted(Comparator.comparingInt(Product::getSalesRank))
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public List<Product> findAll() {
        return new ArrayList<>(allProducts);
    }
}
