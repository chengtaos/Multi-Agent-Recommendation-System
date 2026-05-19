package org.ct.multiagentrecommendationsystem.repository.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ct.multiagentrecommendationsystem.model.UserBehavior;
import org.ct.multiagentrecommendationsystem.repository.BehaviorRepository;
import org.springframework.stereotype.Repository;

import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class JsonBehaviorRepository implements BehaviorRepository {

    private List<UserBehavior> allBehaviors;

    public JsonBehaviorRepository() {
        load();
    }

    private void load() {
        try {
            ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
            InputStream is = getClass().getClassLoader().getResourceAsStream("data/behaviors.json");
            allBehaviors = mapper.readValue(is, new TypeReference<List<UserBehavior>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to load behaviors.json", e);
        }
    }

    @Override
    public List<UserBehavior> findByUserId(String userId) {
        return allBehaviors.stream()
                .filter(b -> b.getUserId().equals(userId))
                .collect(Collectors.toList());
    }
}
