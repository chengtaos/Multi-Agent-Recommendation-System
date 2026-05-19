package org.ct.multiagentrecommendationsystem.config;

import org.ct.multiagentrecommendationsystem.service.VectorSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final VectorSearchService vectorSearchService;

    public DataInitializer(VectorSearchService vectorSearchService) {
        this.vectorSearchService = vectorSearchService;
    }

    @Override
    public void run(String... args) {
        log.info("=== 开始构建向量检索引擎 ===");
        long start = System.currentTimeMillis();
        try {
            vectorSearchService.buildIndex();
            long elapsed = System.currentTimeMillis() - start;
            log.info("=== 向量检索引擎构建完成，耗时 {}ms ===", elapsed);
        } catch (Exception e) {
            log.error("向量检索引擎构建失败: {}", e.getMessage(), e);
        }
    }
}
