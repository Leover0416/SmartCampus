# BM25 稀疏检索与 jieba 中文分词 — 深度技术说明

> **所属项目**：SmartCampus 校园智能问答系统  
> **文档定位**：技术路线说明书附录 — BM25 关键词检索子系统详细设计

---

## 一、为什么 RAG 系统需要 BM25

在讨论 BM25 的原理之前，先理解它在本项目 RAG Pipeline 中存在的必要性。

向量检索（稠密检索）的核心能力是语义匹配——用户说"挂科了怎么办"，向量检索能匹配到"课程成绩不及格的处理办法"，因为两者在语义空间中距离很近。但向量检索有一个系统性盲区：它对精确术语和专有名词不敏感。

以下是真实的校园场景案例：

| 用户查询 | 向量检索表现 | BM25 检索表现 |
|---------|-----------|-------------|
| "学生手册第 42 条规定了什么" | ❌ "第 42 条"在语义空间中没有特殊含义，向量检索可能返回学生手册的任意章节 | ✅ 精确匹配包含"第42条"这个 token 的 chunk |
| "关于缓考的 2024 年修订版" | ❌ 向量检索理解"缓考"但对"2024年修订版"这个限定条件不敏感 | ✅ 同时匹配"缓考"和"2024"两个关键词 |
| "教务处电话是多少" | ❌ 向量检索可能返回各种包含"教务处"的政策文件 | ✅ 精确匹配包含"教务处"和"电话"的联系方式 chunk |
| "GPA 3.5 能转专业吗" | ❌ 数字"3.5"在向量空间中几乎没有区分度 | ✅ 匹配包含"3.5"这个精确数值的条款 |

结论是：向量检索和 BM25 的能力互补而非替代。向量检索负责"理解你想问什么"，BM25 负责"精确找到你说的那个东西"。本项目通过 RRF 融合两路结果，实现语义召回和精确召回的优势互补。

---

## 二、BM25 算法完整推导

### 2.1 从 TF-IDF 到 BM25 的演进

BM25（Best Matching 25）不是凭空设计的，而是从经典的 TF-IDF 逐步改进而来。理解这个演进过程有助于理解 BM25 每一项的设计动机。

**第一代：布尔检索**

最原始的文本检索只回答"包含还是不包含"，没有相关性排序。用户搜索"转专业 条件"，所有同时包含这两个词的文档都返回，没有先后之分。问题显而易见：一篇只提到一次"转专业"的通知和一篇详细阐述转专业条件的政策文件被同等对待。

**第二代：TF-IDF**

引入两个直觉：一个词在文档中出现的次数越多，这篇文档越可能与这个词相关（TF，词频）；一个词在越多文档中出现，它的区分能力越弱（IDF，逆文档频率）。"的""是""在"这些词出现在几乎所有文档中，IDF 接近 0；"缓考""转专业"只出现在特定文档中，IDF 很高。

```
TF-IDF(q, d) = Σ TF(qi, d) × IDF(qi)
```

TF-IDF 解决了排序问题，但存在两个缺陷：TF 是线性增长的，一个词出现 10 次的文档得分是出现 1 次的 10 倍，但实际上出现 10 次并不比出现 5 次更相关多少——词频的边际收益应该递减；长文档天然包含更多词，TF 天然偏高，但长文档未必更相关。

**第三代：BM25**

BM25 正是为了修复 TF-IDF 的这两个缺陷而设计的。

### 2.2 BM25 公式逐项拆解

完整公式如下：

```
BM25(Q, d) = Σ(i=1 to n) IDF(qi) × [ f(qi, d) × (k1 + 1) ] / [ f(qi, d) + k1 × (1 - b + b × |d| / avgdl) ]
```

这个公式由三个独立部分相乘后求和，我们逐项拆解。

#### 第一项：IDF（逆文档频率）—— 这个词有多"稀有"

```
IDF(qi) = log( (N - n(qi) + 0.5) / (n(qi) + 0.5) + 1 )
```

