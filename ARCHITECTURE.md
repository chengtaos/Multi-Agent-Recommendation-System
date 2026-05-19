# 多Agent电商推荐与营销系统 — 架构设计文档

> 从零构建简历级 Agent 项目的完整架构方案。不涉及部署和生产环境，纯本地可运行。

---

## 一、项目定位

一个 **以展示 Multi-Agent 架构能力为核心** 的电商推荐系统。不做 CRUD，不做后台管理，只聚焦 Agent 协作这一件事。

简历里可以写：*"设计并实现基于 Supervisor 模式的 6-Agent 协同推荐系统，含向量检索、多路召回、情感分析、A/B 测试，纯 Java 实现。"*

---

## 二、总体架构

```
                    POST /api/v1/recommend
                            │
                            ▼
            ┌───────────────────────────────┐
            │     RecommendationController   │
            │           请求校验 + 路由        │
            └───────────────┬───────────────┘
                            │
                            ▼
            ┌───────────────────────────────┐
            │     SupervisorOrchestrator     │
            │      3-Phase 并行编排引擎       │
            │   CompletableFuture + 线程池   │
            │       A/B 实验分桶入口          │
            └───────────────┬───────────────┘
                            │
       ╔════════════════════╪════════════════════╗
       ║         PHASE 1 — 并行执行 (3 Agent)     ║
       ╠════════════════════╪════════════════════╣
       ║                    │                    ║
       ║  ┌─────────────────┼─────────────────┐  ║
       ║  │                 │                 │  ║
       ║  ▼                 ▼                 ▼  ║
       ║ UserProfile   SearchAgent     ReviewAgent
       ║ Agent         (多路检索)       (评价舆情)
       ║ (画像分析)        │                 │  ║
       ║  │                 │                 │  ║
       ║  └─────────────────┼─────────────────┘  ║
       ║                    │                    ║
       ╚════════════════════╪════════════════════╝
                            │
       ╔════════════════════╪════════════════════╗
       ║         PHASE 2 — 并行执行 (2 Agent)     ║
       ╠════════════════════╪════════════════════╣
       ║                    │                    ║
       ║    ┌───────────────┴───────────────┐    ║
       ║    │                               │    ║
       ║    ▼                               ▼    ║
       ║ RankingAgent              InventoryAgent
       ║ (LLM 精排)                  (库存决策)
       ║    │                               │    ║
       ║    └───────────────┬───────────────┘    ║
       ║                    │                    ║
       ╚════════════════════╪════════════════════╝
                            │
                    ┌───────┴───────┐
                    │   Aggregator  │
                    │ 库存过滤 → TopN │
                    └───────┬───────┘
                            │
       ╔════════════════════╪════════════════════╗
       ║         PHASE 3 — 串行执行               ║
       ╠════════════════════╪════════════════════╣
       ║                    ▼                    ║
       ║           MarketingCopyAgent            ║
       ║     (个性化文案 + 广告法合规过滤)         ║
       ║                    │                    ║
       ╚════════════════════╪════════════════════╝
                            │
                            ▼
              ┌─────────────────────────┐
              │  RecommendationResponse │
              │  商品 / 文案 / 实验分组   │
              │  Agent执行明细 / 延迟    │
              └─────────────────────────┘
```

**为什么是三阶段？**

- Phase 1 的 3 个 Agent 互不依赖（用户画像、商品检索、评价分析各自独立），并行执行
- Phase 2 依赖 Phase 1 的结果（精排需要画像+候选商品；库存需要候选商品列表），但彼此间独立，同样并行
- Phase 3 必须等前两阶段完成（文案需要最终商品列表 + 用户画像），所以串行

**延迟计算**：`max(P1三个Agent) + max(P2两个Agent) + P3`，而非所有 Agent 耗时累加。

---

## 三、6 大 Agent 详细设计

### Agent 1: UserProfileAgent — 用户画像分析

