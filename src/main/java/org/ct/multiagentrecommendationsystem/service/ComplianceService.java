package org.ct.multiagentrecommendationsystem.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class ComplianceService {

    private static final Set<String> FORBIDDEN_WORDS = Set.of(
            "最好", "第一", "国家级", "绝对", "100%", "顶级", "最佳",
            "唯一", "极品", "全网最低", "永久", "万能", "最高级",
            "全国第一", "世界第一", "销量第一", "排名第一"
    );

    private static final Set<String> FORBIDDEN_PATTERNS = Set.of(
            "最", "第一", "唯一", "绝对"
    );

    /**
     * 扫描并替换违禁词为 ***
     */
    public String filter(String text) {
        String result = text;
        for (String word : FORBIDDEN_WORDS) {
            if (result.contains(word)) {
                result = result.replace(word, "***");
            }
        }
        return result;
    }

    /**
     * 检查是否包含违禁词
     */
    public boolean hasViolation(String text) {
        return FORBIDDEN_WORDS.stream().anyMatch(text::contains);
    }

    /**
     * 获取发现的违禁词列表
     */
    public List<String> findViolations(String text) {
        return FORBIDDEN_WORDS.stream()
                .filter(text::contains)
                .toList();
    }
}
