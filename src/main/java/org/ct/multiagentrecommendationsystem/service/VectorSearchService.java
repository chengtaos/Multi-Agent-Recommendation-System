package org.ct.multiagentrecommendationsystem.service;

import org.ct.multiagentrecommendationsystem.model.Product;
import org.ct.multiagentrecommendationsystem.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class VectorSearchService {

    private final LlmService llmService;
    private final ProductRepository productRepository;
    private final int topK;

    // 产品向量缓存：productId -> float[]
    private Map<String, float[]> productEmbeddings = new HashMap<>();

    public VectorSearchService(LlmService llmService,
                               ProductRepository productRepository,
                               @Value("${vector.top-k:20}") int topK) {
        this.llmService = llmService;
        this.productRepository = productRepository;
        this.topK = topK;
    }

    /**
     * 启动时构建向量库 —— 由 DataInitializer 调用
     */
    public void buildIndex() {
        List<Product> products = productRepository.findAll();
        Map<String, float[]> embeddings = new HashMap<>();
        for (Product p : products) {
            String text = buildProductText(p);
            float[] embedding = llmService.embed(text);
            embeddings.put(p.getProductId(), embedding);
        }
        this.productEmbeddings = embeddings;
    }

    /**
     * 根据查询文本进行语义检索
     */
    public List<Product> search(String queryText, int limit) {
        float[] queryEmbedding = llmService.embed(queryText);
        int k = Math.min(limit > 0 ? limit : topK, productEmbeddings.size());

        // 计算余弦相似度并排序
        return productEmbeddings.entrySet().stream()
                .map(e -> {
                    Product p = productRepository.findById(e.getKey()).orElse(null);
                    double similarity = cosineSimilarity(queryEmbedding, e.getValue());
                    return new AbstractMap.SimpleEntry<>(p, similarity);
                })
                .filter(e -> e.getKey() != null)
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(k)
                .peek(e -> e.getKey().setRecallSource("vector"))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private String buildProductText(Product p) {
        return String.join(" ", p.getCategory(), p.getSubCategory() != null ? p.getSubCategory() : "",
                p.getBrand(), String.join(" ", p.getTags() != null ? p.getTags() : List.of()),
                p.getDescription() != null ? p.getDescription() : "");
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
