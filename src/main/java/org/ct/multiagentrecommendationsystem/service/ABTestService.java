package org.ct.multiagentrecommendationsystem.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ABTestService {

    private final String experimentId;
    private final Map<String, Integer> groupWeights;

    public ABTestService(
            @Value("${ab-test.experiment-id:rec_strategy}") String experimentId,
            @Value("${ab-test.control-weight:50}") int controlWeight,
            @Value("${ab-test.treatment-llm-weight:50}") int treatmentWeight) {
        this.experimentId = experimentId;
        this.groupWeights = new LinkedHashMap<>();
        this.groupWeights.put("control", controlWeight);
        this.groupWeights.put("treatment_llm", treatmentWeight);
    }

    /**
     * MD5 hash-based bucketing — ensures consistent assignment for the same user
     */
    public String assign(String userId) {
        try {
            String input = experimentId + ":" + userId;
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            int hash = Math.abs(((digest[0] & 0xFF) << 8) | (digest[1] & 0xFF));

            int totalWeight = groupWeights.values().stream().mapToInt(Integer::intValue).sum();
            int bucket = hash % totalWeight;
            int cumulative = 0;
            for (Map.Entry<String, Integer> entry : groupWeights.entrySet()) {
                cumulative += entry.getValue();
                if (bucket < cumulative) {
                    return entry.getKey();
                }
            }
        } catch (Exception e) {
            // fallback
        }
        return "control";
    }

    public String getExperimentId() {
        return experimentId;
    }

    public Map<String, Integer> getGroupWeights() {
        return groupWeights;
    }
}
