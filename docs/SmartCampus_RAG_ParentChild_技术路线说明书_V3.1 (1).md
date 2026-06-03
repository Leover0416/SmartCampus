# SmartCampus 校园智能问答系统 — 技术路线说明书


---

## 一、技术栈总览

| 层级 | 技术选型 | 版本 | 职责定位 |
|------|----------|------|----------|
| 后端框架 | Spring Boot 3(jdk17) + Spring AI | 3.3+ / 1.0+ | Web 容器、依赖注入、AI 能力编排、Tool Calling |
| 前端框架 | Vue 3 + Element Plus + Pinia | 3.4+ | 管理后台、对话窗口、老师工作台 |
| 大语言模型 | 通义千问（Qwen-Max / Qwen-Plus / Qwen-Turbo） | 最新 | 意图识别、RAG 生成、Query Rewrite、工具调用 |
| Embedding 模型 | text-embedding-v3（通义） | 最新 | 文档向量化、查询向量化，维度 1024 |
| Rerank 模型 | gte-rerank（通义） / bge-reranker-v2-m3 | 最新 | 检索结果精排，交叉编码器二次打分 |
| 向量数据库 | Milvus | 2.4+ | 稠密向量存储与 ANN 检索 |
| 关系数据库 | MySQL | 8.0+ | 业务数据、BM25 倒排索引、会话记录 |
| 缓存 | Redis | 7+ | 会话上下文、槽位记忆、热点 FAQ 缓存 |
| 对象存储 | MinIO | 最新 | 原始文档、提取图片、附件存储 |
| 中文分词 | jieba / HanLP | 最新 | BM25 关键词检索的中文分词基础 |
| 文档解析 | Apache Tika + PDFBox | 最新 | PDF/Word/Excel 文本与图片提取 |
| 实时通信 | WebSocket + SSE | Spring 内置 | 流式输出、老师工作台实时推送 |
| 部署 | Docker Compose | - | 一键部署全部中间件 |

---

## 二、大模型选型与分层调度策略

### 2.1 为什么选通义千问

本项目选用阿里通义千问系列作为基座大模型，原因如下：通义千问通过阿里云 DashScope API 提供服务，国内直连无需代理，延迟可控；API 协议兼容 OpenAI 格式，Spring AI 原生支持 `spring-ai-openai-spring-boot-starter` 直接对接；Qwen 系列在中文理解、指令遵循和长文本处理上表现优异，尤其适合校园政策文件这类高密度中文语料；通义同时提供 Embedding 模型（text-embedding-v3）和 Rerank 模型（gte-rerank），三件套一站式解决，工程集成成本低。

### 2.2 模型分层策略

不同的 Agent 环节对模型的能力需求不同，我们按"质量—速度—成本"三角做分层调度：

| Agent 环节 | 模型选择 | Temperature | 设计理由 |
|-----------|----------|-------------|----------|
| Intent Router（意图识别） | Qwen-Turbo | 0.1 | 意图分类是确定性任务，需要速度快、格式稳定，不需要创造性。Turbo 模型推理速度最快，成本最低，分类准确率已足够 |
| Query Rewrite（查询改写） | Qwen-Plus | 0.3 | 改写需要一定的语言理解能力来处理指代消解和上下文补全，但不需要最强模型。Plus 在理解力和速度间取得平衡 |
| RAG Generator（知识问答生成） | Qwen-Max | 0.2 | 这是面向用户的最终回答，质量要求最高。Max 模型在长上下文理解、多段落信息整合和引用定位上显著优于 Turbo |
| Tool Caller（工具调用） | Qwen-Plus | 0.1 | 工具调用要求 JSON 格式严格遵循，需要较强的指令遵循能力。Temperature 设为 0.1 确保格式稳定 |
| Chitchat（闲聊） | Qwen-Turbo | 0.7 | 闲聊允许更高创造性，同时成本优先。Turbo 足以应对日常问候和非业务话题 |
| Image Description（图片理解） | Qwen-VL-Max | 0.2 | 多模态文档入库时对 PDF 中提取的图片生成文字描述，需要视觉理解能力 |

### 2.3 Spring AI 对接配置

```yaml
spring:
  ai:
    openai:
      api-key: ${DASHSCOPE_API_KEY}
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      chat:
        options:
          model: qwen-max
          temperature: 0.2
          max-tokens: 2048

# 多模型配置通过自定义 Bean 实现
models:
  intent-router:
    model: qwen-turbo
    temperature: 0.1
    max-tokens: 256
  query-rewriter:
    model: qwen-plus
    temperature: 0.3
    max-tokens: 512
  rag-generator:
    model: qwen-max
    temperature: 0.2
    max-tokens: 2048
  tool-caller:
    model: qwen-plus
    temperature: 0.1
    max-tokens: 1024
```

通过 Spring AI 的 `ChatClient.Builder` 为每个环节创建独立的 `ChatClient` 实例，各自绑定不同的模型参数，在 `AgentOrchestrator` 中按意图路由结果调度对应的 Client。

---

## 三、RAG Pipeline 深度设计

### 3.1 Pipeline 全景

本系统的 RAG 并非简单的"检索 + 生成"，而是一条经过深度优化的六阶段管道。每一阶段解决一个明确的工程问题：

```
用户原始问题
      ↓
┌─────────────────────────────────────────────────┐
│ Stage 1: Conversation Context Merge（上下文融合）  │
│ 目的：将多轮对话的碎片化表达还原为完整语义         │
│ 输入：当前问题 + Redis 中最近 N 轮对话历史         │
│ 输出：带上下文的完整问题                           │
│ 示例："那补考呢" → "计算机学院的补考政策是什么"     │
└──────────────────────┬──────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────┐
│ Stage 2: Query Rewrite（查询改写）                 │
│ 目的：将口语化/模糊查询转为检索友好的精确表达       │
│ 技术：LLM 驱动的指代消解 + 查询扩展               │
│ 输出：1 个主查询 + 2-3 个扩展子查询                │
└──────────────────────┬──────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────┐
│ Stage 3: Multi-Route Recall（多路召回）             │
│ 目的：从不同检索维度最大化召回相关文档               │
│ 路线 A：Milvus Child Chunk 稠密向量检索（语义相似度）│
│ 路线 B：MySQL Child Chunk BM25 检索（关键词精确匹配）│
│ 路线 C：FAQ 精确匹配（高频问题短路命中）            │
│ 输出：三路结果合并后的候选 Child Chunk 池            │
└──────────────────────┬──────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────┐
│ Stage 4: Rerank（精排重排序）                      │
│ 目的：对候选池做交叉编码器精排，过滤噪声            │
│ 技术：gte-rerank 模型 / bge-reranker-v2-m3        │
│ 输出：Top-K 高质量 Child Chunk                      │
└──────────────────────┬──────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────┐
│ Stage 5: Context Assembly（上下文组装）             │
│ 目的：将检索片段 + 元数据 + 对话历史组装为 Prompt   │
│ 策略：Child 命中聚合、Parent 回捞、Token 预算控制    │
│ 输出：结构化 Prompt                                │
└──────────────────────┬──────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────┐
│ Stage 6: Generation + Citation（生成与引用标注）    │
│ 目的：基于检索上下文生成回答，并标注来源引用         │
│ 技术：Qwen-Max 生成 + 引用段落回溯                 │
│ 输出：自然语言回答 + 来源文档列表 + 引用章节号       │
└─────────────────────────────────────────────────┘
```

