package org.ct.multiagentrecommendationsystem.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AgentThreadPoolConfig {

    @Value("${agent.thread-pool.core-size:6}")
    private int coreSize;

    @Bean("agentPool")
    public ExecutorService agentPool() {
        return Executors.newFixedThreadPool(coreSize, r -> {
            Thread t = new Thread(r);
            t.setName("agent-worker-" + t.threadId());
            t.setDaemon(true);
            return t;
        });
    }
}