```
┌─────────────────────────────────────────┐
│           UserProfileAgent               │
├─────────────────────────────────────────┤
│  输入: userId, context (实时上下文)       │
│                                         │
│  Step 1: 从 UserRepository 读基础画像    │
│          {age, gender, city, ...}       │
│                                         │
│  Step 2: 从 BehaviorRepository 读行为   │
│          {clicks, purchases, views}     │
│          → RfmCalculator 计算 RFM 得分   │
│            (纯 Java 算法，不调 LLM)      │
│                                         │
│  Step 3: 调用 LLM 做定性推理             │
│          输入: 行为数据 + RFM分数         │
│          输出: segments / preferredCats  │
│                / priceRange / tags       │
│                                         │
│  输出: UserProfile {                    │
│    segments: ["price_sensitive"],       │
│    preferredCategories: ["手机","耳机"],  │
│    priceRange: [500, 3000],             │
│    rfmScore: {R:0.8, F:0.4, M:0.3},    │
│    realTimeTags: {"活跃时段":"晚间"}      │
│  }                                      │
└─────────────────────────────────────────┘
```

**设计要点**：RFM 定量计算（代码） + 用户意图定性推理（LLM），展示"工程 + AI"结合能力。

---

### Agent 2: SearchAgent — 多路商品检索

```
┌──────────────────────────────────────────────┐
│              SearchAgent                      │
├──────────────────────────────────────────────┤
│  输入: numItems, userProfile(可选)             │
│                                              │
│  四路并行召回:                                  │
│                                              │
│  ┌──────────────────────────────────────┐    │
│  │ 路1: 向量语义检索 (Vector Search)      │    │
│  │   userProfile.preferredCategories    │    │
│  │   → 拼接查询文本 → Embedding API      │    │
│  │   → InMemoryVectorStore 余弦相似度    │    │
│  │   → Top-K1                           │    │
│  └──────────────────────────────────────┘    │
│  ┌──────────────────────────────────────┐    │
│  │ 路2: 类目关键词匹配                    │    │
│  │   preferredCategories → 商品类目索引   │    │
│  │   → 命中商品 Top-K2                  │    │
│  └──────────────────────────────────────┘    │
│  ┌──────────────────────────────────────┐    │
│  │ 路3: 协同过滤 (Item-Based CF)         │    │
│  │   用户近期购买商品ID                   │    │
│  │   → co_purchase 关联表查询            │    │
│  │   → "买了A也买了B" Top-K3            │    │
│  └──────────────────────────────────────┘    │
│  ┌──────────────────────────────────────┐    │
│  │ 路4: 热度兜底                          │    │
│  │   全站热销排名 → Top-K4               │    │
│  └──────────────────────────────────────┘    │
│                                              │
│  合并去重 → 候选集 (50-100 个商品)              │
│                                              │
│  输出: List<Product> candidates              │
│        + 每个商品的召回来源标注                 │
└──────────────────────────────────────────────┘
```

**设计要点**：向量检索是加分项（展示 RAG/Embedding 能力），不同场景用不同策略——类目召回适合明确偏好场景，CF 适合有历史购买场景，向量适合语义模糊查询。

---

### Agent 3: ReviewAgent — 评价舆情分析

```
┌──────────────────────────────────────────┐
│              ReviewAgent                  │
├──────────────────────────────────────────┤
│  输入: 候选商品 ID 列表                    │
│                                          │
│  Step 1: ReviewRepository 查询商品评价     │
│          (每件商品 5-20 条模拟评价)         │
│                                          │
│  Step 2: 规则引擎预计算                    │
│          - 平均评分 / 评分分布             │
│          - 关键词提取 (高频词统计)          │
│                                          │
│  Step 3: LLM 汇总分析 (批量处理)           │
│          输入: 商品名 + 评价样本            │
│          输出: 每件商品的                   │
│            - sentiment (positive/neutral  │
│              /negative)                  │
│            - qualityScore (0-1)           │
│            - keyTags (["质量好","物流快"])  │
│            - riskFlags (负面信号)          │
│                                          │
│  输出: Map<productId, ReviewSummary>      │
│        → 供 RankingAgent 排序参考          │
└──────────────────────────────────────────┘
```

**设计要点**：不是简单的情感分类，而是提取可排序的信号。负面评价多的商品在精排阶段会被降权——模拟真实电商"质量控制"逻辑。

---

### Agent 4: RankingAgent — LLM 精排