### 3.2 Stage 1 — 上下文融合与指代消解

**解决的核心问题**：多轮对话中用户经常使用"这个""那个""它""也一样吗"等指代词，如果直接拿这些碎片去检索，召回率极低。

**技术实现**：从 Redis 中加载当前会话最近 10 轮对话历史，连同当前问题一起发送给 LLM，要求其输出一个独立的、完整的、可直接用于检索的问题。

```java
@Service
public class ContextMerger {

    private final ChatClient rewriteClient; // Qwen-Plus
    private final SessionManager sessionManager;

    private static final String MERGE_PROMPT = """
        你是一个对话理解助手。请根据对话历史，将用户的最新问题改写为一个独立的、完整的问题。

        改写规则：
        1. 解析所有指代词（"这个""那个""它""上面提到的"），替换为具体实体
        2. 补全省略的主语、宾语和限定条件
        3. 如果用户在追问同一主题的不同方面，保留主题限定
        4. 如果最新问题本身已经完整，原样返回即可

        对话历史：
        {history}

        用户最新问题：{question}

        请直接输出改写后的完整问题，不要解释。
        """;

    public String merge(String sessionId, String currentQuestion) {
        SessionContext ctx = sessionManager.getOrCreate(sessionId);
        List<ChatMessage> history = ctx.getHistory();

        if (history.isEmpty()) {
            return currentQuestion; // 首轮对话无需改写
        }

        String historyText = history.stream()
            .map(m -> m.getRole() + ": " + m.getContent())
            .collect(Collectors.joining("\n"));

        return rewriteClient.prompt()
            .system(MERGE_PROMPT
                .replace("{history}", historyText)
                .replace("{question}", currentQuestion))
            .call()
            .content()
            .trim();
    }
}
```

**效果示例**：

| 对话上下文 | 用户当前输入 | 消解后的完整查询 |
|-----------|-------------|----------------|
| 上文讨论了"计算机学院转专业政策" | "那绩点要求是多少" | "计算机学院转专业的绩点要求是多少" |
| 上文讨论了"学生手册中缓考规定" | "这个也适用于研究生吗" | "学生手册中的缓考规定是否适用于研究生" |
| 上文讨论了"本学期选课时间" | "补选呢" | "本学期补选的时间安排是什么" |
| 上文讨论了"计算机学院奖学金" | "数学学院也一样吗" | "数学学院的奖学金评定标准和计算机学院一样吗" |

### 3.3 Stage 2 — Query Rewrite（查询改写与扩展）

**解决的核心问题**：用户的自然语言表达和知识库中的专业表述之间存在"词汇鸿沟"（vocabulary gap）。比如用户说"挂科了怎么办"，但知识库中的表述是"课程成绩不及格的处理办法"。单一查询的召回率有上限，需要从多个角度扩展检索。

**技术实现**：LLM 对消解后的问题进行改写，输出一个主查询和多个扩展子查询，每个子查询侧重不同的检索角度。

```java
@Service
public class QueryRewriter {

    private final ChatClient rewriteClient;

    private static final String REWRITE_PROMPT = """
        你是校园知识库的查询优化器。请将用户的问题改写为更适合检索的形式。

        输出要求（严格 JSON，不要多余内容）：
        {
          "main_query": "最核心的检索查询",
          "sub_queries": [
            "同义词替换或专业术语表达",
            "从不同角度描述同一需求"
          ],
          "keywords": ["关键实体1", "关键实体2"]
        }

        改写策略：
        1. main_query：保留核心语义，去除口语化表达，使用知识库可能出现的正式用语
        2. sub_queries：生成 2-3 个变体查询，覆盖同义表达和上下位概念
           - 口语 → 书面语（"挂科" → "课程不及格"）
           - 缩写 → 全称（"转专业" → "本科生转专业管理办法"）
           - 补充上位概念（"奖学金" → "奖助学金评定办法"）
        3. keywords：提取用于 BM25 关键词检索的核心实体词

        用户问题：{question}
        """;

    public RewrittenQuery rewrite(String question) {
        String response = rewriteClient.prompt()
            .system(REWRITE_PROMPT.replace("{question}", question))
            .call()
            .content();
        return JsonUtil.parse(response, RewrittenQuery.class);
    }
}
```

```java
@Data
public class RewrittenQuery {
    private String mainQuery;        // 主查询，用于向量检索
    private List<String> subQueries; // 子查询，用于多路向量检索
    private List<String> keywords;   // 关键词，用于 BM25 检索
}
```

**效果示例**：

| 原始问题 | main_query | sub_queries | keywords |
|---------|-----------|-------------|----------|
| 挂科了怎么办 | 课程成绩不及格的处理办法 | ["补考重修申请流程", "学业预警与学籍处理规定"] | ["不及格", "补考", "重修"] |
| GPA怎么算 | 学分绩点计算方法 | ["平均学分绩点GPA计算规则", "课程成绩与绩点对应关系"] | ["绩点", "GPA", "学分"] |

### 3.4 Stage 3 — 多路召回（Multi-Route Recall）

**解决的核心问题**：单一检索方式存在系统性盲区。向量检索擅长语义匹配但对精确术语不敏感（"第 42 条"这种精确引用向量检索几乎无能为力）；BM25 擅长关键词匹配但无法理解同义表达。多路召回通过互补消除盲区。

