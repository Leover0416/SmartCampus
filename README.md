# SmartCampus 校园智能问答系统

基于 **RAG + Agent** 的高校政策/教务智能助手，毕设交付项目。

**技术栈**：Java 17 · Spring Boot 3.3 · Vue 3 · Milvus 2.4 · MySQL 8 · Redis 7 · MinIO · DashScope (通义千问)

---

## 功能模块

| 模块 | 说明 |
|------|------|
| 🤖 智能对话 | 六阶段 RAG Pipeline + SSE 流式输出 + 多轮上下文 |
| 🔧 教务工具 | Function Calling — 校历 / 选课 / 联系方式 / 转人工 |
| 📚 知识库 | PDF/DOCX/TXT 上传 → Parent-Child 分块 → 向量入库 |
| ❓ FAQ 管理 | 高频问答维护，命中短路 <200ms |
| 🎫 老师工作台 | 人工工单状态机 + WebSocket 实时推送 |
| 📊 数据看板 | 会话趋势 · 意图分布 · 响应耗时 · 工具调用统计 |
| ⚙️ 系统设置 | 模型参数 / Prompt 模板 / 工具开关动态配置 |

---

## 快速启动

- **代码阅读**：见 [docs/代码导读.md](docs/代码导读.md)（全项目文件说明 + 核心类搜 `【导读】` 注释）
- **量化评测**：见 [docs/评测脚本使用说明.md](docs/评测脚本使用说明.md)（`mvn test -Dtest=EvalMetricsReportTest`）
- 部署步骤见：[docs/部署教程.md](docs/部署教程.md)（若存在）

### 前置要求