```
┌──────────────────────────────────────────────┐
│              RankingAgent                     │
├──────────────────────────────────────────────┤
│  输入:                                       │
│    - candidates (SearchAgent 候选集)           │
│    - userProfile (用户画像)                    │
│    - reviewSummaryMap (评价摘要)               │
│                                              │
│  处理:                                       │
│    综合三方面信号:                              │
│    1. 用户画像匹配度 (类目/价格/偏好)            │
│    2. 商品客观属性 (价格/品牌/标签)              │
│    3. 评价质量信号 (评分/口碑/负面信号)           │
│    → LLM 综合排序，输出 Top-N ID 列表           │
│                                              │
│  输出: List<Product> ranked (Top-N)           │
│        + 每个商品的排序得分                     │
└──────────────────────────────────────────────┘
```

**设计要点**：排序不是简单的一个维度，而是三路信号融合——这是推荐系统的核心难点，也是面试中的好谈资。

---

### Agent 5: InventoryAgent — 库存决策

```
┌──────────────────────────────────────────────┐
│              InventoryAgent                   │
├──────────────────────────────────────────────┤
│  输入: 商品列表                               │
│                                              │
│  处理:                                       │
│    InventoryRepository.getStock(productIds) │
│    ├── stock <= 0    → 标记缺货，剔除          │
│    ├── stock <= 50   → 紧急预警 + 限购1件      │
│    ├── stock <= 100  → 库存紧张 + 限购2件      │
│    └── stock > 100   → 正常                   │
│                                              │
│  输出:                                       │
│    {                                         │
│      availableProducts: [ID1, ID2, ...],     │
│      alerts: [{productId, level, msg}],      │
│      purchaseLimits: {productId: maxQty}     │
│    }                                         │
└──────────────────────────────────────────────┘
```

**设计要点**：业务流程中"推荐质量"的最后一道防线——推荐了缺货商品 = 糟糕的用户体验。

---

### Agent 6: MarketingCopyAgent — 营销文案

```
┌──────────────────────────────────────────────┐
│           MarketingCopyAgent                  │
├──────────────────────────────────────────────┤
│  输入: userProfile + 最终商品列表               │
│                                              │
│  Step 1: 模板选择                             │
│    根据 userProfile.segments 匹配模板:         │
│    new_user → 新人欢迎 + 优惠引导              │
│    high_value → 品质尊享 + 品牌调性            │
│    price_sensitive → 性价比 + 促销紧迫感       │
│    active → 商品亮点 + 使用场景               │
│    churn_risk → 专属折扣 + 召回话术            │
│    (模板从 copy_templates.json 加载)          │
│                                              │
│  Step 2: LLM 生成                             │
│    商品信息 + 选定模板 → 每件商品生成30-50字文案 │
│                                              │
│  Step 3: 合规过滤                             │
│    扫描违禁词 → 替换为 ***                     │
│    FORBIDDEN: [最好, 第一, 国家级, 绝对, 100%]  │
│                                              │
│  输出: List<{productId, copy}>               │
└──────────────────────────────────────────────┘
```

---

## 四、BaseAgent 基类设计

所有 Agent 的父类，提供统一的可靠性保障：

```java
public abstract class BaseAgent {
    String name;           // Agent 名称
    Duration timeout;      // 独立超时
    int maxRetries;        // 最大重试次数

    // 子类只需实现这个
    protected abstract AgentResult doExecute(Map<String, Object> input);

    // 模板方法：计时 → 重试 → 降级
    public CompletableFuture<AgentResult> run(Map<String, Object> input) {
        // 1. 记录开始时间
        // 2. 指数退避重试 (500ms → 1s → 2s)
        // 3. 超时 orTimeout → fallback
        // 4. 全部失败 → 降级结果 (success=false, 不抛异常)
    }

    // 可选的 fallback 覆盖
    protected AgentResult fallback(...) {
        return AgentResult.failed(name, error, latency);
    }
}
```

**关键设计**：
- 每个 Agent 的 `run()` 返回 `CompletableFuture`，不阻塞
- 失败不抛异常，返回降级结果——一个 Agent 挂了不影响整体
- 指数退避遵循生产实践（不是固定间隔）
- 每个 Agent 独立超时——避免某个慢 Agent 拖死全局

---