#### Parent-Child 召回模式：小块检索，大块生成

现有单层 chunk 会把“检索粒度”和“生成上下文粒度”绑定在一起：chunk 过大时召回命中但噪声高，chunk 过小时语义被切碎。Parent-Child 模式将两者拆开：**检索只面向 Child Chunk，生成只回传 Parent Chunk**。

**核心结构**：

| 层级 | 作用 | 推荐大小 | 是否向量化 | 是否进入 Prompt |
|------|------|----------|------------|----------------|
| Parent Chunk | 保留章节级、语义完整上下文 | 600-1200 tokens | 可选，不作为主检索对象 | ✅ 是 |
| Child Chunk | 精细表达局部事实，用于精准召回 | 120-250 tokens | ✅ 是 | ❌ 默认不直接给 LLM |
| parent_id 映射 | 建立 child → parent 的回捞关系 | - | 元数据字段 | 用于追踪来源 |

**检索流程调整**：

```
用户问题
  ↓
Query Rewrite
  ↓
检索 Child Chunk Top-20
  ↓
RRF 融合 + Rerank Child
  ↓
按 parent_id 聚合去重
  ↓
回捞 Parent Chunk Top 3-6
  ↓
Parent 上下文进入 LLM 生成
```

这样做的目标不是“召回更多文本”，而是“先用小块找到关键证据，再用父块提供完整语义”。

#### 路线 A：Milvus 稠密向量检索（语义召回）

将改写后的 main_query 和 sub_queries 分别进行 Embedding，在 Milvus 的 **Child Chunk 向量集合** 中做 ANN（近似最近邻）检索。每条查询独立检索 Top-K，召回结果仍然是 child_id，后续再通过 parent_id 回捞 Parent Chunk。

```java
@Service
public class DenseRetriever {

    private final EmbeddingModel embeddingModel; // text-embedding-v3
    private final MilvusVectorStore vectorStore;

    /**
     * 对主查询和子查询分别做 Child Chunk 向量检索，合并去重
     */
    public List<Document> retrieve(RewrittenQuery rq, String filterExpr, int topK) {
        Map<String, Document> resultMap = new LinkedHashMap<>();

        // 主查询检索 Child Chunk，权重最高，取更多结果
        List<Document> mainResults = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(rq.getMainQuery())
                .topK(topK)
                .similarityThreshold(0.5)
                .filterExpression(filterExpr)
                .build()
        );
        mainResults.forEach(doc -> resultMap.putIfAbsent(doc.getId(), doc));

        // 子查询检索 Child Chunk，每条取较少结果，补充覆盖面
        for (String subQuery : rq.getSubQueries()) {
            List<Document> subResults = vectorStore.similaritySearch(
                SearchRequest.builder()
                    .query(subQuery)
                    .topK(topK / 2)
                    .similarityThreshold(0.45)
                    .filterExpression(filterExpr)
                    .build()
            );
            subResults.forEach(doc -> resultMap.putIfAbsent(doc.getId(), doc));
        }

        return new ArrayList<>(resultMap.values());
    }
}
```

**Milvus 索引配置说明**：Milvus 只存 Child Chunk 的向量，metadata 中冗余 `parent_id`、`doc_id`、`heading_path`、页码和权限字段。索引采用 HNSW 索引类型，`M=16`（每个节点的最大连接数），`efConstruction=256`（建图时的搜索宽度），检索时 `ef=128`。HNSW 在百万级数据量下可实现亚毫秒级检索，且召回率优于 IVF 系列索引。Embedding 维度为 1024（text-embedding-v3 默认），距离度量使用内积（IP），因为通义的 Embedding 模型输出已做 L2 归一化，IP 等价于余弦相似度但计算更快。

#### 路线 B：BM25 稀疏检索（关键词召回）

BM25 是经典的基于词频的检索算法，核心公式为：

```
BM25(q, d) = Σ IDF(qi) · [ f(qi, d) · (k1 + 1) ] / [ f(qi, d) + k1 · (1 - b + b · |d| / avgdl) ]
```

其中 `f(qi, d)` 是词 qi 在文档 d 中的词频，`|d|` 是文档长度，`avgdl` 是平均文档长度，`k1=1.2`（词频饱和参数），`b=0.75`（文档长度归一化参数）。IDF 使用对数平滑公式 `log((N - n + 0.5) / (n + 0.5) + 1)`。

**为什么不用 Elasticsearch**：本项目是毕设级别的单机部署场景，引入 ES 会显著增加运维复杂度和资源消耗。MySQL 8.0 的 FULLTEXT 索引配合 jieba 分词预处理，在万级文档量下完全足够。我们在 MySQL 中维护一张分词后的倒排表，自行实现 BM25 打分。

```java
@Service
public class BM25Retriever {

    private final JdbcTemplate jdbcTemplate;
    private final JiebaSegmenter segmenter;

    private static final double K1 = 1.2;
    private static final double B = 0.75;

    /**
     * 基于 BM25 的关键词检索
     * 1. 对 keywords 做 jieba 分词
     * 2. 在倒排索引表中查找包含这些词的文档
     * 3. 按 BM25 公式计算得分并排序
     */
    public List<ScoredChunk> retrieve(List<String> keywords, String category, int topK) {
        // 对关键词做分词（处理复合词）
        List<String> tokens = keywords.stream()
            .flatMap(kw -> segmenter.process(kw, JiebaSegmenter.SegMode.SEARCH).stream())
            .map(SegToken::word)
            .filter(w -> w.length() > 1) // 过滤单字
            .distinct()
            .toList();

        if (tokens.isEmpty()) return List.of();

        // 查询倒排索引，获取包含目标词的所有 chunk
        String placeholders = tokens.stream()
            .map(t -> "?").collect(Collectors.joining(","));

        String sql = """
            SELECT ci.chunk_id, ci.token, ci.term_freq,
                   cs.content, cs.doc_id, cs.doc_title, cs.total_tokens,
                   (SELECT AVG(total_tokens) FROM chunk_stats) as avg_dl,
                   (SELECT COUNT(DISTINCT chunk_id) FROM chunk_stats) as total_docs,
                   (SELECT COUNT(DISTINCT chunk_id) FROM chunk_inverted_index
                    WHERE token = ci.token) as doc_freq
            FROM chunk_inverted_index ci
            JOIN chunk_stats cs ON ci.chunk_id = cs.chunk_id
            WHERE ci.token IN (%s)
            """.formatted(placeholders);

        if (category != null) {
            sql += " AND cs.category = ?";
        }

        // 执行查询并计算 BM25 得分
        // ... BM25 打分逻辑，按 chunk_id 聚合各 token 的得分
        // 返回 Top-K 结果
    }
}
```

