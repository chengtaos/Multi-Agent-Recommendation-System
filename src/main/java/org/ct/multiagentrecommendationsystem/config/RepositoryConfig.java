package org.ct.multiagentrecommendationsystem.config;

import org.ct.multiagentrecommendationsystem.agent.*;
import org.ct.multiagentrecommendationsystem.orchestrator.SupervisorOrchestrator;
import org.ct.multiagentrecommendationsystem.repository.*;
import org.ct.multiagentrecommendationsystem.service.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;

/**
 * Agent 和 Orchestrator 的 Bean 注册
 * Repository 由 @Repository 自动扫描
 */
@Configuration
public class RepositoryConfig {

    @Bean
    public UserProfileAgent userProfileAgent(UserProfileRepository userProfileRepository,
                                              BehaviorRepository behaviorRepository,
                                              RfmCalculator rfmCalculator,
                                              LlmService llmService) {
        return new UserProfileAgent(userProfileRepository, behaviorRepository, rfmCalculator, llmService);
    }

    @Bean
    public SearchAgent searchAgent(ProductRepository productRepository,
                                    CoPurchaseRepository coPurchaseRepository,
                                    VectorSearchService vectorSearchService) {
        return new SearchAgent(productRepository, coPurchaseRepository, vectorSearchService);
    }

    @Bean
    public ReviewAgent reviewAgent(ReviewRepository reviewRepository, LlmService llmService) {
        return new ReviewAgent(reviewRepository, llmService);
    }

    @Bean
    public RankingAgent rankingAgent(LlmService llmService) {
        return new RankingAgent(llmService);
    }

    @Bean
    public InventoryAgent inventoryAgent(InventoryRepository inventoryRepository) {
        return new InventoryAgent(inventoryRepository);
    }

    @Bean
    public MarketingCopyAgent marketingCopyAgent(LlmService llmService, ComplianceService complianceService) {
        return new MarketingCopyAgent(llmService, complianceService);
    }

    @Bean
    public SupervisorOrchestrator supervisorOrchestrator(UserProfileAgent userProfileAgent,
                                                          SearchAgent searchAgent,
                                                          ReviewAgent reviewAgent,
                                                          RankingAgent rankingAgent,
                                                          InventoryAgent inventoryAgent,
                                                          MarketingCopyAgent marketingCopyAgent,
                                                          ABTestService abTestService,
                                                          ExecutorService agentPool) {
        return new SupervisorOrchestrator(userProfileAgent, searchAgent, reviewAgent,
                rankingAgent, inventoryAgent, marketingCopyAgent, abTestService, agentPool);
    }
}
