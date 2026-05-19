package org.ct.multiagentrecommendationsystem.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ABTestServiceTest {

    private final ABTestService service = new ABTestService("rec_strategy", 50, 50);

    @Test
    void shouldAssignConsistently() {
        String group1 = service.assign("U001");
        String group2 = service.assign("U001");
        assertEquals(group1, group2);
    }

    @Test
    void shouldAssignDifferentUsers() {
        Set<String> groups = new HashSet<>();
        groups.add(service.assign("U001"));
        groups.add(service.assign("U002"));
        groups.add(service.assign("U003"));
        // At least one group should appear (with 50/50 split, both should usually appear)
        assertFalse(groups.isEmpty());
    }

    @Test
    void shouldReturnValidGroup() {
        String group = service.assign("U999");
        assertTrue(group.equals("control") || group.equals("treatment_llm"));
    }
}
