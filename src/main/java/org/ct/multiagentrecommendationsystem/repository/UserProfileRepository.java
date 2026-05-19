package org.ct.multiagentrecommendationsystem.repository;

import org.ct.multiagentrecommendationsystem.model.UserProfile;

import java.util.Optional;

public interface UserProfileRepository {
    Optional<UserProfile> findById(String userId);
}