**BM25 倒排索引表设计**：

```sql
-- Child Chunk 统计表（与 child_chunks 可合并，也可单独维护检索统计）
CREATE TABLE `chunk_stats` (
    `chunk_id` VARCHAR(64) PRIMARY KEY COMMENT '等价于 child_id',
    `doc_id` VARCHAR(36) NOT NULL,
    `doc_title` VARCHAR(256),
    `content` TEXT NOT NULL,
    `category` VARCHAR(64),
    `total_tokens` INT NOT NULL COMMENT '分词后的 token 数量',
    INDEX `idx_category` (`category`)
);

-- 倒排索引表
CREATE TABLE `chunk_inverted_index` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `token` VARCHAR(64) NOT NULL COMMENT '分词后的单个词',
    `chunk_id` VARCHAR(64) NOT NULL,
    `term_freq` INT NOT NULL COMMENT '该词在此 chunk 中出现的次数',
    INDEX `idx_token` (`token`),
    INDEX `idx_chunk` (`chunk_id`)
);
```

文档入库时，对每个 **Child Chunk** 做 jieba 分词，统计词频，写入倒排索引表。检索时直接查表计算 BM25 得分，返回 child_id，再通过 parent_id 回捞 Parent Chunk，无需依赖外部搜索引擎。

#### 路线 C：FAQ 精确匹配（短路召回）

对于高频问题（如"本学期什么时候选课""校园卡丢了去哪里补办"），直接走 FAQ 精确匹配，跳过整个 RAG Pipeline，极大降低延迟和成本。

```java
@Service
public class FaqMatcher {

    private final EmbeddingModel embeddingModel;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * FAQ 匹配：先做向量相似度计算，相似度 > 0.92 视为精确命中
     * FAQ 向量在启动时预加载到 Redis，避免每次查库
     */
    public FaqMatchResult match(String query) {
        float[] queryEmbedding = embeddingModel.embed(query);

        // 从 Redis 中获取所有已启用 FAQ 的向量
        // 计算余弦相似度，取最高分
        // 阈值 0.92 以上视为命中，直接返回标准答案
        // 0.85-0.92 作为候选，参与后续 Rerank
        // 0.85 以下不命中
    }
}
```

#### 三路融合：Reciprocal Rank Fusion（RRF）

三路召回的结果需要统一排序。不同路线的得分不在同一尺度上（向量相似度 0-1，BM25 可能是几十分），直接比较分数没有意义。我们使用 RRF（倒数排名融合）算法，只依赖排名而不依赖分数：

```
RRF_score(d) = Σ 1 / (k + rank_i(d))
```

其中 `k=60` 是平滑常数，`rank_i(d)` 是文档 d 在第 i 路召回中的排名。

```java
@Service
public class MultiRouteRecaller {

    private final DenseRetriever denseRetriever;
    private final BM25Retriever bm25Retriever;
    private final FaqMatcher faqMatcher;

    private static final int RRF_K = 60;

    public RecallResult recall(RewrittenQuery rq, UserContext user) {
        // 0. FAQ 短路检查
        FaqMatchResult faqResult = faqMatcher.match(rq.getMainQuery());
        if (faqResult.isExactMatch()) {
            return RecallResult.fromFaq(faqResult); // 直接返回，跳过后续
        }

        // 1. 构建权限过滤表达式
        String filterExpr = buildPermissionFilter(user);

        // 2. 三路并行召回
        CompletableFuture<List<Document>> denseFuture =
            CompletableFuture.supplyAsync(() ->
                denseRetriever.retrieve(rq, filterExpr, 15));

        CompletableFuture<List<ScoredChunk>> bm25Future =
            CompletableFuture.supplyAsync(() ->
                bm25Retriever.retrieve(rq.getKeywords(), null, 15));

        // 3. 等待所有路线完成
        List<Document> denseResults = denseFuture.join();
        List<ScoredChunk> bm25Results = bm25Future.join();

        // 4. RRF 融合
        Map<String, Double> rrfScores = new HashMap<>();

        for (int i = 0; i < denseResults.size(); i++) {
            String id = denseResults.get(i).getId();
            rrfScores.merge(id, 1.0 / (RRF_K + i + 1), Double::sum);
        }
        for (int i = 0; i < bm25Results.size(); i++) {
            String id = bm25Results.get(i).getChunkId();
            rrfScores.merge(id, 1.0 / (RRF_K + i + 1), Double::sum);
        }

        // 如果 FAQ 有候选匹配（0.85-0.92），也参与 RRF
        if (faqResult.hasCandidates()) {
            for (int i = 0; i < faqResult.getCandidates().size(); i++) {
                String id = "faq:" + faqResult.getCandidates().get(i).getId();
                rrfScores.merge(id, 1.0 / (RRF_K + i + 1), Double::sum);
            }
        }

        // 5. 按 RRF 得分排序，取 Top-20 进入 Rerank 阶段
        List<String> topIds = rrfScores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(20)
            .map(Map.Entry::getKey)
            .toList();

        return RecallResult.fromRRF(topIds, denseResults, bm25Results);
    }
}
```

**为什么用 RRF 而不是线性加权**：线性加权（如 `0.6 * 向量分 + 0.4 * BM25 分`）需要对不同路线的分数做归一化，但归一化本身依赖于当前查询的结果分布，不稳定且难以调参。RRF 只依赖排名，对分数尺度完全不敏感，且在学术和工业界被广泛验证为多路召回的默认融合策略。

### 3.5 Stage 4 — Rerank（交叉编码器精排）

**解决的核心问题**：多路召回的 Top-20 Child Chunk 中仍然存在噪声。向量检索基于双塔模型（query 和 document 分别编码再算相似度），这种架构效率高但精度有限，因为 query 和 document 之间没有直接交互。Rerank 使用交叉编码器（Cross-Encoder），将 query 和 document 拼接后一起输入模型，让模型在 attention 层直接建模两者的细粒度交互关系，精度远高于双塔模型。

