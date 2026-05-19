package org.ct.multiagentrecommendationsystem.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResult {
    private String agentName;
    private boolean success;
    private Map<String, Object> data;
    private String error;
    private long latencyMs;
    private int retryCount;

    public static AgentResult success(String name, Map<String, Object> data, long latencyMs) {
        return AgentResult.builder()
                .agentName(name)
                .success(true)
                .data(data)
                .latencyMs(latencyMs)
                .retryCount(0)
                .build();
    }

    public static AgentResult failed(String name, String error, long latencyMs) {
        return AgentResult.builder()
                .agentName(name)
                .success(false)
                .error(error)
                .latencyMs(latencyMs)
                .build();
    }
}
