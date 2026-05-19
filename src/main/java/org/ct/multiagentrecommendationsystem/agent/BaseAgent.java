package org.ct.multiagentrecommendationsystem.agent;

import org.ct.multiagentrecommendationsystem.model.AgentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class BaseAgent {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final String name;
    protected final Duration timeout;
    protected final int maxRetries;
    protected final long initialBackoffMs;

    private final AtomicInteger callCount = new AtomicInteger(0);
    private final AtomicInteger errorCount = new AtomicInteger(0);

    protected BaseAgent(String name, Duration timeout, int maxRetries, long initialBackoffMs) {
        this.name = name;
        this.timeout = timeout;
        this.maxRetries = maxRetries;
        this.initialBackoffMs = initialBackoffMs;
    }

    /**
     * 子类实现具体业务逻辑
     */
    protected abstract AgentResult doExecute(Map<String, Object> input);

    /**
     * 模板方法：计时 → 重试 → 降级
     */
    public CompletableFuture<AgentResult> run(Map<String, Object> input) {
        return CompletableFuture.supplyAsync(() -> executeWithRetry(input))
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(this::fallback);
    }

    private AgentResult executeWithRetry(Map<String, Object> input) {
        callCount.incrementAndGet();
        long startTime = System.currentTimeMillis();
        int attempt = 0;

        while (attempt <= maxRetries) {
            try {
                AgentResult result = doExecute(input);
                long latency = System.currentTimeMillis() - startTime;
                result.setLatencyMs(latency);
                result.setRetryCount(attempt);
                return result;
            } catch (Exception e) {
                attempt++;
                log.warn("[{}] 执行失败 (attempt {}/{}): {}", name, attempt, maxRetries + 1, e.getMessage());
                if (attempt <= maxRetries) {
                    try {
                        long backoff = initialBackoffMs * (1L << (attempt - 1)); // 指数退避
                        Thread.sleep(Math.min(backoff, 5000));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        errorCount.incrementAndGet();
        long latency = System.currentTimeMillis() - startTime;
        return AgentResult.failed(name, "All " + (maxRetries + 1) + " attempts failed", latency);
    }

    /**
     * 降级处理 —— 超时或异常时调用
     */
    protected AgentResult fallback(Throwable t) {
        errorCount.incrementAndGet();
        log.error("[{}] 降级触发: {}", name, t.getMessage());
        return AgentResult.failed(name, t.getMessage(), timeout.toMillis());
    }

    public int getCallCount() {
        return callCount.get();
    }

    public int getErrorCount() {
        return errorCount.get();
    }

    public double getErrorRate() {
        int calls = callCount.get();
        return calls == 0 ? 0.0 : (double) errorCount.get() / calls;
    }

    public String getName() {
        return name;
    }
}