**模型选择**：通义 gte-rerank 通过 DashScope API 调用；如果需要本地部署，可用 bge-reranker-v2-m3（BAAI 开源，支持中文，ONNX Runtime 推理）。

```java
@Service
public class Reranker {

    private final RestTemplate restTemplate;

    private static final String RERANK_API = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";

    /**
     * 调用 gte-rerank 对候选文档做精排
     * 输入：query + 20 个候选 passage
     * 输出：按相关性重新排序的结果 + 相关性得分
     */
    public List<RankedChunk> rerank(String query, List<CandidateChunk> candidates, int topK) {
        // 构造请求
        Map<String, Object> body = Map.of(
            "model", "gte-rerank",
            "input", Map.of(
                "query", query,
                "documents", candidates.stream()
                    .map(CandidateChunk::getContent)
                    .toList()
            ),
            "parameters", Map.of(
                "top_n", topK,
                "return_documents", false
            )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(dashscopeApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<RerankResponse> response = restTemplate.exchange(
            RERANK_API, HttpMethod.POST,
            new HttpEntity<>(body, headers),
            RerankResponse.class
        );

        // 按 Rerank 得分过滤：相关性分数 < 0.3 的直接丢弃
        return response.getBody().getOutput().getResults().stream()
            .filter(r -> r.getRelevanceScore() > 0.3)
            .limit(topK)
            .map(r -> {
                CandidateChunk original = candidates.get(r.getIndex());
                return new RankedChunk(original, r.getRelevanceScore());
            })
            .toList();
    }
}
```

**Rerank 的位置为什么在 RRF 之后而不是之前**：因为交叉编码器的计算成本远高于双塔模型（每个 query-document 对都要过一次完整的 Transformer），对 20 个 Child 候选做 Rerank 可以接受（约 200-500ms），但对原始知识库的全量文档做 Rerank 是不可能的。所以必须先通过多路召回 + RRF 把 Child 候选缩小到 20 个以内，再用 Rerank 做精排，然后进入 Parent 回捞。

### 3.6 Stage 5 — Context Assembly（Parent 回捞与上下文组装）

精排后得到的不再是直接进入 Prompt 的大段文本，而是 Top-K 个高相关 **Child Chunk**。Context Assembly 的核心任务从“相邻块合并”升级为：**根据 child 命中结果回捞 parent，并控制最终进入 LLM 的 parent 数量和噪声比例**。

**为什么不直接把 Child Chunk 给 LLM**：Child Chunk 的语义粒度更适合检索，但不一定适合生成。比如一个 child 只包含“申请时间为每学期开学后两周内”，如果缺少父块中的“适用对象、申请条件、审批流程”，模型容易断章取义。因此，系统只用 child 做定位，用 parent 做回答依据。

**Parent 回捞规则**：

| 步骤 | 处理逻辑 | 说明 |
|------|----------|------|
| 1. Child Rerank | 对 RRF 后的 Top-20 child 做交叉编码器精排 | 保留局部事实匹配最强的 child |
| 2. parent_id 聚合 | 将 child 按 parent_id 分组 | 同一 parent 下多个 child 命中，说明该父块更可信 |
| 3. Parent 联合打分 | `0.5 * max_child_score + 0.3 * child_hit_count_norm + 0.2 * coverage_score` | 同时考虑最高相关度、命中数量、覆盖范围 |
| 4. Parent 去重 | 同一文档相邻 parent 可合并，但不得超过 token 预算 | 避免重复上下文撑爆 Prompt |
| 5. Prompt 装配 | 最终选择 3-6 个 parent，控制在 3000-4000 tokens | 优先保证证据完整性，而不是塞满上下文 |

**Parent 打分公式**：

```text
parent_score = 0.5 * max_child_score
             + 0.3 * log(1 + hit_child_count)
             + 0.2 * coverage_score
```

其中：

- `max_child_score`：该 parent 下命中 child 的最高 Rerank 分数。
- `hit_child_count`：该 parent 下命中的 child 数量，数量越多表示证据更集中。
- `coverage_score`：命中 child 在 parent 中的覆盖程度，避免只因重复短语堆高分。

```java
@Service
public class ParentChildContextAssembler {

    private static final int MAX_CONTEXT_TOKENS = 4000;
    private static final int MAX_PARENT_COUNT = 6;
    private static final int MIN_PARENT_COUNT = 3;

    private final ParentChunkRepository parentChunkRepository;

    /**
     * 输入：Rerank 后的 Child Chunk
     * 输出：回捞后的 Parent Chunk 上下文
     */
    public AssembledContext assemble(List<RankedChildChunk> rankedChildren,
                                     List<ChatMessage> history) {
        // 1. 按 parent_id 聚合 child 命中结果
        Map<String, List<RankedChildChunk>> grouped = rankedChildren.stream()
            .collect(Collectors.groupingBy(
                RankedChildChunk::getParentId,
                LinkedHashMap::new,
                Collectors.toList()
            ));

        // 2. 计算 parent_score
        List<ParentCandidate> parentCandidates = grouped.entrySet().stream()
            .map(entry -> {
                String parentId = entry.getKey();
                List<RankedChildChunk> children = entry.getValue();

                double maxChildScore = children.stream()
                    .mapToDouble(RankedChildChunk::getScore)
                    .max()
                    .orElse(0.0);

                double hitCountScore = Math.log(1 + children.size());
                double coverageScore = calculateCoverage(children);

                double parentScore = 0.5 * maxChildScore
                    + 0.3 * hitCountScore
                    + 0.2 * coverageScore;

                ParentChunk parent = parentChunkRepository.findById(parentId);
                return new ParentCandidate(parent, children, parentScore);
            })
            .sorted(Comparator.comparingDouble(ParentCandidate::getScore).reversed())
            .toList();

        // 3. Token 预算控制：最多 3-6 个 parent，不盲目塞满上下文
        StringBuilder contextBuilder = new StringBuilder();
        List<SourceReference> sources = new ArrayList<>();
        int usedTokens = 0;
        int selectedParents = 0;

        for (ParentCandidate candidate : parentCandidates) {
            if (selectedParents >= MAX_PARENT_COUNT) break;

            ParentChunk parent = candidate.getParent();
            int parentTokens = estimateTokens(parent.getContent());

            if (usedTokens + parentTokens > MAX_CONTEXT_TOKENS) {
                // 如果还没达到最小 parent 数，可以尝试压缩 parent；否则停止
                if (selectedParents < MIN_PARENT_COUNT) {
                    parent = truncateParentAroundHitChildren(parent, candidate.getHitChildren());
                    parentTokens = estimateTokens(parent.getContent());
                } else {
                    break;
                }
            }

            contextBuilder.append(String.format(
                "[来源: %s | 章节: %s | 页码: %s | parent_score: %.2f | 命中child数: %d]\n%s\n\n",
                parent.getDocTitle(),
                parent.getHeadingPath(),
                parent.getPageRange(),
                candidate.getScore(),
                candidate.getHitChildren().size(),
                parent.getContent()
            ));

            sources.add(new SourceReference(
                parent.getDocId(),
                parent.getDocTitle(),
                parent.getPageRange(),
                "parent-child",
                candidate.getHitChildren().stream()
                    .map(RankedChildChunk::getChildId)
                    .toList()
            ));

            usedTokens += parentTokens;
            selectedParents++;
        }

        String historyText = truncateHistory(history, 1000);
        return new AssembledContext(contextBuilder.toString(), historyText, sources);
    }

    /**
     * 计算 child 在 parent 内的覆盖程度，避免多个 child 都集中在同一句附近。
     */
    private double calculateCoverage(List<RankedChildChunk> children) {
        if (children.isEmpty()) return 0.0;
        int minStart = children.stream().mapToInt(RankedChildChunk::getStartOffset).min().orElse(0);
        int maxEnd = children.stream().mapToInt(RankedChildChunk::getEndOffset).max().orElse(0);
        int span = Math.max(1, maxEnd - minStart);
        return Math.min(1.0, span / 1000.0);
    }
}
```

