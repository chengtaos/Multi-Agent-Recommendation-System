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
public class HealthStatus {
    private String status;
    private Map<String, AgentHealth> agents;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentHealth {
        private int calls;
        private double errorRate;
    }
}
