package org.ct.multiagentrecommendationsystem.agent;

import org.ct.multiagentrecommendationsystem.model.AgentResult;
import org.ct.multiagentrecommendationsystem.model.CoPurchase;
import org.ct.multiagentrecommendationsystem.model.Product;
import org.ct.multiagentrecommendationsystem.model.UserProfile;
import org.ct.multiagentrecommendationsystem.repository.CoPurchaseRepository;
import org.ct.multiagentrecommendationsystem.repository.ProductRepository;
import org.ct.multiagentrecommendationsystem.service.VectorSearchService;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class SearchAgent extends BaseAgent {

    private final ProductRepository productRepository;
    private final CoPurchaseRepository coPurchaseRepository;
    private final VectorSearchService vectorSearchService;

    public SearchAgent(ProductRepository productRepository,
                       CoPurchaseRepository coPurchaseRepository,
                       VectorSearchService vectorSearchService) {
        super("search", Duration.ofSeconds(10), 2, 500);
        this.productRepository = productRepository;
        this.coPurchaseRepository = coPurchaseRepository;
        this.vectorSearchService = vectorSearchService;
    }

    @Override
    protected AgentResult doExecute(Map<String, Object> input) {
        long startTime = System.currentTimeMillis();
        UserProfile profile = (UserProfile) input.get("userProfile");

        CompletableFuture<List<Product>> f1 = CompletableFuture.supplyAsync(() -> vectorSearch(profile));
        CompletableFuture<List<Product>> f2 = CompletableFuture.supplyAsync(() -> categorySearch(profile));
        CompletableFuture<List<Product>> f3 = CompletableFuture.supplyAsync(() -> cfSearch(profile));
        CompletableFuture<List<Product>> f4 = CompletableFuture.supplyAsync(this::hotSearch);

        CompletableFuture.allOf(f1, f2, f3, f4).join();

        Set<String> seen = new LinkedHashSet<>();
        List<Product> candidates = new ArrayList<>();
        List<CompletableFuture<List<Product>>> futures = List.of(f1, f2, f3, f4);

        for (CompletableFuture<List<Product>> future : futures) {
            try {
                for (Product p : future.get()) {
                    if (seen.add(p.getProductId())) {
                        candidates.add(p);
                    }
                }
            } catch (Exception e) {
                log.warn("[search] recall path failed: {}", e.getMessage());
            }
        }

        int maxCandidates = Math.min(100, Math.max(50, 50));
        if (candidates.size() > maxCandidates) {
            candidates = candidates.subList(0, maxCandidates);
        }

        long latency = System.currentTimeMillis() - startTime;
        return AgentResult.success(name, Map.of("candidates", candidates), latency);
    }

    private List<Product> vectorSearch(UserProfile profile) {
        try {
            String query = buildVectorQuery(profile);
            return vectorSearchService.search(query, 15);
        } catch (Exception e) {
            log.warn("[search] vector search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Product> categorySearch(UserProfile profile) {
        try {
            if (profile == null || profile.getPreferredCategories() == null) {
                return List.of();
            }
            return profile.getPreferredCategories().stream()
                    .flatMap(cat -> productRepository.findByCategory(cat).stream())
                    .peek(p -> p.setRecallSource("category"))
                    .limit(15)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[search] category search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Product> cfSearch(UserProfile profile) {
        try {
            if (profile == null || profile.getRecentPurchases() == null) {
                return List.of();
            }
            return profile.getRecentPurchases().stream()
                    .flatMap(pid -> coPurchaseRepository.findByProductId(pid).stream())
                    .sorted(Comparator.comparingInt(CoPurchase::getFrequency).reversed())
                    .limit(15)
                    .map(cp -> {
                        Product p = productRepository.findById(cp.getCoPurchasedProductId()).orElse(null);
                        if (p != null) p.setRecallSource("cf");
                        return p;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[search] cf search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Product> hotSearch() {
        try {
            return productRepository.findHotProducts(15).stream()
                    .peek(p -> p.setRecallSource("hot"))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[search] hot search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private String buildVectorQuery(UserProfile profile) {
        if (profile == null) return "hot products";
        StringBuilder sb = new StringBuilder();
        if (profile.getPreferredCategories() != null && !profile.getPreferredCategories().isEmpty()) {
            sb.append("preferred: ").append(String.join(" ", profile.getPreferredCategories()));
        }
        if (profile.getPriceRange() != null && profile.getPriceRange().length >= 2) {
            sb.append(" budget: ").append((int) profile.getPriceRange()[0])
                    .append("-").append((int) profile.getPriceRange()[1]);
        }
        if (profile.getRealTimeTags() != null) {
            sb.append(" ").append(String.join(" ", profile.getRealTimeTags()));
        }
        return sb.toString().isEmpty() ? "hot products" : sb.toString();
    }
}