其中 N 是文档总数，n(qi) 是包含词 qi 的文档数。

直觉解释：如果知识库有 1000 个 chunk，其中 800 个包含"学校"这个词，那"学校"的 IDF 很低，因为它几乎出现在所有文档中，没有区分能力。但如果只有 3 个 chunk 包含"缓考"，那"缓考"的 IDF 非常高，它是一个强信号词——用户查了"缓考"，包含这个词的 chunk 大概率就是目标。

公式末尾的 `+1` 是对数平滑，确保当一个词出现在超过一半的文档中时 IDF 不会变成负数。`+0.5` 是拉普拉斯平滑，避免分母为零。

**校园场景的 IDF 分布示例**：

| 词语 | 出现在多少 chunk 中（假设共 5000 个） | IDF 值 | 含义 |
|------|-----------------------------------|--------|------|
| 学校 | 3200 | 0.25 | 极弱信号，几乎所有文档都提到学校 |
| 学生 | 2800 | 0.35 | 弱信号 |
| 学院 | 1500 | 0.89 | 中等信号 |
| 转专业 | 45 | 4.71 | 强信号，高度集中在转专业政策文件中 |
| 缓考 | 12 | 5.83 | 极强信号 |
| 学籍预警 | 8 | 6.13 | 极强信号 |

这意味着当用户查询"转专业学籍预警"时，BM25 会给包含"学籍预警"（IDF=6.13）的 chunk 极高的权重，远超包含"学校"（IDF=0.25）的 chunk。这正是我们想要的行为。

#### 第二项：饱和词频 —— 词出现多少次"才算够"

```
TF_saturated = f(qi, d) × (k1 + 1) / (f(qi, d) + k1 × norm)
```

其中 f(qi, d) 是词 qi 在文档 d 中的出现次数，k1 是词频饱和参数。

这是 BM25 相对于 TF-IDF 最关键的改进。我们来看 k1 如何控制饱和曲线：

```
当 f = 0 时：TF_saturated = 0（不包含该词，得分为 0）
当 f = 1 时：TF_saturated ≈ 1 × 2.2 / (1 + 1.2) ≈ 1.0
当 f = 5 时：TF_saturated ≈ 5 × 2.2 / (5 + 1.2) ≈ 1.77
当 f = 10 时：TF_saturated ≈ 10 × 2.2 / (10 + 1.2) ≈ 1.96
当 f = 100 时：TF_saturated ≈ 100 × 2.2 / (100 + 1.2) ≈ 2.17
当 f → ∞ 时：TF_saturated → k1 + 1 = 2.2（上限）
```

可以看到：从 0 次到 1 次，得分跳了 1.0（最大的增益）；从 1 次到 5 次，只增加了 0.77；从 5 次到 100 次，只增加了 0.4。这就是"饱和"的含义——一个词出现越多次，每多出现一次带来的边际收益越小，最终趋近于 k1+1 的上限。

**k1 的调参含义**：k1 越大，饱和越慢，词频的影响力越大。k1=0 时退化为二元模型（只看包含/不包含），k1→∞ 时退化为原始 TF（线性增长）。k1=1.2 是经过大量实验验证的经典默认值，在信息检索学术界已稳定使用超过 20 年。

对于校园知识库，k1=1.2 的默认值合适，因为我们的 chunk 通常只有 300-500 字，同一个词在一个 chunk 中不太可能出现超过 5 次，饱和效应不需要特别激进。

#### 第三项：文档长度归一化 —— 长文档不该天然占优

```
norm = 1 - b + b × |d| / avgdl
```

其中 |d| 是文档 d 的长度（token 数），avgdl 是所有文档的平均长度，b 是长度归一化参数。

直觉解释：假设知识库中 chunk 的平均长度是 400 个 token。一个 200 token 的短 chunk 包含"转专业"3 次，和一个 800 token 的长 chunk 包含"转专业"3 次，哪个更相关？短的那个更可能是专门讲转专业的段落，因为它用更少的篇幅提到了同样多的次数。

