# Multi-Agent E-commerce Recommendation System

基于 **Supervisor 模式**的 6-Agent 协同电商推荐系统，Spring Boot + JDK 21 纯 Java 实现。

## 项目简介

这是一个以展示 **Multi-Agent 架构能力** 为核心的推荐系统。系统将电商推荐拆解为 6 个独立 Agent，由 Supervisor 编排器统一调度，通过 3 阶段并行流水线完成从用户画像分析到个性化文案生成的全链路推荐。

### 核心亮点

| 特性 | 说明 |
|------|------|
| **6-Agent 协同** | 画像分析 / 多路检索 / 评价舆情 / LLM 精排 / 库存决策 / 营销文案 |
| **3-Phase 并行编排** | CompletableFuture + 独立线程池，延迟 = max(每阶段) 而非累加 |
| **双模式 LLM** | 默认 Mock 模式零依赖启动，支持 OpenAI 兼容 API（DeepSeek / 智谱） |
| **向量语义检索** | Embedding + 余弦相似度，支持真实 API 和 TF-IDF 本地降级 |
| **多路召回策略** | 向量检索 + 类目匹配 + 协同过滤 + 热度兜底，四路互补 |
| **A/B 实验框架** | MD5 哈希分桶，同一用户实验组一致，支持 control/treatment 对比 |
| **Agent 级可靠性** | 独立超时 + 指数退避重试 + 降级，单 Agent 故障不影响整体 |
| **广告法合规过滤** | 营销文案自动扫描违禁词并替换 |
| **Repository 接口抽象** | 依赖倒置，当前 JSON 实现，可无缝切换数据库 |

---

## 架构总览

```
                POST /api/v1/recommend
                        │
                        ▼
        ┌───────────────────────────────┐
        │     RecommendationController   │
        └───────────────┬───────────────┘
                        │
                        ▼
        ┌───────────────────────────────┐
        │     SupervisorOrchestrator     │
        │   3-Phase 并行编排 + A/B分桶    │
        └───────────────┬───────────────┘
                        │
    ╔═══════════════════╪═══════════════════╗
    ║     Phase 1 — 并行 (3 Agent)          ║
    ║  UserProfile  │  SearchAgent  │ Review ║
    ╚═══════════════════╪═══════════════════╝
                        │
    ╔═══════════════════╪═══════════════════╗
    ║     Phase 2 — 并行 (2 Agent)          ║
    ║  RankingAgent   │   InventoryAgent    ║
    ╚═══════════════════╪═══════════════════╝
                        │
                 ┌──────┴──────┐
                 │  库存过滤→TopN │
                 └──────┬──────┘
                        │
    ╔═══════════════════╪═══════════════════╗
    ║     Phase 3 — 串行                    ║
    ║       MarketingCopyAgent              ║
    ╚═══════════════════╪═══════════════════╝
                        │
                        ▼
            ┌─────────────────────┐
            │ RecommendationResponse│
            └─────────────────────┘
```

**编排逻辑**：
- **Phase 1**：用户画像、商品检索、评价分析互不依赖，并行执行
- **Phase 2**：精排依赖 Phase 1（画像+候选+评价），库存依赖候选列表，两者间并行
- **Phase 3**：文案依赖最终结果，串行执行

---

## 6 大 Agent

### UserProfileAgent — 用户画像分析
- 从 Repository 读取基础画像 + 行为数据
- RFM 算法定量计算（纯代码，不调 LLM）
- LLM 定性推理输出 segments、偏好类目、价格区间

### SearchAgent — 多路商品检索
- **路 1**：向量语义检索（Embedding + 余弦相似度）
- **路 2**：类目关键词匹配
- **路 3**：协同过滤（Item-Based CF，"买了也买"）
- **路 4**：热度兜底（全站热销）
- 合并去重，标注召回来源

### ReviewAgent — 评价舆情分析
- 每个候选商品取 5 条评价样本
- LLM 分析输出 sentiment、qualityScore、keyTags、riskFlags
- 5 线程池并行处理，超时自动降级为规则引擎