**反直觉参数控制**：Child Top-K 不是越大越好。建议初始值为 `topK_child=20`，Rerank 后保留 `topN_child=8-12`，最终聚合为 `topN_parent=3-6`。如果将 child top-k 从 20 拉到 50，离线 Recall@k 可能上升，但 parent 回捞后上下文会膨胀，线上回答反而更容易被噪声干扰。

### 3.7 Stage 6 — Generation + Citation（生成与引用标注）

最终阶段将组装好的上下文交给 Qwen-Max 生成回答。Prompt 设计是这一阶段的核心：

```java
@Service
public class RagGenerator {

    private final ChatClient ragClient; // Qwen-Max

    private static final String RAG_SYSTEM_PROMPT = """
        你是 SmartCampus 校园智能问答助手。请严格根据以下知识库内容回答学生的问题。

        ## 回答规则
        1. 仅基于提供的知识库内容回答，不要编造不存在的信息
        2. 如果知识库内容不足以回答问题，明确说明"当前知识库暂无相关信息，建议咨询教务处或相关部门"
        3. 回答末尾用 [来源: 文档名称, 第X页] 格式标注信息来源
        4. 如果多个来源提供了互补信息，综合后回答，分别标注各自来源
        5. 回答使用中文，语气亲切专业，适合面向大学生的服务场景
        6. 涉及具体时间、数字、条款号时务必准确引用原文，不要凭记忆概括

        ## 知识库内容
        {context}
        """;

    public Flux<String> generateStream(String question, AssembledContext ctx) {
        return ragClient.prompt()
            .system(RAG_SYSTEM_PROMPT.replace("{context}", ctx.getContextText()))
            .messages(parseHistory(ctx.getHistoryText()))
            .user(question)
            .stream()
            .content();
    }
}
```

---

## 四、文档入库（Indexing Pipeline）

### 4.1 文档解析与 Parent-Child 分块策略

文档入库是 RAG 质量的基础。原来的单层 chunk 策略会把“检索精度”和“上下文完整性”绑定在一起：chunk 太大，向量召回虽然命中，但噪声高；chunk 太小，局部事实容易命中，但语义上下文被切碎。SmartCampus 采用 **Parent-Child 两层分块**，让检索和生成使用不同粒度。

#### 4.1.1 分块原则

| 层级 | 主要目标 | 切分粒度 | 推荐参数 | 存储位置 | 用途 |
|------|----------|----------|----------|----------|------|
| Parent Chunk | 保留完整语义上下文 | 章节、条款组、语义段落 | 600-1200 tokens | MySQL / MinIO 元数据 | 回捞后进入 LLM |
| Child Chunk | 提高检索精度 | 父块内部的短语义片段 | 120-250 tokens，overlap 20-40 tokens | Milvus + MySQL BM25 | 向量检索、BM25 检索、Rerank |
| parent_id | 建立映射关系 | 每个 child 指向唯一 parent | UUID / 雪花 ID | child metadata | child 命中后回捞 parent |

**核心规则**：

1. **先切 Parent，再切 Child**：Parent 是语义完整单元，Child 只能在 Parent 内部继续切，不能跨 Parent。
2. **Parent 按标题层级优先**：优先使用 H1/H2/H3、`第X章`、`第X条`、政策文件条款编号。
3. **Parent 过长继续按段落切**：如果单个章节超过 1200 tokens，按自然段、条款号、列表项继续拆分。
4. **Parent 过短向后合并**：如果 Parent 少于 300 tokens，优先和同级相邻段落合并，避免上下文过碎。
5. **Child 面向检索，不面向阅读**：Child 允许更短，但必须保留最小事实单元，不能把条件、对象、结论切散。
6. **所有 Child 必须携带 parent_id、doc_id、heading_path、page_range、start_offset、end_offset**，便于回捞、引用和可解释性追踪。

#### 4.1.2 中文技术文档推荐参数

| 参数 | 推荐值 | 说明 |
|------|--------|------|
| Parent 大小 | 600-1200 tokens | 适合校园政策、技术说明、规章制度 |
| Parent overlap | 0-100 tokens | 仅在章节边界存在跨段说明时使用，不默认大重叠 |
| Child 大小 | 120-250 tokens | 适合向量匹配局部事实 |
| Child overlap | 20-40 tokens | 避免关键句落在边界处 |
| Child 初召回 | Top-20 | 初始推荐值，避免召回过多导致 parent 膨胀 |
| Child Rerank 后 | Top-8 到 Top-12 | 只保留高质量命中 |
| Parent 回捞 | Top-3 到 Top-6 | 最终进入 Prompt 的上下文块 |
| Context Token 预算 | 3000-4000 tokens | 防止 Lost in the Middle 和噪声覆盖关键证据 |

