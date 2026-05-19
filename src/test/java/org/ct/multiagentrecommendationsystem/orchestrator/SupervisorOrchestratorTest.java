package org.ct.multiagentrecommendationsystem.orchestrator;

import org.ct.multiagentrecommendationsystem.model.RecommendationRequest;
import org.ct.multiagentrecommendationsystem.model.RecommendationResponse;
import org.ct.multiagentrecommendationsystem.repository.*;
import org.ct.multiagentrecommendationsystem.repository.impl.*;
import org.ct.multiagentrecommendationsystem.service.*;
import org.ct.multiagentrecommendationsystem.agent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class SupervisorOrchestratorTest {

    private SupervisorOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        // Create real repositories (they load JSON from test classpath too)
        ProductRepository productRepo = new JsonProductRepository();
        BehaviorRepository behaviorRepo = new JsonBehaviorRepository();
        UserProfileRepository userProfileRepo = new JsonUserProfileRepository(behaviorRepo);
        ReviewRepository reviewRepo = new JsonReviewRepository();
        InventoryRepository inventoryRepo = new JsonInventoryRepository();
        CoPurchaseRepository coPurchaseRepo = new JsonCoPurchaseRepository();

        // Services
        LlmService llmService = new MockLlmService();
        RfmCalculator rfmCalculator = new RfmCalculator();
        ComplianceService complianceService = new ComplianceService();
        ABTestService abTestService = new ABTestService("rec_strategy", 50, 50);
        VectorSearchService vectorSearchService = new VectorSearchService(llmService, productRepo, 20);

        // Build vector index
        vectorSearchService.buildIndex();

        // Agents
        UserProfileAgent userProfileAgent = new UserProfileAgent(userProfileRepo, behaviorRepo, rfmCalculator, llmService);
        SearchAgent searchAgent = new SearchAgent(productRepo, coPurchaseRepo, vectorSearchService);
        ReviewAgent reviewAgent = new ReviewAgent(reviewRepo, llmService);
        RankingAgent rankingAgent = new RankingAgent(llmService);
        InventoryAgent inventoryAgent = new InventoryAgent(inventoryRepo);
        MarketingCopyAgent marketingCopyAgent = new MarketingCopyAgent(llmService, complianceService);

        ExecutorService agentPool = Executors.newFixedThreadPool(6);

        orchestrator = new SupervisorOrchestrator(userProfileAgent, searchAgent, reviewAgent,
                rankingAgent, inventoryAgent, marketingCopyAgent, abTestService, agentPool);
    }

    @Test
    void shouldReturnRecommendations() {
        RecommendationRequest request = RecommendationRequest.builder()
                .userId("U001")
                .scene("homepage")
                .numItems(5)
                .build();

        RecommendationResponse response = orchestrator.recommend(request);

        assertNotNull(response);
        assertNotNull(response.getRequestId());
        assertEquals("U001", response.getUserId());
        assertNotNull(response.getProducts());
        assertNotNull(response.getAgentResults());
        assertNotNull(response.getMarketingCopies());
        assertTrue(response.getTotalLatencyMs() > 0);
    }

    @Test
    void shouldIncludeAllAgentResults() {
        RecommendationRequest request = RecommendationRequest.builder()
                .userId("U002")
                .scene("homepage")
                .numItems(3)
                .build();

        RecommendationResponse response = orchestrator.recommend(request);

        assertTrue(response.getAgentResults().containsKey("user_profile"));
        assertTrue(response.getAgentResults().containsKey("search"));
        assertTrue(response.getAgentResults().containsKey("review"));
        assertTrue(response.getAgentResults().containsKey("ranking"));
        assertTrue(response.getAgentResults().containsKey("inventory"));
        assertTrue(response.getAgentResults().containsKey("marketing"));
    }

    @Test
    void shouldRespectNumItems() {
        RecommendationRequest request = RecommendationRequest.builder()
                .userId("U001")
                .numItems(3)
                .build();

        RecommendationResponse response = orchestrator.recommend(request);
        assertTrue(response.getProducts().size() <= 3);
    }

    @Test
    void shouldHandleUnknownUser() {
        RecommendationRequest request = RecommendationRequest.builder()
                .userId("UNKNOWN")
                .numItems(5)
                .build();

        RecommendationResponse response = orchestrator.recommend(request);
        assertNotNull(response);
        // Should still work with defaults
    }
}
