package org.ct.multiagentrecommendationsystem.repository.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ct.multiagentrecommendationsystem.model.CoPurchase;
import org.ct.multiagentrecommendationsystem.repository.CoPurchaseRepository;
import org.springframework.stereotype.Repository;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class JsonCoPurchaseRepository implements CoPurchaseRepository {

    private Map<String, List<CoPurchase>> index;

    public JsonCoPurchaseRepository() {
        load();
    }

    private void load() {
        try {
            ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
            InputStream is = getClass().getClassLoader().getResourceAsStream("data/co_purchase.json");
            List<CoPurchase> list = mapper.readValue(is, new TypeReference<List<CoPurchase>>() {});
            index = list.stream().collect(Collectors.groupingBy(CoPurchase::getProductId));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load co_purchase.json", e);
        }
    }

    @Override
    public List<CoPurchase> findByProductId(String productId) {
        return index.getOrDefault(productId, Collections.emptyList());
    }
}
