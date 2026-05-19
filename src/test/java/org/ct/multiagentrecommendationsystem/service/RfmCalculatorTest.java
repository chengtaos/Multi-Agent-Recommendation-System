package org.ct.multiagentrecommendationsystem.service;

import org.ct.multiagentrecommendationsystem.model.RfmScore;
import org.ct.multiagentrecommendationsystem.model.UserBehavior;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RfmCalculatorTest {

    private final RfmCalculator calculator = new RfmCalculator();

    @Test
    void shouldReturnZeroForNoPurchases() {
        List<UserBehavior> behaviors = List.of(
                behavior("U001", "P001", "view")
        );
        RfmScore score = calculator.calculate("U001", behaviors);
        assertEquals(0, score.getR());
        assertEquals(0, score.getF());
        assertEquals(0, score.getM());
    }

    @Test
    void shouldCalculateRfmForRecentPurchase() {
        List<UserBehavior> behaviors = List.of(
                behavior("U001", "P001", "purchase", LocalDateTime.now().minusDays(1), 1000)
        );
        RfmScore score = calculator.calculate("U001", behaviors);
        assertTrue(score.getR() > 0.9);
        assertEquals(0.1, score.getF(), 0.01);
        assertEquals(0.02, score.getM(), 0.01);
    }

    @Test
    void shouldCalculateHighRfmForFrequentBuyer() {
        List<UserBehavior> behaviors = List.of(
                behavior("U001", "P001", "purchase", LocalDateTime.now().minusDays(1), 5000),
                behavior("U001", "P002", "purchase", LocalDateTime.now().minusDays(3), 6000),
                behavior("U001", "P003", "purchase", LocalDateTime.now().minusDays(5), 7000),
                behavior("U001", "P004", "purchase", LocalDateTime.now().minusDays(7), 8000),
                behavior("U001", "P005", "purchase", LocalDateTime.now().minusDays(9), 9000),
                behavior("U001", "P006", "purchase", LocalDateTime.now().minusDays(11), 10000),
                behavior("U001", "P007", "purchase", LocalDateTime.now().minusDays(13), 5000),
                behavior("U001", "P008", "purchase", LocalDateTime.now().minusDays(15), 4000),
                behavior("U001", "P009", "purchase", LocalDateTime.now().minusDays(17), 3000),
                behavior("U001", "P010", "purchase", LocalDateTime.now().minusDays(19), 2000)
        );
        RfmScore score = calculator.calculate("U001", behaviors);
        assertTrue(score.getR() > 0.9);
        assertEquals(1.0, score.getF(), 0.01);
        assertTrue(score.getM() > 0.5);
    }

    @Test
    void shouldHandleOldPurchase() {
        List<UserBehavior> behaviors = List.of(
                behavior("U001", "P001", "purchase", LocalDateTime.now().minusDays(400), 1000)
        );
        RfmScore score = calculator.calculate("U001", behaviors);
        assertTrue(score.getR() < 0.1);
    }

    private UserBehavior behavior(String userId, String productId, String action) {
        return UserBehavior.builder()
                .userId(userId).productId(productId).action(action)
                .timestamp(LocalDateTime.now())
                .amount(0)
                .build();
    }

    private UserBehavior behavior(String userId, String productId, String action, LocalDateTime time, double amount) {
        return UserBehavior.builder()
                .userId(userId).productId(productId).action(action)
                .timestamp(time).amount(amount)
                .build();
    }
}