- Docker Desktop（>= 24）
- JDK 17（本地开发）
- Node.js 20（本地开发）
- DashScope API Key（[申请地址](https://dashscope.console.aliyun.com/)）

### 一键 Docker 部署

```bash
# 1. 克隆仓库
git clone <repo-url>
cd code_campus

# 2. 设置环境变量
export DASHSCOPE_API_KEY=your-key-here     # Linux/macOS
# set DASHSCOPE_API_KEY=your-key-here      # Windows CMD
# $env:DASHSCOPE_API_KEY="your-key-here"   # Windows PowerShell

# 3. 启动所有服务（首次构建约 5-8 分钟）
docker compose up -d --build

# 4. 查看启动状态
docker compose ps
```

访问 **http://localhost** 即可使用。

### 本地开发模式

```bash
# 1. 启动中间件（MySQL / Redis / Milvus / MinIO）
docker compose up -d mysql redis etcd minio milvus

# 2. 等待所有中间件 healthy（约 1-2 分钟）
docker compose ps

# 3. 启动后端
cd campus
export DASHSCOPE_API_KEY=your-key-here
mvn spring-boot:run

# 4. 启动前端（新终端）
cd campus-frontend
npm install
npm run dev
```

前端：**http://localhost:5173** → 代理到后端 8080。

---

## 演示账号

| 角色 | 用户名 | 密码 | 权限 |
|------|--------|------|------|
| 学生 | student001 | student123 | 对话 / 查看知识库 |
| 教师 | teacher001 | teacher123 | 工单处理 / FAQ 管理 / 知识库上传 |
| 管理员 | admin001 | admin123 | 全部功能 + 系统设置 |

---

## 典型演示路径

### 1. RAG 知识问答
1. 以 `teacher001` 登录，进入**知识库管理**，上传一份 PDF（如学生手册）
2. 等待状态变为「已就绪」（约 30 秒）
3. 切换到**对话窗口**，提问：`"挂科了怎么办"`
4. 回答中应包含 `[来源: 文档名, 第X页]` 引用标注

### 2. 多轮指代消解
- 问：`"计算机学院转专业政策"` → 再问：`"那绩点要求是多少"` → 应正确消解"那"指转专业绩点

### 3. FAQ 短路命中
- 在 FAQ 管理中添加高置信度问答
- 提问同义句，响应应 <200ms（FAQ 短路，不走完整 RAG）

### 4. 教务工具调用
- 提问：`"本学期什么时候选课"` → 前端展示绿色工具调用卡片（含选课时间表 + 数据来源）

### 5. 转人工工单
- 在对话中说：`"我要找老师处理"` → 触发 HUMAN 意图
- 以 `teacher001` 登录老师工作台，可看到待处理工单，接管后 WebSocket 实时更新

---

## 核心架构

```
用户消息
  ↓
【Stage 1】ContextMerger        — Qwen-Plus 多轮指代消解
  ↓
【Stage 2】QueryRewriter        — 主查询 + 2-3 子查询 + 关键词
  ↓
【Stage 3】MultiRouteRecaller   — Dense(Milvus) + BM25 + FAQ  并行召回
  ↓               ↑ FAQ 精确命中(≥0.92) 短路返回
              RRF(k=60) 合并 Top-20 Child
  ↓
【Stage 4】Reranker             — gte-rerank 交叉编码，过滤 <0.3
  ↓
【Stage 5】ParentChildAssembler — Child 聚合回捞 Parent，token 预算 4000
  ↓
【Stage 6】RagGenerator        — Qwen-Max 流式生成，SSE token 推送
```

---

## 服务端口

| 服务 | 端口 | 说明 |
|------|------|------|
| 前端 (nginx) | 80 | 生产环境入口 |
| 前端 (Vite) | 5173 | 开发模式 |
| 后端 (Spring Boot) | 8080 | REST API + SSE + WebSocket |
| MySQL | 3306 | 关系型数据 |
| Redis | 6379 | 会话缓存 / FAQ 向量缓存 |
| Milvus | 19530 | 向量检索 |
| MinIO | 9000 / 9001 | 对象存储 / 控制台 |

---

## 配置说明

### 环境变量

| 变量 | 说明 | 必填 |
|------|------|------|
| `DASHSCOPE_API_KEY` | 通义千问 API Key | ✅ |

### 动态配置（系统设置页）

运行时可在 **系统设置** 页面调整（无需重启）：
- 各模型选型与参数（Temperature / Max Tokens）
- Prompt 模板
- RAG 召回数 / Rerank 阈值 / FAQ 命中阈值
- 工具开关（单独启停每个教务工具）

---

## 单元测试

```bash
cd campus
mvn test -Dtest="BM25ScoringTest,ParentChildChunkSplitterTest,IntentRouterTest"
```

- `BM25ScoringTest` — BM25 公式正确性（TF 饱和 / 长度惩罚 / IDF 零值）
- `ParentChildChunkSplitterTest` — 分块边界、Parent-Child 关联、多 Section
- `IntentRouterTest` — 关键词快路由、LLM 降级、异常处理

---

## 目录结构

```
code_campus/
├─ campus/                  后端 Spring Boot
│  ├─ src/main/java/        业务代码
│  ├─ src/main/resources/   application.yml / schema.sql / data-init.sql
│  ├─ src/test/             单元测试
│  └─ Dockerfile
├─ campus-frontend/         前端 Vue 3
│  ├─ src/views/            6 大页面
│  ├─ src/api/              API 客户端
│  ├─ nginx.conf            生产环境 nginx 配置
│  └─ Dockerfile
├─ docker-compose.yml       一键启动全栈
└─ README.md
```

---

## 常见问题

**Q: Milvus 启动失败？**  
A: 检查 etcd 和 minio 是否 healthy：`docker compose ps`。Milvus 依赖这两个服务。

**Q: 后端启动时报 Milvus 连接失败？**  
A: Milvus 冷启动约需 60 秒，后端设有 start_period=60s 重试。或手动重启：`docker compose restart backend`。

**Q: 提问无回答 / 乱码？**  
A: 检查 `DASHSCOPE_API_KEY` 是否正确设置，用 `docker compose logs backend` 查看错误。

**Q: 上传文档后一直显示"处理中"？**  
A: 查看后端日志 `docker compose logs -f backend`，确认 Milvus / MinIO 连通性。