当 |d| = avgdl（文档长度等于平均长度）时，norm = 1，不做任何调整。当 |d| > avgdl（文档比平均长）时，norm > 1，分母增大，得分降低——惩罚长文档。当 |d| < avgdl（文档比平均短）时，norm < 1，分母减小，得分增高——奖励短文档。

**b 的调参含义**：b 控制长度归一化的强度。b=0 时完全不考虑文档长度；b=1 时完全按长度比例归一化。b=0.75 是默认值。

对于校园知识库，由于我们用了智能分块策略（按标题层级 + 滑动窗口），chunk 长度比较均匀（300-500 字），长度归一化的影响相对有限。但对于少数特殊情况（比如一整张大表格被当作一个 chunk，长度可能上千字），b=0.75 能有效防止它因为包含大量词汇而不当获得高分。

### 2.3 完整计算示例

假设用户查询"转专业绩点要求"，经过 jieba 分词后得到三个检索词：["转专业", "绩点", "要求"]。

知识库有 5000 个 chunk，平均长度 avgdl = 400 个 token。考察以下两个候选 chunk：

**Chunk A**（320 个 token，来自《计算机学院转专业管理办法》）：

```
第三条 转专业基本条件
（一）申请转专业的学生应为我校全日制本科在籍学生；
（二）学习成绩优良，平均学分绩点达到本专业前 30%；
（三）无不及格课程记录；
（四）符合转入学院规定的其他条件。
```

词频统计：转专业=2, 绩点=1, 要求=0（文档中用的是"条件"而非"要求"）

**Chunk B**（580 个 token，来自《学生手册 2024 版》）：

```
...学校鼓励学生根据自身发展需要合理规划学业。关于转专业的具体要求，
各学院应按照学校统一要求制定实施细则。学生的学业绩点是重要参考指标之一...
此外，学校要求各学院在评估学生转专业申请时，应综合考虑...要求学生提供...
```

词频统计：转专业=2, 绩点=1, 要求=4

**逐词计算**：

| 词 | n(qi) | IDF | Chunk A: f | Chunk A: 得分 | Chunk B: f | Chunk B: 得分 |
|---|-------|-----|-----------|-------------|-----------|-------------|
| 转专业 | 45 | 4.71 | 2 | 4.71 × 2×2.2/(2+1.2×(1-0.75+0.75×320/400)) = 4.71 × 4.4/3.16 = **6.56** | 2 | 4.71 × 4.4/(2+1.2×(1-0.75+0.75×580/400)) = 4.71 × 4.4/3.49 = **5.94** |
| 绩点 | 60 | 4.42 | 1 | 4.42 × 2.2/2.16 = **4.50** | 1 | 4.42 × 2.2/2.49 = **3.90** |
| 要求 | 1200 | 1.08 | 0 | **0** | 4 | 1.08 × 4×2.2/(4+1.2×1.34) = 1.08 × 8.8/5.61 = **1.69** |

**最终得分**：

```
Chunk A = 6.56 + 4.50 + 0 = 11.06
Chunk B = 5.94 + 3.90 + 1.69 = 11.53
```

Chunk B 略高，因为它多匹配了"要求"这个词。但在实际系统中，Chunk A 很可能在向量检索路线中排名更高（因为它的语义更聚焦于转专业条件），经过 RRF 融合和 Rerank 后，两个 chunk 都会进入最终的 Top-5。这正是多路召回的价值——两种检索方式各自发现了不同的相关文档。

注意"要求"的 IDF 只有 1.08（因为有 1200 个 chunk 都包含"要求"这个常见词），所以即使 Chunk B 包含 4 次"要求"，贡献的分数也只有 1.69，远低于"转专业"一个词的贡献。BM25 通过 IDF 自动识别出"转专业"和"绩点"才是这个查询的核心信号词。

---

## 三、jieba 中文分词

### 3.1 为什么中文检索必须有分词

