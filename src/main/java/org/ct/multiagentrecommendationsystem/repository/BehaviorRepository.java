package org.ct.multiagentrecommendationsystem.repository;

import org.ct.multiagentrecommendationsystem.model.UserBehavior;

import java.util.List;

public interface BehaviorRepository {
    List<UserBehavior> findByUserId(String userId);
}
