package org.ct.multiagentrecommendationsystem.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ct.multiagentrecommendationsystem.model.AgentResult;
import org.ct.multiagentrecommendationsystem.model.RfmScore;
import org.ct.multiagentrecommendationsystem.model.UserBehavior;
import org.ct.multiagentrecommendationsystem.model.UserProfile;
import org.ct.multiagentrecommendationsystem.repository.BehaviorRepository;
import org.ct.multiagentrecommendationsystem.repository.UserProfileRepository;
import org.ct.multiagentrecommendationsystem.service.LlmService;
import org.ct.multiagentrecommendationsystem.service.RfmCalculator;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class UserProfileAgent extends BaseAgent {

    private final UserProfileRepository userProfileRepository;
    private final BehaviorRepository behaviorRepository;
    private final RfmCalculator rfmCalculator;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    public UserProfileAgent(UserProfileRepository userProfileRepository,
                            BehaviorRepository behaviorRepository,
                            RfmCalculator rfmCalculator,
                            LlmService llmService) {
        super("user_profile", Duration.ofSeconds(10), 2, 500);
        this.userProfileRepository = userProfileRepository;
        this.behaviorRepository = behaviorRepository;
        this.rfmCalculator = rfmCalculator;
        this.llmService = llmService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    protected AgentResult doExecute(Map<String, Object> input) {
        String userId = (String) input.get("userId");
        long startTime = System.currentTimeMillis();

        UserProfile profile = userProfileRepository.findById(userId)
                .orElse(UserProfile.builder().userId(userId).build());

        List<UserBehavior> behaviors = behaviorRepository.findByUserId(userId);
        RfmScore rfm = rfmCalculator.calculate(userId, behaviors);
        profile.setRfmScore(rfm);

        List<String> recentPurchases = behaviors.stream()
                .filter(b -> "purchase".equals(b.getAction()))
                .map(UserBehavior::getProductId)
                .distinct()
                .toList();
        profile.setRecentPurchases(recentPurchases);

        try {
            String prompt = String.format(
                    "User ID: %s\nAge: %d, Gender: %s, City: %s\n" +
                            "RFM Score: R=%.2f, F=%.2f, M=%.2f\n" +
                            "Total Orders: %d, Avg Order Value: %.2f\n" +
                            "Recent Purchases: %s\n\n" +
                            "Output JSON:\n" +
                            "{\"segments\":[\"active\",\"price_sensitive\"],\n" +
                            "\"preferredCategories\":[\"phones\",\"headphones\"],\n" +
                            "\"priceRange\":[500,5000],\n" +
                            "\"realTimeTags\":[\"active_time:evening\"]}",
                    profile.getUserId(), profile.getAge(), profile.getGender(), profile.getCity(),
                    profile.getRfmScore().getR(), profile.getRfmScore().getF(), profile.getRfmScore().getM(),
                    profile.getTotalOrders(), profile.getAvgOrderValue(),
                    profile.getRecentPurchases());

            String llmResponse = llmService.chat(
                    "You are a user profile analysis expert. Output JSON based on user data.",
                    prompt);
            Map<String, Object> llmResult = objectMapper.readValue(llmResponse,
                    new TypeReference<Map<String, Object>>() {});

            if (llmResult.containsKey("segments")) {
                @SuppressWarnings("unchecked")
                List<String> segments = (List<String>) llmResult.get("segments");
                profile.setSegments(segments);
            }
            if (llmResult.containsKey("preferredCategories")) {
                @SuppressWarnings("unchecked")
                List<String> cats = (List<String>) llmResult.get("preferredCategories");
                profile.setPreferredCategories(cats);
            }
            if (llmResult.containsKey("priceRange")) {
                @SuppressWarnings("unchecked")
                List<Integer> range = (List<Integer>) llmResult.get("priceRange");
                if (range != null && range.size() >= 2) {
                    profile.setPriceRange(new double[]{range.get(0), range.get(1)});
                }
            }
            if (llmResult.containsKey("realTimeTags")) {
                @SuppressWarnings("unchecked")
                List<String> tags = (List<String>) llmResult.get("realTimeTags");
                profile.setRealTimeTags(tags);
            }
        } catch (Exception e) {
            log.warn("[user_profile] LLM inference failed, using defaults: {}", e.getMessage());
            if (profile.getPreferredCategories() == null) {
                profile.setPreferredCategories(inferCategoriesFromBehaviors(behaviors));
            }
            if (profile.getSegments() == null) {
                profile.setSegments(determineSegmentFromRfm(rfm));
            }
            if (profile.getPriceRange() == null) {
                profile.setPriceRange(new double[]{100, 10000});
            }
        }

        long latency = System.currentTimeMillis() - startTime;
        return AgentResult.success(name, Map.of("profile", profile), latency);
    }

    private List<String> inferCategoriesFromBehaviors(List<UserBehavior> behaviors) {
        return behaviors.stream()
                .filter(b -> "purchase".equals(b.getAction()))
                .map(b -> "electronics")
                .distinct()
                .limit(3)
                .toList();
    }

    private List<String> determineSegmentFromRfm(RfmScore rfm) {
        if (rfm.getR() > 0.7 && rfm.getF() > 0.5 && rfm.getM() > 0.5) return List.of("high_value", "active");
        if (rfm.getR() > 0.5) return List.of("active");
        if (rfm.getR() < 0.2 && rfm.getM() > 0.5) return List.of("churn_risk", "high_value");
        if (rfm.getF() < 0.2) return List.of("new_user");
        return List.of("active");
    }
}
