package org.ct.multiagentrecommendationsystem.repository;

import org.ct.multiagentrecommendationsystem.model.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Optional<Product> findById(String productId);
    List<Product> findByCategory(String category);
    List<Product> findByIds(List<String> productIds);
    List<Product> findHotProducts(int limit);
    List<Product> findAll();
}
