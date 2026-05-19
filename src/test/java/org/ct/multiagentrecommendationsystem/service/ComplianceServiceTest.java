package org.ct.multiagentrecommendationsystem.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ComplianceServiceTest {

    private final ComplianceService service = new ComplianceService();

    @Test
    void shouldFilterChineseForbiddenWords() {
        String result = service.filter("最好用的产品，绝对划算");
        assertFalse(result.contains("最好"));
        assertFalse(result.contains("绝对"));
    }

    @Test
    void shouldDetectViolations() {
        assertTrue(service.hasViolation("这是最好的产品"));
        assertFalse(service.hasViolation("这是一款不错的产品"));
    }

    @Test
    void shouldFindViolations() {
        List<String> violations = service.findViolations("最好的唯一的顶级产品");
        assertFalse(violations.isEmpty());
    }

    @Test
    void shouldNotModifyCleanText() {
        String clean = "性价比高的优质产品，品质有保障";
        assertEquals(clean, service.filter(clean));
    }

    @Test
    void shouldFilterMultipleForbiddenWords() {
        String result = service.filter("全网最低价，顶级品质，绝对100%正品");
        assertFalse(result.contains("全网最低"));
        assertFalse(result.contains("顶级"));
        assertFalse(result.contains("绝对"));
        assertFalse(result.contains("100%"));
    }
}