## 五、Orchestrator 编排器设计

```java
public class SupervisorOrchestrator {

    // 独立线程池，不使用 ForkJoinPool.commonPool()
    private final ExecutorService agentPool = Executors.newFixedThreadPool(6);

    public RecommendationResponse recommend(RecommendationRequest req) {

        // 0. A/B 分桶
        String expGroup = abTestService.assign(req.userId());

        // Phase 1: 并行
        var f1 = userProfileAgent.run(input1);
        var f2 = searchAgent.run(input2);
        var f3 = reviewAgent.run(input3);
        CompletableFuture.allOf(f1, f2, f3).join();

        // Phase 2: 并行 (依赖 P1 结果)
        var f4 = rankingAgent.run(profile + candidates + reviews);
        var f5 = inventoryAgent.run(candidates);
        CompletableFuture.allOf(f4, f5).join();

        // 聚合
        List<Product> finalProducts = filterByStock(ranked, inventory);

        // Phase 3: 串行
        var f6 = marketingCopyAgent.run(profile + finalProducts).join();

        // 组装响应
        return buildResponse(finalProducts, copies, expGroup, timings);
    }
}
```

---

## 六、数据层设计

### 数据文件（src/main/resources/data/）

| 文件 | 内容 | 规模 |
|---|---|---|
| `products.json` | 商品完整信息 | 500+ 条 |
| `users.json` | 用户种子画像 | 50 条 |
| `behaviors.json` | 用户行为序列 | 每用户 20-50 条 |
| `reviews.json` | 商品评价 | 每商品 5-20 条 |
| `co_purchase.json` | 协同购买关系 | 200+ 条关联 |
| `inventory.json` | 库存快照 | 与商品一一对应 |
| `copy_templates.json` | 文案模板 | 5 套基础模板 |

### products.json 条目示例

```json
{
  "productId": "P001",
  "name": "iPhone 16 Pro",
  "category": "手机",
  "subCategory": "智能手机",
  "price": 7999.00,
  "brand": "Apple",
  "description": "A18 Pro芯片，48MP三摄，钛金属边框，支持Apple Intelligence",
  "tags": ["旗舰", "5G", "AI手机"],
  "attributes": {
    "color": "黑色钛金属",
    "storage": "256GB"
  },
  "rating": 4.8,
  "reviewCount": 2340,
  "salesRank": 1,
  "releaseDate": "2025-09-20"
}
```

### 仓储接口设计

```java
// 全部定义为接口 — 当前用 JSON 实现，未来可切换数据库
public interface ProductRepository {
    Optional<Product> findById(String productId);
    List<Product> findByCategory(String category);
    List<Product> findByIds(List<String> productIds);
    List<Product> findHotProducts(int limit);
    List<Product> findAll();
}

// 启动时从 JSON 文件加载到内存
public class JsonFileProductRepository implements ProductRepository { ... }
```

**关键设计**：接口抽象是面试中的大加分项。"为什么用接口？——方便将来切换为 MySQL 实现，遵循依赖倒置原则。"

---

## 七、向量检索引擎设计

```
应用启动
   │
   ▼
DataInitializer (实现 CommandLineRunner)
   │
   ├── 1. 加载 products.json → List<Product>
   │
   ├── 2. 遍历商品，为每个 description 生成 embedding
   │      调用 MiniMax Embedding API (或 OpenAI-compatible)
   │      通过 Spring AI EmbeddingClient
   │
   ├── 3. 存入 InMemoryVectorStore
   │      productId → float[] embedding
   │      同时存商品 ID 作为 metadata
   │
   └── 4. 加载其余 JSON → Repository Bean

请求时 (SearchAgent 调用)
   │
   ├── 1. 构造查询文本: "用户偏好{category}，价格{range}"
   │
   ├── 2. EmbeddingClient.embed(queryText) → float[]
   │
   ├── 3. VectorStore.similaritySearch(queryVector, topK)
   │      余弦相似度: cos(a,b) = a·b / (|a|·|b|)
   │
   └── 4. 返回 Top-K 商品 ID + 相似度分数
```

如果没有 Embedding API（比如 MiniMax 不支持 Embedding 或 Key 受限），降级方案：

