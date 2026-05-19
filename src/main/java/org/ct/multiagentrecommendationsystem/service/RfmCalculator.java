package org.ct.multiagentrecommendationsystem.service;

import org.ct.multiagentrecommendationsystem.model.RfmScore;
import org.ct.multiagentrecommendationsystem.model.UserBehavior;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class RfmCalculator {

    /**
     * 计算用户的 RFM 得分（0-1 归一化）
     * R: Recency — 最近一次购买距今多久，越近越高
     * F: Frequency — 购买频次
     * M: Monetary — 消费总金额
     */
    public RfmScore calculate(String userId, List<UserBehavior> behaviors) {
        LocalDateTime now = LocalDateTime.now();

        // 只取购买行为
        List<UserBehavior> purchases = behaviors.stream()
                .filter(b -> "purchase".equals(b.getAction()))
                .toList();

        if (purchases.isEmpty()) {
            return RfmScore.builder().r(0).f(0).m(0).build();
        }

        // R: 最近一次购买距今天数，映射到 0-1（30天内=1，365天以上=0）
        LocalDateTime lastPurchase = purchases.stream()
                .map(UserBehavior::getTimestamp)
                .max(Comparator.naturalOrder())
                .orElse(now);
        long daysSince = Duration.between(lastPurchase, now).toDays();
        double r = Math.max(0, 1.0 - (daysSince / 365.0));

        // F: 购买总次数，映射到 0-1（>=10次=1）
        int frequency = purchases.size();
        double f = Math.min(1.0, frequency / 10.0);

        // M: 总消费金额，映射到 0-1（>=50000=1）
        double totalAmount = purchases.stream().mapToDouble(b -> b.getAmount() != 0 ? b.getAmount() : 0).sum();
        double m = Math.min(1.0, totalAmount / 50000.0);

        return RfmScore.builder().r(r).f(f).m(m).build();
    }
}