英文天然以空格分隔单词，"transfer major"直接拆成 ["transfer", "major"] 两个 token。但中文没有天然的词边界，"计算机学院转专业管理办法"是一个连续的字符串，搜索引擎无法直接知道应该切分为 ["计算机学院", "转专业", "管理办法"] 还是 ["计算", "机学", "院转", "专业", "管理", "办法"]。错误的分词会直接导致检索失败——如果把"转专业"切成了"转"和"专业"两个独立的词，用户搜"转专业"时就无法精确匹配到这个完整概念。

### 3.2 jieba 的三种分词模式

jieba 提供三种分词模式，适用于不同场景：

**精确模式（DEFAULT）**：试图将句子最精确地切分，不存在冗余词语。适合文本分析。

```
输入："计算机学院本科生转专业管理办法"
输出：["计算机学院", "本科生", "转专业", "管理办法"]
```

**全模式（FULL）**：将句子中所有可以成词的词语都扫描出来，速度快但存在冗余。

```
输入："计算机学院本科生转专业管理办法"
输出：["计算机", "计算机学院", "学院", "本科", "本科生", "转专业", "专业", "管理", "管理办法", "办法"]
```

**搜索引擎模式（SEARCH）**：在精确模式的基础上，对长词再做一次细粒度切分。这是本项目采用的模式。

```
输入："计算机学院本科生转专业管理办法"
输出：["计算机", "学院", "计算机学院", "本科", "本科生", "转专业", "管理", "办法", "管理办法"]
```

**本项目选择搜索引擎模式的原因**：搜索引擎模式同时保留了长词（"计算机学院""管理办法"）和短词（"计算机""学院""管理""办法"）。这意味着无论用户搜索"计算机学院"（完整长词）还是搜索"学院"（短词），都能命中这个 chunk。长词匹配的 IDF 更高（更精确），短词匹配的覆盖面更广（更高召回），两者兼得。

### 3.3 jieba 的算法原理

jieba 分词的核心是三层机制的组合：

**第一层：基于前缀词典的有向无环图（DAG）**

jieba 内置一个约 35 万条词语的词典（dict.txt），每条记录包含词语、词频和词性。分词时，jieba 先对输入句子构建一个 DAG（有向无环图），图中的每个节点是一个字的位置，每条边代表一个词典中存在的词语。

以"转专业管理办法"为例，DAG 中的边包括：

```
位置 0→2: "转专业"（词典中存在）
位置 0→0: "转"（单字词）
位置 1→2: "专业"（词典中存在）
位置 3→4: "管理"（词典中存在）
位置 3→5: "管理办法"（词典中存在）
位置 5→5: "办法"（词典中存在）（注意这里是位置4→5）
```

**第二层：动态规划求最大概率路径**

DAG 构建完成后，jieba 用动态规划算法（Viterbi 的简化版）从右向左计算每个位置开始的最大路径概率，选择全局最优的切分方案。词频越高的切分方案概率越大。

```
P("转专业" | "管理办法") vs P("转" | "专业" | "管理" | "办法")
```

如果"转专业"在词典中的词频足够高，DP 会选择将其作为一个整体词语，而不是切成"转"+"专业"。

**第三层：隐马尔可夫模型（HMM）处理未登录词**

当句子中出现词典中不存在的词（未登录词，OOV），jieba 使用 HMM（隐马尔可夫模型）进行新词发现。HMM 将每个字标注为 B（词首）、M（词中）、E（词尾）、S（单字词）四种状态之一，通过 Viterbi 算法求解最优状态序列。

例如，假设"学籍预警"不在词典中：

```
学(B) 籍(E) 预(B) 警(E)  → "学籍" + "预警"
```

HMM 通过字与字之间的转移概率和发射概率，推断出"学籍"和"预警"各自是一个词。

### 3.4 自定义词典：校园领域适配

jieba 的默认词典是通用领域的，对校园专有术语的切分可能不理想。比如：