```java
// 用商品属性构造 TF-IDF 风格的稀疏向量
// 类目 + 品牌 + 标签 → one-hot 编码 → 余弦相似度
// 虽然不如语义 embedding，但演示了向量检索的完整流程
```

---

## 八、包结构

```
com.ecommerce/
├── MultiAgentApplication.java           // @SpringBootApplication 入口
│
├── agent/                               // Agent 层
│   ├── BaseAgent.java                   // 抽象基类 (重试/超时/降级)
│   ├── UserProfileAgent.java
│   ├── SearchAgent.java
│   ├── ReviewAgent.java
│   ├── RankingAgent.java
│   ├── InventoryAgent.java
│   └── MarketingCopyAgent.java
│
├── orchestrator/                        // 编排层
│   └── SupervisorOrchestrator.java
│
├── model/                               // 数据模型
│   ├── AgentResult.java                 // Agent 执行结果
│   ├── Product.java                     // 商品
│   ├── UserProfile.java                 // 用户画像
│   ├── ReviewSummary.java               // 评价摘要
│   ├── InventoryStatus.java             // 库存状态
│   ├── RecommendationRequest.java       // 请求
│   ├── RecommendationResponse.java      // 响应
│   └── ExperimentConfig.java            // 实验配置
│
├── repository/                          // 数据访问 (接口)
│   ├── ProductRepository.java
│   ├── UserProfileRepository.java
│   ├── BehaviorRepository.java
│   ├── ReviewRepository.java
│   ├── InventoryRepository.java
│   └── CoPurchaseRepository.java
│
├── repository/impl/                     // JSON 实现
│   ├── JsonProductRepository.java
│   ├── JsonUserProfileRepository.java
│   ├── JsonBehaviorRepository.java
│   ├── JsonReviewRepository.java
│   ├── JsonInventoryRepository.java
│   └── JsonCoPurchaseRepository.java
│
├── service/                             // 业务服务
│   ├── RfmCalculator.java               // RFM 算法 (纯代码)
│   ├── ABTestService.java               // A/B 分桶
│   ├── ComplianceService.java           // 广告法合规
│   └── VectorSearchService.java         // 向量检索封装
│
├── config/                              // 配置
│   ├── AgentThreadPoolConfig.java       // 线程池
│   ├── RepositoryConfig.java            // Repository Bean 注册
│   ├── DataInitializer.java             // 启动加载 + 向量库构建
│   └── RecommendationController.java    // REST API
│
└── exception/                           // 异常处理
    ├── AgentExecutionException.java
    └── GlobalExceptionHandler.java
```

---

## 九、API 设计

### POST /api/v1/recommend

请求：
```json
{
  "userId": "U001",
  "scene": "homepage",
  "numItems": 5,
  "context": {
    "recentView": "P003",
    "sessionId": "sess_abc"
  }
}
```

响应：
```json
{
  "requestId": "uuid",
  "userId": "U001",
  "experimentGroup": "treatment_llm",
  "products": [
    {
      "productId": "P003",
      "name": "AirPods Pro 3",
      "category": "耳机",
      "price": 1899.00,
      "score": 0.95,
      "recallSource": "cf_vector",
      "reviewSummary": {
        "sentiment": "positive",
        "qualityScore": 0.92,
        "keyTags": ["降噪好", "佩戴舒适"]
      }
    }
  ],
  "marketingCopies": [
    {
      "productId": "P003",
      "copy": "根据您的浏览偏好精选 AirPods Pro 3，主动降噪体验，好评率 92%"
    }
  ],
  "agentResults": {
    "user_profile": { "agentName": "user_profile", "success": true, "latencyMs": 320 },
    "search":       { "agentName": "search", "success": true, "latencyMs": 150 },
    "review":       { "agentName": "review", "success": true, "latencyMs": 280 },
    "ranking":      { "agentName": "ranking", "success": true, "latencyMs": 450 },
    "inventory":    { "agentName": "inventory", "success": true, "latencyMs": 30 },
    "marketing":    { "agentName": "marketing_copy", "success": true, "latencyMs": 380 }
  },
  "totalLatencyMs": 850,
  "timestamp": "2026-05-19T15:30:00"
}
```

### GET /api/v1/health

