package org.ct.multiagentrecommendationsystem.orchestrator;

import org.ct.multiagentrecommendationsystem.agent.*;
import org.ct.multiagentrecommendationsystem.model.*;
import org.ct.multiagentrecommendationsystem.service.ABTestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

public class SupervisorOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SupervisorOrchestrator.class);

    private final UserProfileAgent userProfileAgent;
    private final SearchAgent searchAgent;
    private final ReviewAgent reviewAgent;
    private final RankingAgent rankingAgent;
    private final InventoryAgent inventoryAgent;
    private final MarketingCopyAgent marketingCopyAgent;
    private final ABTestService abTestService;
    private final ExecutorService agentPool;

    public SupervisorOrchestrator(UserProfileAgent userProfileAgent,
                                  SearchAgent searchAgent,
                                  ReviewAgent reviewAgent,
                                  RankingAgent rankingAgent,
                                  InventoryAgent inventoryAgent,
                                  MarketingCopyAgent marketingCopyAgent,
                                  ABTestService abTestService,
                                  ExecutorService agentPool) {
        this.userProfileAgent = userProfileAgent;
        this.searchAgent = searchAgent;
        this.reviewAgent = reviewAgent;
        this.rankingAgent = rankingAgent;
        this.inventoryAgent = inventoryAgent;
        this.marketingCopyAgent = marketingCopyAgent;
        this.abTestService = abTestService;
        this.agentPool = agentPool;
    }

    public RecommendationResponse recommend(RecommendationRequest req) {
        long startTime = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        Map<String, AgentResult> agentResults = new LinkedHashMap<>();

        // A/B 分桶
        String expGroup = abTestService.assign(req.getUserId());

        // ========== Phase 1: 并行执行 ==========
        log.info("[Orchestrator] Phase 1 开始");

        Map<String, Object> input1 = Map.of("userId", req.getUserId(),
                "context", req.getContext() != null ? req.getContext() : Map.of());

        Map<String, Object> searchInput = Map.of("numItems", req.getNumItems());
        Map<String, Object> reviewInput = new HashMap<>();

        CompletableFuture<AgentResult> f1 = userProfileAgent.run(input1);
        CompletableFuture<AgentResult> f2 = searchAgent.run(searchInput);
        // ReviewAgent 需要候选集，先占位
        CompletableFuture<AgentResult> f3 = CompletableFuture.completedFuture(null);

        AgentResult profileResult = f1.join();
        agentResults.put("user_profile", profileResult);

        AgentResult searchResult = f2.join();
        agentResults.put("search", searchResult);

        // 提取 Phase 1 结果
        UserProfile profile = extractProfile(profileResult);
        @SuppressWarnings("unchecked")
        List<Product> candidates = (List<Product>) searchResult.getData().get("candidates");

        // 用 Phase 1 结果执行 ReviewAgent
        reviewInput = Map.of("candidates", candidates != null ? candidates : List.of());
        AgentResult reviewResult = reviewAgent.run(reviewInput).join();
        agentResults.put("review", reviewResult);

        log.info("[Orchestrator] Phase 1 完成");

        // ========== Phase 2: 并行执行 ==========
        log.info("[Orchestrator] Phase 2 开始");

        Map<String, Object> rankingInput = Map.of(
                "candidates", candidates != null ? candidates : List.of(),
                "userProfile", profile,
                "reviewSummaryMap", reviewResult.getData() != null ? reviewResult.getData().get("reviewSummaryMap") : Map.of(),
                "numItems", req.getNumItems()
        );

        Map<String, Object> inventoryInput = Map.of(
                "products", candidates != null ? candidates : List.of()
        );

        CompletableFuture<AgentResult> f4 = rankingAgent.run(rankingInput);
        CompletableFuture<AgentResult> f5 = inventoryAgent.run(inventoryInput);

        AgentResult rankingResult = f4.join();
        agentResults.put("ranking", rankingResult);

        AgentResult inventoryResult = f5.join();
        agentResults.put("inventory", inventoryResult);

        log.info("[Orchestrator] Phase 2 完成");

        // ========== 聚合：库存过滤 → TopN ==========
        @SuppressWarnings("unchecked")
        List<Product> ranked = rankingResult.getData() != null ?
                (List<Product>) rankingResult.getData().get("ranked") : null;
        @SuppressWarnings("unchecked")
        List<String> available = inventoryResult.getData() != null ?
                (List<String>) inventoryResult.getData().get("availableProducts") : null;

        List<Product> finalProducts;
        if (ranked != null && available != null) {
            Set<String> availableSet = new HashSet<>(available);
            finalProducts = ranked.stream()
                    .filter(p -> availableSet.contains(p.getProductId()))
                    .limit(req.getNumItems())
                    .collect(Collectors.toList());
        } else if (ranked != null) {
            finalProducts = ranked.stream().limit(req.getNumItems()).collect(Collectors.toList());
        } else {
            finalProducts = List.of();
        }

        // ========== Phase 3: 串行 ==========
        log.info("[Orchestrator] Phase 3 开始");

        Map<String, Object> copyInput = Map.of(
                "userProfile", profile,
                "products", finalProducts
        );

        AgentResult copyResult = marketingCopyAgent.run(copyInput).join();
        agentResults.put("marketing", copyResult);

        log.info("[Orchestrator] Phase 3 完成");

        // ========== 组装响应 ==========
        @SuppressWarnings("unchecked")
        List<MarketingCopy> copies = copyResult.getData() != null ?
                (List<MarketingCopy>) copyResult.getData().get("marketingCopies") : null;
        @SuppressWarnings("unchecked")
        Map<String, InventoryStatus> inventoryStatuses = inventoryResult.getData() != null ?
                (Map<String, InventoryStatus>) inventoryResult.getData().get("inventoryStatuses") : null;
        @SuppressWarnings("unchecked")
        Map<String, ReviewSummary> reviewSummaryMap = reviewResult.getData() != null ?
                (Map<String, ReviewSummary>) reviewResult.getData().get("reviewSummaryMap") : null;

        List<RecommendationResponse.ProductItem> productItems = new ArrayList<>();
        for (Product p : finalProducts) {
            productItems.add(RecommendationResponse.ProductItem.builder()
                    .productId(p.getProductId())
                    .name(p.getName())
                    .category(p.getCategory())
                    .price(p.getPrice())
                    .brand(p.getBrand())
                    .description(p.getDescription())
                    .score(p.getScore())
                    .recallSource(p.getRecallSource())
                    .reviewSummary(reviewSummaryMap != null ? reviewSummaryMap.get(p.getProductId()) : null)
                    .inventoryStatus(inventoryStatuses != null ? inventoryStatuses.get(p.getProductId()) : null)
                    .build());
        }

        long totalLatency = System.currentTimeMillis() - startTime;

        return RecommendationResponse.builder()
                .requestId(requestId)
                .userId(req.getUserId())
                .experimentGroup(expGroup)
                .products(productItems)
                .marketingCopies(copies)
                .agentResults(agentResults)
                .totalLatencyMs(totalLatency)
                .timestamp(LocalDateTime.now())
                .build();
    }

    private UserProfile extractProfile(AgentResult result) {
        if (result == null || !result.isSuccess() || result.getData() == null) return null;
        return (UserProfile) result.getData().get("profile");
    }

    public Map<String, BaseAgent> getAgents() {
        Map<String, BaseAgent> agents = new LinkedHashMap<>();
        agents.put("user_profile", userProfileAgent);
        agents.put("search", searchAgent);
        agents.put("review", reviewAgent);
        agents.put("ranking", rankingAgent);
        agents.put("inventory", inventoryAgent);
        agents.put("marketing", marketingCopyAgent);
        return agents;
    }
}
