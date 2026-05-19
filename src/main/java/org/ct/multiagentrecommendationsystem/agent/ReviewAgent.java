package org.ct.multiagentrecommendationsystem.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ct.multiagentrecommendationsystem.model.*;
import org.ct.multiagentrecommendationsystem.repository.ReviewRepository;
import org.ct.multiagentrecommendationsystem.service.LlmService;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class ReviewAgent extends BaseAgent {

    private final ReviewRepository reviewRepository;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    // 并行分析评价的线程池，限制并发数为 5，避免打爆 API
    private static final ExecutorService REVIEW_POOL = Executors.newFixedThreadPool(5);

    public ReviewAgent(ReviewRepository reviewRepository, LlmService llmService) {
        super("review", Duration.ofSeconds(60), 2, 500);
        this.reviewRepository = reviewRepository;
        this.llmService = llmService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected AgentResult doExecute(Map<String, Object> input) {
        long startTime = System.currentTimeMillis();
        List<Product> candidates = (List<Product>) input.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            return AgentResult.success(name, Map.of("reviewSummaryMap", Map.of()), System.currentTimeMillis() - startTime);
        }

        List<String> productIds = candidates.stream().map(Product::getProductId).toList();
        List<Review> allReviews = reviewRepository.findByProductIds(productIds);

        Map<String, List<Review>> reviewsByProduct = allReviews.stream()
                .collect(Collectors.groupingBy(Review::getProductId));

        Map<String, ReviewSummary> summaryMap = new ConcurrentHashMap<>();

        // 并行分析每个商品的评价
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (String pid : productIds) {
            futures.add(CompletableFuture.runAsync(() -> {
                analyzeProduct(pid, candidates, reviewsByProduct, summaryMap);
            }, REVIEW_POOL));
        }

        // 等待所有分析完成
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .orTimeout(50, TimeUnit.SECONDS)
                    .join();
        } catch (Exception e) {
            log.warn("[review] 部分商品分析超时或异常: {}", e.getMessage());
        }

        long latency = System.currentTimeMillis() - startTime;
        return AgentResult.success(name, Map.of("reviewSummaryMap", summaryMap), latency);
    }

    private void analyzeProduct(String pid, List<Product> candidates,
                                Map<String, List<Review>> reviewsByProduct,
                                Map<String, ReviewSummary> summaryMap) {
        List<Review> reviews = reviewsByProduct.getOrDefault(pid, List.of());
        double avgRating = reviews.stream().mapToDouble(Review::getRating).average().orElse(0);
        int count = reviews.size();

        try {
            String reviewSample = reviews.stream()
                    .limit(5)
                    .map(r -> String.format("rating %.1f: %s", r.getRating(), r.getContent()))
                    .collect(Collectors.joining("\n"));

            String productName = candidates.stream()
                    .filter(p -> p.getProductId().equals(pid))
                    .findFirst()
                    .map(Product::getName)
                    .orElse(pid);

            String prompt = String.format(
                    "Product: %s\nReviews:\n%s\n\nOutput ONLY valid JSON, no markdown:\n" +
                            "{\"sentiment\":\"positive/neutral/negative\",\n" +
                            "\"qualityScore\":0.85,\n" +
                            "\"keyTags\":[\"good quality\",\"fast shipping\"],\n" +
                            "\"riskFlags\":[]}",
                    productName, reviewSample);

            String llmResponse = llmService.chat(
                    "You are a review analysis expert. Analyze product reviews and output ONLY JSON without markdown.",
                    prompt);
            Map<String, Object> llmResult = objectMapper.readValue(llmResponse,
                    new TypeReference<Map<String, Object>>() {});

            ReviewSummary summary = ReviewSummary.builder()
                    .productId(pid)
                    .sentiment((String) llmResult.getOrDefault("sentiment", "neutral"))
                    .qualityScore(((Number) llmResult.getOrDefault("qualityScore", avgRating / 5.0)).doubleValue())
                    .keyTags((List<String>) llmResult.getOrDefault("keyTags", List.of()))
                    .riskFlags((List<String>) llmResult.getOrDefault("riskFlags", List.of()))
                    .avgRating(avgRating)
                    .reviewCount(count)
                    .build();

            summaryMap.put(pid, summary);
        } catch (Exception e) {
            log.warn("[review] LLM analysis failed for {}: {}", pid, e.getMessage());
            double qs = Math.min(1.0, avgRating / 5.0);
            ReviewSummary summary = ReviewSummary.builder()
                    .productId(pid)
                    .sentiment(avgRating >= 4.0 ? "positive" : avgRating >= 3.0 ? "neutral" : "negative")
                    .qualityScore(qs)
                    .keyTags(List.of())
                    .riskFlags(avgRating < 3.5 ? List.of("low rating") : List.of())
                    .avgRating(avgRating)
                    .reviewCount(count)
                    .build();
            summaryMap.put(pid, summary);
        }
    }
}