#### 4.1.3 入库数据结构

Parent 和 Child 不是两套知识库，而是一套文档的两层索引。Parent 保存完整文本，Child 保存检索片段，并通过 `parent_id` 关联。

```sql
-- Parent Chunk：用于回捞和生成
CREATE TABLE `parent_chunks` (
    `parent_id` VARCHAR(64) PRIMARY KEY,
    `doc_id` VARCHAR(36) NOT NULL,
    `doc_title` VARCHAR(256),
    `heading_path` VARCHAR(512) COMMENT '标题路径，如 学籍管理 > 补考与重修',
    `content` MEDIUMTEXT NOT NULL,
    `page_start` INT,
    `page_end` INT,
    `token_count` INT NOT NULL,
    `category` VARCHAR(64),
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_doc_id` (`doc_id`),
    INDEX `idx_category` (`category`)
);

-- Child Chunk：用于向量检索、BM25、Rerank
CREATE TABLE `child_chunks` (
    `child_id` VARCHAR(64) PRIMARY KEY,
    `parent_id` VARCHAR(64) NOT NULL,
    `doc_id` VARCHAR(36) NOT NULL,
    `doc_title` VARCHAR(256),
    `heading_path` VARCHAR(512),
    `content` TEXT NOT NULL,
    `chunk_index` INT NOT NULL,
    `start_offset` INT,
    `end_offset` INT,
    `token_count` INT NOT NULL,
    `page_start` INT,
    `page_end` INT,
    `category` VARCHAR(64),
    INDEX `idx_parent_id` (`parent_id`),
    INDEX `idx_doc_id` (`doc_id`),
    INDEX `idx_category` (`category`)
);
```

Milvus 中只存 Child Chunk 向量，metadata 中必须冗余以下字段：

```json
{
  "child_id": "child_001",
  "parent_id": "parent_001",
  "doc_id": "doc_001",
  "doc_title": "学生手册",
  "heading_path": "学籍管理 > 补考与重修",
  "page_start": 12,
  "page_end": 13,
  "category": "academic_policy",
  "access_roles": ["student", "teacher"]
}
```

BM25 倒排索引也改为基于 Child Chunk 构建，`chunk_id` 字段语义上等价于 `child_id`。这样 BM25 和向量检索召回的是同一层级，后续 RRF、Rerank、parent 回捞都能统一处理。

#### 4.1.4 Parent-Child Splitter 实现

```java
@Service
public class ParentChildChunkSplitter {

    private static final int PARENT_MIN_TOKENS = 300;
    private static final int PARENT_MAX_TOKENS = 1200;
    private static final int CHILD_MAX_TOKENS = 250;
    private static final int CHILD_MIN_TOKENS = 120;
    private static final int CHILD_OVERLAP_TOKENS = 40;

    /**
     * 两层分块：先生成 parent，再在 parent 内部生成 child。
     */
    public SplitResult split(String content, DocumentMeta meta) {
        // 1. 解析标题层级、页码、条款编号
        List<Section> sections = detectSections(content);

        // 2. 生成 Parent Chunk
        List<ParentChunk> parents = buildParentChunks(sections, meta);

        // 3. 在每个 Parent 内部生成 Child Chunk
        List<ChildChunk> children = new ArrayList<>();
        for (ParentChunk parent : parents) {
            children.addAll(splitChildren(parent));
        }

        return new SplitResult(parents, children);
    }

    private List<ParentChunk> buildParentChunks(List<Section> sections, DocumentMeta meta) {
        List<ParentChunk> parents = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        String currentHeadingPath = "";

        for (Section section : sections) {
            int sectionTokens = estimateTokens(section.getContent());

            // 超长章节：按段落或条款继续拆 parent
            if (sectionTokens > PARENT_MAX_TOKENS) {
                flushParentIfNeeded(parents, buffer, currentHeadingPath, meta);
                parents.addAll(splitLongSectionToParents(section, meta));
                buffer.setLength(0);
                continue;
            }

            // 正常章节：尽量合并到 600-1200 tokens 的 parent
            if (estimateTokens(buffer.toString()) + sectionTokens <= PARENT_MAX_TOKENS) {
                if (buffer.isEmpty()) {
                    currentHeadingPath = section.getHeadingPath();
                }
                buffer.append(section.getContent()).append("\n\n");
            } else {
                flushParentIfNeeded(parents, buffer, currentHeadingPath, meta);
                buffer.setLength(0);
                buffer.append(section.getContent()).append("\n\n");
                currentHeadingPath = section.getHeadingPath();
            }
        }

        flushParentIfNeeded(parents, buffer, currentHeadingPath, meta);
        return mergeTooSmallParents(parents, PARENT_MIN_TOKENS);
    }

    private List<ChildChunk> splitChildren(ParentChunk parent) {
        List<String> sentences = splitBySentence(parent.getContent());
        List<ChildChunk> children = new ArrayList<>();

        StringBuilder window = new StringBuilder();
        int childIndex = 0;
        int startOffset = 0;

        for (String sentence : sentences) {
            int nextTokens = estimateTokens(window + sentence);

            if (nextTokens <= CHILD_MAX_TOKENS) {
                window.append(sentence);
                continue;
            }

            if (estimateTokens(window.toString()) >= CHILD_MIN_TOKENS) {
                children.add(toChildChunk(parent, window.toString(), childIndex++, startOffset));
                // 保留 20-40 tokens 重叠，避免边界丢失
                String overlap = takeLastTokens(window.toString(), CHILD_OVERLAP_TOKENS);
                startOffset += Math.max(0, window.length() - overlap.length());
                window.setLength(0);
                window.append(overlap).append(sentence);
            } else {
                // 如果还没达到最小 child 长度，继续累积，避免过碎
                window.append(sentence);
            }
        }

        if (!window.isEmpty()) {
            children.add(toChildChunk(parent, window.toString(), childIndex, startOffset));
        }

        return children;
    }
}
```

#### 4.1.5 入库流程调整

```text
原始文档
  ↓
Apache Tika / PDFBox 解析文本、标题、页码
  ↓
Parent Split：按标题层级 / 条款 / 语义段切成 600-1200 tokens
  ↓
Child Split：在每个 Parent 内部切成 120-250 tokens，重叠 20-40 tokens
  ↓
Parent 写入 MySQL parent_chunks
  ↓
Child 写入 MySQL child_chunks + BM25 倒排索引
  ↓