```
默认分词："学分绩点" → ["学分", "绩点"]  ✅ 正确
默认分词："学籍预警" → ["学籍", "预警"]  ✅ 正确
默认分词："缓考申请" → ["缓", "考", "申请"]  ❌ 应为 ["缓考", "申请"]
默认分词："SmartCampus" → ["Smart", "Campus"]  ❌ 应为整体
```

解决方案是添加校园领域自定义词典：

```
# campus_dict.txt — 校园自定义词典
# 格式：词语 词频 词性

缓考 50 n
补考 50 n
重修 50 n
学籍预警 30 n
学分绩点 30 n
转专业 80 n
平均学分绩点 20 n
课程成绩 40 n
教务处 60 n
学生处 60 n
培养方案 50 n
学生手册 50 n
SmartCampus 10 eng
```

```java
@Configuration
public class JiebaConfig {

    @Bean
    public JiebaSegmenter jiebaSegmenter() {
        // 加载校园自定义词典
        WordDictionary.getInstance().loadUserDict(
            Paths.get("config/campus_dict.txt"));
        return new JiebaSegmenter();
    }
}
```

自定义词典的词频设置有讲究：词频越高，jieba 越倾向于将这些字组合为一个词。"转专业"设为 80 是因为这三个字几乎永远应该作为整体出现；"平均学分绩点"设为 20 是因为有时用户也会分开说"平均"和"学分绩点"。

### 3.5 停用词过滤

分词后还需要过滤停用词（stop words）——那些出现频率极高但对检索没有贡献的词。如果不过滤，倒排索引会被大量无用 token 膨胀，BM25 的计算也会浪费在这些低 IDF 词上。

```
# stopwords.txt — 停用词表（部分示例）
的
了
在
是
我
有
和
就
不
也
一
都
会
为
与
及
等
```

```java
@Service
public class TokenProcessor {

    private final JiebaSegmenter segmenter;
    private final Set<String> stopWords;

    /**
     * 完整的分词处理流程：分词 → 过滤停用词 → 过滤单字 → 去重统计词频
     */
    public Map<String, Integer> tokenize(String text) {
        return segmenter.process(text, JiebaSegmenter.SegMode.SEARCH)
            .stream()
            .map(token -> token.word.trim().toLowerCase())
            .filter(word -> word.length() > 1)        // 过滤单字
            .filter(word -> !stopWords.contains(word)) // 过滤停用词
            .filter(word -> !word.matches("\\d+"))     // 过滤纯数字
            .collect(Collectors.groupingBy(
                Function.identity(),
                Collectors.summingInt(e -> 1)           // 统计词频
            ));
    }
}
```

### 3.6 同义词扩展

分词后还有一个优化空间：同义词扩展。用户说"挂科"，但知识库文档中写的是"不及格"；用户说"GPA"，文档中写的是"学分绩点"。如果不做同义词扩展，BM25 会完全匹配不到。

```
# synonyms.txt — 校园同义词表
挂科,不及格,未通过
GPA,绩点,学分绩点,平均学分绩点
补办,重新办理,遗失补办
退课,退选,取消选课
旷课,缺课,未到课
宿舍,寝室,公寓
```

```java
@Service
public class SynonymExpander {

    private final Map<String, List<String>> synonymMap;

    /**
     * 对分词结果做同义词扩展
     * 输入：["挂科", "怎么办"]
     * 输出：["挂科", "不及格", "未通过", "怎么办"]
     */
    public List<String> expand(List<String> tokens) {
        List<String> expanded = new ArrayList<>();
        for (String token : tokens) {
            expanded.add(token); // 保留原词
            List<String> synonyms = synonymMap.get(token);
            if (synonyms != null) {
                expanded.addAll(synonyms); // 追加同义词
            }
        }
        return expanded;
    }
}
```

扩展后的所有 token 都参与 BM25 检索，这样即使用户的用词和文档不一致，也能通过同义词桥接召回到目标文档。

---

## 四、倒排索引的构建与检索

### 4.1 倒排索引的数据结构

