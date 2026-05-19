package org.ct.multiagentrecommendationsystem.repository.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ct.multiagentrecommendationsystem.model.UserProfile;
import org.ct.multiagentrecommendationsystem.repository.BehaviorRepository;
import org.ct.multiagentrecommendationsystem.repository.UserProfileRepository;
import org.springframework.stereotype.Repository;

import java.io.InputStream;
import java.util.*;

@Repository
public class JsonUserProfileRepository implements UserProfileRepository {

    private final Map<String, UserProfile> userMap = new HashMap<>();
    private final BehaviorRepository behaviorRepository;

    public JsonUserProfileRepository(BehaviorRepository behaviorRepository) {
        this.behaviorRepository = behaviorRepository;
        load();
    }

    private void load() {
        try {
            ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
            InputStream is = getClass().getClassLoader().getResourceAsStream("data/users.json");
            List<JsonNode> users = mapper.readValue(is, new TypeReference<List<JsonNode>>() {});
            for (JsonNode node : users) {
                UserProfile profile = UserProfile.builder()
                        .userId(node.get("userId").asText())
                        .age(node.get("age").asInt())
                        .gender(node.get("gender").asText())
                        .city(node.get("city").asText())
                        .build();
                userMap.put(profile.getUserId(), profile);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load users.json", e);
        }
    }

    @Override
    public Optional<UserProfile> findById(String userId) {
        UserProfile profile = userMap.get(userId);
        if (profile == null) {
            return Optional.empty();
        }
        // 附加行为统计
        var behaviors = behaviorRepository.findByUserId(userId);
        int totalOrders = (int) behaviors.stream().filter(b -> "purchase".equals(b.getAction())).count();
        double avgOrderValue = behaviors.stream()
                .filter(b -> "purchase".equals(b.getAction()))
                .mapToDouble(b -> b.getAmount())
                .average()
                .orElse(0);
        profile.setTotalOrders(totalOrders);
        profile.setAvgOrderValue(avgOrderValue);
        return Optional.of(profile);
    }
}
