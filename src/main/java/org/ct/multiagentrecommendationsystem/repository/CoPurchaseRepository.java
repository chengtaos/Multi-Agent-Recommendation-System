package org.ct.multiagentrecommendationsystem.repository;

import org.ct.multiagentrecommendationsystem.model.CoPurchase;

import java.util.List;

public interface CoPurchaseRepository {
    List<CoPurchase> findByProductId(String productId);
}