倒排索引（Inverted Index）是 BM25 检索的基础数据结构。它的核心思想是：不是为每个文档记录它包含哪些词，而是为每个词记录它出现在哪些文档中。

```
正排索引（Forward Index）：
  chunk_001 → ["转专业", "条件", "绩点", "学院", "申请"]
  chunk_002 → ["转专业", "流程", "材料", "提交"]
  chunk_003 → ["奖学金", "评定", "绩点", "成绩"]

倒排索引（Inverted Index）：
  "转专业" → [(chunk_001, tf=2), (chunk_002, tf=3)]
  "绩点"   → [(chunk_001, tf=1), (chunk_003, tf=2)]
  "条件"   → [(chunk_001, tf=1)]
  "奖学金" → [(chunk_003, tf=2)]
  "流程"   → [(chunk_002, tf=1)]
  ...
```

检索时，拿到用户的分词结果（如 ["转专业", "绩点"]），直接查倒排索引，取出包含这些词的所有 chunk，然后对每个 chunk 计算 BM25 得分。

### 4.2 MySQL 表结构设计

```sql
-- 文档块元数据与统计表
CREATE TABLE `chunk_stats` (
    `chunk_id` VARCHAR(64) PRIMARY KEY COMMENT 'Milvus 中对应的 chunk ID',
    `doc_id` VARCHAR(36) NOT NULL COMMENT '所属文档 ID',
    `doc_title` VARCHAR(256) NOT NULL,
    `content` TEXT NOT NULL COMMENT 'chunk 原文内容',
    `category` VARCHAR(64) COMMENT '知识分类',
    `total_tokens` INT NOT NULL COMMENT '分词后的有效 token 数（去停用词后）',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_doc` (`doc_id`),
    INDEX `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 倒排索引表
CREATE TABLE `chunk_inverted_index` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `token` VARCHAR(128) NOT NULL COMMENT '分词后的单个词',
    `chunk_id` VARCHAR(64) NOT NULL COMMENT '包含该词的 chunk ID',
    `term_freq` INT NOT NULL COMMENT '该词在此 chunk 中出现的次数',
    INDEX `idx_token` (`token`),
    INDEX `idx_chunk` (`chunk_id`),
    INDEX `idx_token_chunk` (`token`, `chunk_id`) COMMENT '联合索引，加速检索'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 全局统计表（单行，缓存全局统计值，避免每次查询都做聚合）
CREATE TABLE `bm25_global_stats` (
    `id` INT PRIMARY KEY DEFAULT 1,
    `total_chunks` INT NOT NULL COMMENT '总 chunk 数 N',
    `avg_doc_length` DOUBLE NOT NULL COMMENT '平均文档长度 avgdl',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;
```

**为什么要单独维护 `bm25_global_stats` 表**：BM25 公式中的 N（总文档数）和 avgdl（平均文档长度）是全局统计量。如果每次检索都执行 `SELECT COUNT(*) FROM chunk_stats` 和 `SELECT AVG(total_tokens) FROM chunk_stats`，在大数据量下会有性能开销。用一张单行表缓存这两个值，每次文档入库/删除时更新，检索时直接读取。

### 4.3 文档入库时构建倒排索引