### RankingAgent — LLM 精排
- 融合三路信号：画像匹配度 + 商品属性 + 评价质量
- LLM 综合排序输出 Top-N
- LLM 失败时降级为规则加权排序

### InventoryAgent — 库存决策
- 库存 ≤ 0：标记缺货，剔除
- 库存 ≤ 50：紧急预警，限购 1 件
- 库存 ≤ 100：库存紧张，限购 2 件

### MarketingCopyAgent — 营销文案
- 根据用户分群匹配文案模板
- LLM 生成 30-50 字个性化文案
- ComplianceService 广告法违禁词过滤

---

## 技术栈

| 类别 | 选型 | 说明 |
|------|------|------|
| 语言 | JDK 21 | Virtual Threads 就绪 |
| 框架 | Spring Boot 4.0.6 | 当前稳定版 |
| AI | Spring AI 2.0.0-M6 | OpenAI 兼容接口 |
| LLM | DeepSeek / Mock | 双模式，默认 Mock 零依赖 |
| Embedding | 智谱 AI / TF-IDF | 真实 API + 本地降级 |
| 并发 | CompletableFuture + 线程池 | 独立 Agent 线程池 |
| 序列化 | Jackson | 含 JavaTimeModule |
| 数据 | JSON + 内存加载 | 7 个数据文件，无需数据库 |
| 构建 | Maven Wrapper | 自带 mvnw，无需预装 Maven |

---

## 快速开始

### 前置条件

- JDK 21+
- 设置 `JAVA_HOME` 指向 JDK 21 安装目录

### 启动（Mock 模式，无需 API Key）

```bash
./mvnw spring-boot:run
```

服务启动在 `http://localhost:8081`，所有 Agent 使用规则引擎生成结果。

### 启动（LLM 模式）

创建 `src/main/resources/application-local.yaml`（已 .gitignore）：

```yaml
spring:
  profiles:
    active: openai
  ai:
    openai:
      api-key: <your-deepseek-api-key>
      embedding:
        api-key: <your-zhipu-api-key>
```

然后启动，Agent 将调用真实 LLM API。

### 运行测试

```bash
JAVA_HOME=<jdk21-path> ./mvnw test
```

17 个测试覆盖所有 Agent、服务、编排器和 A/B 实验。

---

## API 文档

### POST /api/v1/recommend — 推荐主接口

**Request：**

```json
{
  "userId": "U001",
  "scene": "homepage",
  "numItems": 5,
  "context": {
    "recentView": "P003"
  }
}
```

**Response：**

```json
{
  "requestId": "a1b2c3d4-...",
  "userId": "U001",
  "experimentGroup": "treatment_llm",
  "products": [
    {
      "productId": "P003",
      "name": "AirPods Pro 3",
      "category": "耳机",
      "price": 1899.00,
      "brand": "Apple",
      "score": 0.95,
      "recallSource": "vector",
      "reviewSummary": {
        "productId": "P003",
        "sentiment": "positive",
        "qualityScore": 0.92,
        "keyTags": ["降噪好", "佩戴舒适"],
        "riskFlags": []
      },
      "inventoryStatus": {
        "productId": "P003",
        "stock": 230,
        "status": "normal",
        "maxPurchase": 5
      }
    }
  ],
  "marketingCopies": [
    {
      "productId": "P003",
      "copy": "根据您的偏好精选 AirPods Pro 3，主动降噪标杆，好评率 92%"
    }
  ],
  "agentResults": {
    "user_profile": { "agentName": "user_profile", "success": true, "latencyMs": 320 },
    "search": { "agentName": "search", "success": true, "latencyMs": 150 },
    "review": { "agentName": "review", "success": true, "latencyMs": 280 },
    "ranking": { "agentName": "ranking", "success": true, "latencyMs": 450 },
    "inventory": { "agentName": "inventory", "success": true, "latencyMs": 30 },
    "marketing": { "agentName": "marketing_copy", "success": true, "latencyMs": 380 }
  },
  "totalLatencyMs": 850,
  "timestamp": "2026-05-21T15:30:00"
}
```

### GET /api/v1/health — Agent 健康检查