```json
{
  "status": "healthy",
  "agents": {
    "user_profile": { "calls": 10, "errorRate": 0.0 },
    "search":       { "calls": 10, "errorRate": 0.0 },
    "review":       { "calls": 10, "errorRate": 0.0 },
    "ranking":      { "calls": 10, "errorRate": 0.0 },
    "inventory":    { "calls": 10, "errorRate": 0.0 },
    "marketing":    { "calls": 10, "errorRate": 0.0 }
  }
}
```

### GET /api/v1/experiments

```json
{
  "experiments": [
    {
      "id": "rec_strategy",
      "groups": ["control", "treatment_llm"],
      "split": "50/50"
    }
  ]
}
```

---

## 十、技术栈

| 类别 | 选型 | 说明 |
|---|---|---|
| 语言 | Java 17 | LTS 版本 |
| 框架 | Spring Boot 3.4 | 当前稳定版 |
| AI | Spring AI 1.0.0-M3 | OpenAI-compatible 接口 |
| LLM | MiniMax M1 | 兼容 OpenAI API |
| Embedding | MiniMax Embedding API | 通过 Spring AI EmbeddingClient |
| 向量库 | SimpleVectorStore | Spring AI 内置，内存存储 |
| 并发 | CompletableFuture + 自定义线程池 | 不用 commonPool |
| 序列化 | Jackson | Spring Boot 默认 |
| 数据 | JSON 文件 + 内存加载 | 无需数据库 |
| 构建 | Maven | |

---

## 十一、开发顺序

按这个顺序建项目，每一步产出可运行的结果：

```
Phase 1: 项目骨架
├── Maven 项目初始化 (pom.xml + 依赖)
├── application.yml (LLM 配置 + 端口)
└── MultiAgentApplication.java (能启动)

Phase 2: 数据层
├── model/ 全部 DTO
├── 编写 7 个 JSON 数据文件
├── repository/ 接口定义
├── repository/impl/ JSON 实现
├── RepositoryConfig (注册为 Spring Bean)
└── 验证: 启动后能读到数据

Phase 3: 基础组件
├── BaseAgent.java
├── RfmCalculator.java + 测试
├── ComplianceService.java + 测试
├── ABTestService.java + 测试
├── VectorSearchService.java
├── DataInitializer.java (加载JSON + 构建向量库)
└── AgentThreadPoolConfig.java

Phase 4: 6 个 Agent 逐个实现
├── UserProfileAgent + 测试
├── SearchAgent + 测试
├── ReviewAgent + 测试
├── RankingAgent + 测试
├── InventoryAgent + 测试
└── MarketingCopyAgent + 测试

Phase 5: 编排 + API
├── SupervisorOrchestrator + 测试
├── RecommendationController
├── GlobalExceptionHandler
└── 端到端验证: curl 发请求看完整链路

Phase 6: 打磨
├── Agent 健康指标暴露 (/health)
├── 日志完善 (每个 Agent 耗时/结果)
└── README 文档
```

---

## 十二、面试可谈的 10 个技术点

| # | 技术点 | 面试可以怎么说 |
|---|---|---|
| 1 | Supervisor 模式 | "集中式编排，3 阶段并行，延迟 = max(每阶段) 而非累加" |
| 2 | 向量检索 | "Spring AI Embedding + 余弦相似度，商品语义搜索" |
| 3 | 多路召回 | "向量+关键词+CF+热度四路，不同场景互补" |
| 4 | 系统稳定性 | "每个 Agent 独立超时 + 指数退避重试 + 降级，一个挂了不影响全局" |
| 5 | 接口抽象 | "Repository 接口 + JSON 实现，遵循依赖倒置，方便切换 DB" |
| 6 | RAG | "SearchAgent 检索增强 + RankingAgent 利用评价/向量信号排序" |
| 7 | A/B 测试 | "MD5 哈希分桶，保证同一用户一致性，后续可接 Thompson Sampling" |
| 8 | RFM 模型 | "算法代码实现 + LLM 定性推理，定量不靠 AI" |
| 9 | 广告法合规 | "敏感词过滤，模板引擎，真实业务场景" |
| 10 | 并发设计 | "CompletableFuture + 独立线程池，不阻塞主线程" |