```java
@Service
public class BM25IndexBuilder {

    private final TokenProcessor tokenProcessor;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 对一个 chunk 构建倒排索引
     * 在文档入库流程中调用，与 Milvus 向量入库并行执行
     */
    @Transactional
    public void buildIndex(String chunkId, String docId, String docTitle,
                           String content, String category) {
        // 1. 分词 + 统计词频
        Map<String, Integer> tokenFreqs = tokenProcessor.tokenize(content);
        int totalTokens = tokenFreqs.values().stream()
            .mapToInt(Integer::intValue).sum();

        // 2. 写入 chunk_stats
        jdbcTemplate.update("""
            INSERT INTO chunk_stats (chunk_id, doc_id, doc_title, content,
                                     category, total_tokens)
            VALUES (?, ?, ?, ?, ?, ?)
            """, chunkId, docId, docTitle, content, category, totalTokens);

        // 3. 批量写入倒排索引
        String sql = "INSERT INTO chunk_inverted_index (token, chunk_id, term_freq) VALUES (?, ?, ?)";
        List<Object[]> batchArgs = tokenFreqs.entrySet().stream()
            .map(e -> new Object[]{e.getKey(), chunkId, e.getValue()})
            .toList();
        jdbcTemplate.batchUpdate(sql, batchArgs);

        // 4. 更新全局统计
        updateGlobalStats();
    }

    private void updateGlobalStats() {
        jdbcTemplate.update("""
            INSERT INTO bm25_global_stats (id, total_chunks, avg_doc_length)
            SELECT 1, COUNT(*), AVG(total_tokens) FROM chunk_stats
            ON DUPLICATE KEY UPDATE
                total_chunks = VALUES(total_chunks),
                avg_doc_length = VALUES(avg_doc_length)
            """);
    }
}
```

### 4.4 BM25 检索完整实现

```java
@Service
public class BM25Retriever {

    private final TokenProcessor tokenProcessor;
    private final SynonymExpander synonymExpander;
    private final JdbcTemplate jdbcTemplate;

    private static final double K1 = 1.2;
    private static final double B = 0.75;

    public List<BM25Result> retrieve(List<String> keywords, int topK) {
        // 1. 对关键词分词 + 同义词扩展
        List<String> tokens = keywords.stream()
            .flatMap(kw -> tokenProcessor.tokenize(kw).keySet().stream())
            .distinct()
            .toList();
        List<String> expandedTokens = synonymExpander.expand(tokens);

        if (expandedTokens.isEmpty()) return List.of();

        // 2. 读取全局统计
        Map<String, Object> globalStats = jdbcTemplate.queryForMap(
            "SELECT total_chunks, avg_doc_length FROM bm25_global_stats WHERE id = 1");
        int N = ((Number) globalStats.get("total_chunks")).intValue();
        double avgdl = ((Number) globalStats.get("avg_doc_length")).doubleValue();

        // 3. 批量查询每个 token 的文档频率（用于计算 IDF）
        String placeholders = expandedTokens.stream()
            .map(t -> "?").collect(Collectors.joining(","));
        Map<String, Integer> docFreqs = new HashMap<>();
        jdbcTemplate.query(
            "SELECT token, COUNT(DISTINCT chunk_id) as df FROM chunk_inverted_index " +
            "WHERE token IN (" + placeholders + ") GROUP BY token",
            expandedTokens.toArray(),
            rs -> {
                docFreqs.put(rs.getString("token"), rs.getInt("df"));
            });

        // 4. 查询包含目标 token 的所有 chunk 及其词频
        List<Map<String, Object>> hits = jdbcTemplate.queryForList(
            "SELECT ci.chunk_id, ci.token, ci.term_freq, cs.total_tokens " +
            "FROM chunk_inverted_index ci " +
            "JOIN chunk_stats cs ON ci.chunk_id = cs.chunk_id " +
            "WHERE ci.token IN (" + placeholders + ")",
            expandedTokens.toArray());

        // 5. 按 chunk_id 聚合，计算每个 chunk 的 BM25 总分
        Map<String, Double> chunkScores = new HashMap<>();
        Map<String, Integer> chunkDocLengths = new HashMap<>();

        for (Map<String, Object> hit : hits) {
            String chunkId = (String) hit.get("chunk_id");
            String token = (String) hit.get("token");
            int tf = ((Number) hit.get("term_freq")).intValue();
            int docLen = ((Number) hit.get("total_tokens")).intValue();
            chunkDocLengths.put(chunkId, docLen);

            // 计算 IDF
            int df = docFreqs.getOrDefault(token, 1);
            double idf = Math.log((N - df + 0.5) / (df + 0.5) + 1);

            // 计算文档长度归一化
            double norm = 1 - B + B * docLen / avgdl;

            // 计算饱和词频
            double tfSaturated = (tf * (K1 + 1)) / (tf + K1 * norm);

            // 累加到该 chunk 的总分
            chunkScores.merge(chunkId, idf * tfSaturated, Double::sum);
        }

        // 6. 按 BM25 得分降序排序，取 Top-K
        return chunkScores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(topK)
            .map(e -> {
                BM25Result result = new BM25Result();
                result.setChunkId(e.getKey());
                result.setScore(e.getValue());
                return result;
            })
            .toList();
    }
}
```