```json
{
  "status": "healthy",
  "agents": {
    "user_profile": { "calls": 10, "errorRate": 0.0 },
    "search": { "calls": 10, "errorRate": 0.0 },
    "review": { "calls": 10, "errorRate": 0.0 },
    "ranking": { "calls": 10, "errorRate": 0.0 },
    "inventory": { "calls": 10, "errorRate": 0.0 },
    "marketing": { "calls": 10, "errorRate": 0.0 }
  }
}
```

### GET /api/v1/experiments — A/B 实验配置

```json
{
  "experiments": [
    {
      "id": "rec_strategy",
      "groups": ["control", "treatment_llm"],
      "split": "control:50/treatment_llm:50"
    }
  ]
}
```

---

## 项目结构

```
src/main/java/org/ct/multiagentrecommendationsystem/
├── MultiAgentRecommendationSystemApplication.java  # 启动入口
├── agent/                   # 6 个 Agent + BaseAgent 基类
│   ├── BaseAgent.java       # 超时/重试/降级模板
│   ├── UserProfileAgent.java
│   ├── SearchAgent.java
│   ├── ReviewAgent.java
│   ├── RankingAgent.java
│   ├── InventoryAgent.java
│   └── MarketingCopyAgent.java
├── orchestrator/
│   └── SupervisorOrchestrator.java  # 3-Phase 编排引擎
├── model/                   # 14 个 DTO
│   ├── AgentResult.java, Product.java, UserProfile.java
│   ├── ReviewSummary.java, InventoryStatus.java
│   ├── MarketingCopy.java, Review.java, CoPurchase.java
│   ├── UserBehavior.java, RfmScore.java
│   ├── RecommendationRequest.java, RecommendationResponse.java
│   ├── HealthStatus.java, ExperimentConfig.java
├── repository/              # 6 个接口 + 6 个 JSON 实现
│   ├── ProductRepository.java, UserProfileRepository.java
│   ├── BehaviorRepository.java, ReviewRepository.java
│   ├── InventoryRepository.java, CoPurchaseRepository.java
│   └── impl/Json*.java
├── service/                 # 业务服务
│   ├── LlmService.java      # LLM 接口
│   ├── MockLlmService.java  # Mock 实现 (默认)
│   ├── OpenAiLlmService.java# OpenAI 兼容实现
│   ├── RfmCalculator.java   # RFM 算法
│   ├── ABTestService.java   # A/B 分桶
│   ├── ComplianceService.java # 广告法合规
│   └── VectorSearchService.java # 向量检索
├── controller/
│   └── RecommendationController.java  # REST API
├── config/
│   ├── AgentThreadPoolConfig.java
│   ├── RepositoryConfig.java
│   └── DataInitializer.java # 启动加载 + 向量库构建
└── exception/
    ├── AgentExecutionException.java
    └── GlobalExceptionHandler.java

src/main/resources/
├── application.yaml          # 主配置（脱敏，可安全提交）
├── application-local.yaml    # 本地密钥（已 .gitignore）
└── data/                     # 7 个 JSON 数据文件
    ├── products.json         # 500+ 商品
    ├── users.json            # 50 用户
    ├── behaviors.json        # 行为序列
    ├── reviews.json          # 商品评价
    ├── co_purchase.json      # 协同购买
    ├── inventory.json        # 库存快照
    └── copy_templates.json   # 文案模板
```

---

## 配置说明

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `spring.profiles.active` | `mock` | `mock` 零依赖 / `openai` 真实 LLM |
| `server.port` | `8081` | 服务端口 |
| `agent.thread-pool.core-size` | `6` | Agent 线程池大小 |
| `agent.timeout.default-seconds` | `10` | Agent 默认超时 |
| `agent.retry.max-retries` | `3` | 最大重试次数 |
| `ab-test.control-weight` | `50` | A/B 对照组权重 |
| `ab-test.treatment-llm-weight` | `50` | A/B 实验组权重 |
| `vector.top-k` | `20` | 向量检索 Top-K |

---

## License

本项目基于 MIT License 开源。

```
MIT License

Copyright (c) 2026

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
