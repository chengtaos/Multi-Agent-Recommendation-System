package org.ct.multiagentrecommendationsystem.repository;

import org.ct.multiagentrecommendationsystem.model.Review;

import java.util.List;

public interface ReviewRepository {
    List<Review> findByProductId(String productId);
    List<Review> findByProductIds(List<String> productIds);
}
