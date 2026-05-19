package org.ct.multiagentrecommendationsystem.service;

public interface LlmService {
    String chat(String systemPrompt, String userPrompt);
    float[] embed(String text);
}