Child Embedding 写入 Milvus，metadata 带 parent_id
```

#### 4.1.6 评测指标

Parent-Child 是否有效不能只看召回率，需要分三层评估：

| 层级 | 指标 | 观察重点 |
|------|------|----------|
| 检索层 | Recall@k、MRR | Child 是否更容易命中关键事实 |
| 上下文层 | Context Precision、平均 parent token 数 | Parent 回捞后噪声是否可控 |
| 生成层 | Faithfulness、Answer Correctness | 答案是否更少幻觉、引用是否更稳定 |

上线前建议保留 A/B 配置：`single_chunk` 与 `parent_child` 两套策略并行评测。同一批问题下，如果 Parent-Child 的 Recall@20 提升，但 Context Precision 下降，说明 child top-k 或 parent 回捞数量过大，需要优先收缩 `topK_child` 和 `topN_parent`。

### 4.2 多模态文档处理

对于 PDF 中嵌入的图片（流程图、表格截图、校园地图等），使用 PDFBox 提取后调用 Qwen-VL-Max 生成文字描述，描述文本作为特殊类型的 chunk 入库，元数据中标记 `chunk_type=image` 并保留原图在 MinIO 中的地址。检索到图片 chunk 时，前端可同时展示文字回答和原图。

### 4.3 入库时构建 BM25 倒排索引

每个 chunk 入库时同步做 jieba 分词，将分词结果写入 `chunk_inverted_index` 表。分词使用 SEARCH 模式（会对长词做进一步细粒度切分，如"清华大学"同时产出"清华""大学""清华大学"三个 token），提高召回率。同时维护自定义词典，将校园专有名词（学院名、制度名、系统名）加入 jieba 词典，避免被错误切分。

---

## 五、会话管理与多轮对话

### 5.1 Redis 会话状态模型

```
session:{sessionId} → SessionContext JSON
    ├── sessionId: 会话唯一标识
    ├── userId: 用户 ID
    ├── role: 用户角色（student/teacher/admin）
    ├── college: 用户所属学院
    ├── history: [最近 10 轮对话消息]（滑动窗口）
    ├── slots: {studentNo, college, major, term, ...}（槽位记忆）
    ├── currentIntent: 当前意图
    ├── unresolvedCount: 连续未解决轮次
    └── status: active / pending_human / human / closed
```

**TTL 策略**：活跃会话 TTL 2 小时，每次交互重置；转人工会话 TTL 24 小时；关闭的会话从 Redis 淘汰，持久化到 MySQL `chat_sessions` 表。

### 5.2 槽位记忆

校园对话中有强槽位依赖：一旦用户提到了学院、专业、学期等信息，后续追问通常都在这个范围内。槽位从对话中自动提取并持久化到 Redis，后续检索时作为隐含过滤条件注入。

---

## 六、权限感知的检索过滤

### 6.1 权限模型

每份知识文档在上传时由管理员设置访问策略，权限信息同步冗余到 Milvus chunk 的元数据字段中。检索时，根据当前用户的角色和属性（学院、专业、年级）构建 Milvus filter expression，在向量检索阶段直接过滤无权限的文档，确保用户只能检索到其有权访问的内容。

### 6.2 安全原则

当检索结果为空时，系统统一回复"当前知识库暂无相关信息"，不泄露"该文档存在但你无权访问"的事实，避免信息枚举攻击。

---

## 七、系统性能优化策略

| 优化点 | 技术手段 | 预期效果 |
|--------|---------|---------|
| FAQ 热点短路 | 高频 FAQ 向量预加载到 Redis，相似度 > 0.92 直接返回 | 高频问题响应 < 200ms |
| 多路并行召回 | CompletableFuture 并发执行向量检索和 BM25 检索 | 召回耗时从串行的 400ms 降至并行的 200ms |
| Embedding 缓存 | 对相同查询的 Embedding 结果缓存到 Redis（TTL 1h） | 减少重复 Embedding 计算 |
| 流式输出 | SSE/WebSocket 逐 token 推送 | 用户首字延迟 < 1 秒 |
| 连接池优化 | Milvus 连接池、MySQL 连接池（HikariCP） | 避免高并发下的连接耗尽 |
| Child 向量预计算 | 文档入库时离线完成 Child Chunk Embedding，查询时只需计算 query 向量 | 检索阶段无文档 Embedding 开销 |

---

## 八、技术路线总结图

```
                         用户提问
                            │
                    ┌───────▼────────┐
                    │  JWT 鉴权解析    │ → 角色、学院、专业
                    └───────┬────────┘
                            │
                    ┌───────▼────────┐
                    │  Intent Router  │ → Qwen-Turbo 意图分类
                    └───────┬────────┘
                            │
              ┌─────────────┼──────────────┐
              ▼             ▼              ▼
         POLICY_QA    ACADEMIC_TOOL    CHITCHAT / HUMAN
              │             │              │
    ┌─────────▼──────────┐  │         直接 LLM / 转人工
    │ Context Merge      │  │
    │ (指代消解)          │  │
    └─────────┬──────────┘  │
    ┌─────────▼──────────┐  │
    │ Query Rewrite      │  │ ← Qwen-Plus
    │ (查询改写+扩展)     │  │
    └─────────┬──────────┘  │
    ┌─────────▼──────────┐  │
    │ Multi-Route Recall │  │
    │ ┌────┐ ┌────┐ ┌──┐│  │
    │ │向量│ │BM25│ │FAQ││  │
    │ │Milvus│MySQL│ │Redis│  │
    │ └──┬─┘ └──┬─┘ └─┬┘│  │
    │    └───┬──┘─────┘  │  │
    │   RRF 融合 Top-20  │  │
    └─────────┬──────────┘  │
    ┌─────────▼──────────┐  │
    │ Rerank 精排         │  │ ← gte-rerank
    │ Cross-Encoder Top-5│  │
    └─────────┬──────────┘  │
    ┌─────────▼──────────┐  │
    │ Context Assembly   │  │
    │ (相邻合并+Token控制)│  │
    └─────────┬──────────┘  │
    ┌─────────▼──────────┐  ▼
    │ Generation         │ Tool Executor
    │ Qwen-Max 生成回答   │ (校历/选课/考试)
    │ + 引用来源标注       │  │
    └─────────┬──────────┘  │
              └──────┬──────┘
                     ▼
              SSE 流式返回用户
```