---

## 五、性能分析与优化

### 5.1 倒排索引查询性能

在万级 chunk（约 10,000 个）的规模下，`chunk_inverted_index` 表大约有 50-80 万行（每个 chunk 平均 50-80 个有效 token）。通过 `idx_token` 索引，单个 token 的查询是 B+ 树索引扫描，时间复杂度 O(log n)，实际耗时 < 1ms。3-5 个 token 的联合查询通过 IN 子句一次完成，总耗时通常在 5-15ms。

### 5.2 与 Elasticsearch 的性能对比

| 维度 | MySQL 自建 BM25 | Elasticsearch |
|------|----------------|---------------|
| 万级文档检索延迟 | 10-30ms | 5-15ms |
| 百万级文档检索延迟 | 200-500ms（性能下降明显） | 20-50ms（分片并行） |
| 内存占用 | MySQL 本身即可，无额外开销 | 最少 1-2GB JVM 堆内存 |
| 运维复杂度 | 零额外组件 | 需独立部署、监控、调优 |
| 分词定制 | jieba 自由控制 | 需安装 ik 分析器插件 |
| 适用规模 | 万级文档（校园知识库的实际规模） | 百万级以上 |

结论：校园知识库的典型规模是几百到几千篇文档，分块后约 5,000-50,000 个 chunk。在这个量级下，MySQL 自建 BM25 的性能完全足够，且省去了 ES 的部署和运维成本。如果未来知识库扩展到百万级（比如全校所有历年文件归档），再迁移到 ES 也不迟——BM25 的算法逻辑不变，只是存储和索引层替换。

### 5.3 索引更新策略

文档入库和删除时需要同步更新倒排索引和全局统计。为了避免频繁更新 `bm25_global_stats` 表造成锁竞争，采用延迟批量更新策略：每次入库操作只更新 `chunk_stats` 和 `chunk_inverted_index`，全局统计通过定时任务每 5 分钟刷新一次。BM25 对 N 和 avgdl 的微小变化不敏感（新增几个 chunk 对总量的影响可以忽略），所以 5 分钟的延迟不会影响检索质量。

---

## 六、BM25 参数调优指南

### 6.1 何时需要调参

默认的 k1=1.2、b=0.75 在绝大多数场景下表现良好，但以下情况可能需要微调：

| 现象 | 原因 | 调整方向 |
|------|------|---------|
| 长文档总是排在前面 | b 太小，长度惩罚不够 | 增大 b（0.75 → 0.85） |
| 短文档总是排在前面，长的详细文档被漏掉 | b 太大，过度惩罚长文档 | 减小 b（0.75 → 0.6） |
| 包含关键词一次的文档和包含多次的得分差不多 | k1 太小，饱和太快 | 增大 k1（1.2 → 1.8） |
| 关键词堆砌的文档排名过高 | k1 太大，饱和太慢 | 减小 k1（1.2 → 0.8） |

### 6.2 校园知识库的推荐配置

基于校园文档的特点（chunk 长度 300-500 字，比较均匀；专业术语密度适中），推荐保持默认配置 k1=1.2、b=0.75。但可以考虑一个微调：由于我们的智能分块策略已经控制了 chunk 长度的均匀性，b 可以适当降低到 0.65，减弱长度归一化的影响，让 BM25 更关注词频本身而非文档长度差异。

```java
// 可通过配置文件调整，支持热更新
bm25:
  k1: 1.2
  b: 0.65
  min-token-length: 2    # 最短 token 长度
  max-results: 15        # 单路最大召回数
```
