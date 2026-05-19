package org.ct.multiagentrecommendationsystem.repository.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ct.multiagentrecommendationsystem.model.Review;
import org.ct.multiagentrecommendationsystem.repository.ReviewRepository;
import org.springframework.stereotype.Repository;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class JsonReviewRepository implements ReviewRepository {

    private final Map<String, List<Review>> reviewMap = new HashMap<>();

    public JsonReviewRepository() {
        load();
    }

    private void load() {
        try {
            ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
            InputStream is = getClass().getClassLoader().getResourceAsStream("data/reviews.json");
            List<JsonNode> productReviews = mapper.readValue(is, new TypeReference<List<JsonNode>>() {});
            for (JsonNode node : productReviews) {
                String productId = node.get("productId").asText();
                List<Review> reviews = new ArrayList<>();
                for (JsonNode r : node.get("reviews")) {
                    Review review = mapper.convertValue(r, Review.class);
                    reviews.add(review);
                }
                reviewMap.put(productId, reviews);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load reviews.json", e);
        }
    }

    @Override
    public List<Review> findByProductId(String productId) {
        return reviewMap.getOrDefault(productId, Collections.emptyList());
    }

    @Override
    public List<Review> findByProductIds(List<String> productIds) {
        return productIds.stream()
                .flatMap(id -> findByProductId(id).stream())
                .collect(Collectors.toList());
    }
}
