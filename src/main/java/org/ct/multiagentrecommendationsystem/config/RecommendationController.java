package org.ct.multiagentrecommendationsystem.config;

import org.ct.multiagentrecommendationsystem.agent.BaseAgent;
import org.ct.multiagentrecommendationsystem.model.*;
import org.ct.multiagentrecommendationsystem.orchestrator.SupervisorOrchestrator;
import org.ct.multiagentrecommendationsystem.service.ABTestService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class RecommendationController {

    private final SupervisorOrchestrator orchestrator;
    private final ABTestService abTestService;

    public RecommendationController(SupervisorOrchestrator orchestrator, ABTestService abTestService) {
        this.orchestrator = orchestrator;
        this.abTestService = abTestService;
    }

    @PostMapping("/recommend")
    public RecommendationResponse recommend(@RequestBody RecommendationRequest request) {
        if (request.getNumItems() <= 0) {
            request.setNumItems(5);
        }
        return orchestrator.recommend(request);
    }

    @GetMapping("/health")
    public HealthStatus health() {
        Map<String, BaseAgent> agents = orchestrator.getAgents();
        Map<String, HealthStatus.AgentHealth> agentHealth = new LinkedHashMap<>();
        boolean allHealthy = true;

        for (Map.Entry<String, BaseAgent> entry : agents.entrySet()) {
            BaseAgent agent = entry.getValue();
            agentHealth.put(entry.getKey(), HealthStatus.AgentHealth.builder()
                    .calls(agent.getCallCount())
                    .errorRate(agent.getErrorRate())
                    .build());
            if (agent.getErrorRate() > 0.5) {
                allHealthy = false;
            }
        }

        return HealthStatus.builder()
                .status(allHealthy ? "healthy" : "degraded")
                .agents(agentHealth)
                .build();
    }

    @GetMapping("/experiments")
    public Map<String, Object> experiments() {
        return Map.of("experiments", List.of(
                ExperimentConfig.builder()
                        .id(abTestService.getExperimentId())
                        .groups(abTestService.getGroupWeights().keySet().stream().toList())
                        .split(abTestService.getGroupWeights().entrySet().stream()
                                .map(e -> e.getKey() + ":" + e.getValue())
                                .reduce((a, b) -> a + "/" + b)
                                .orElse(""))
                        .build()
        ));
    }
}
