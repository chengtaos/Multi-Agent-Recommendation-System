package org.ct.multiagentrecommendationsystem.exception;

public class AgentExecutionException extends RuntimeException {
    private final String agentName;

    public AgentExecutionException(String agentName, String message) {
        super(message);
        this.agentName = agentName;
    }

    public AgentExecutionException(String agentName, String message, Throwable cause) {
        super(message, cause);
        this.agentName = agentName;
    }

    public String getAgentName() {
        return agentName;
    }
}
