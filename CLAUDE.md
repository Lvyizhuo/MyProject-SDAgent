# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概览

**AI 政策通·智能体服务平台** — 基于 AI/LLM 的山东省以旧换新政策咨询系统。前后端分离架构，核心围绕 RAG（检索增强生成）+ ReAct Agent 构建政策问答、补贴计算、多模态识别能力。

## 开发命令

### 后端（Spring Boot 3.4 + Java 21）

```bash
cd backend

# 构建（跳过测试以加速）
./mvnw clean package -DskipTests

# 本地开发运行（需先启动基础设施容器：postgres, redis, minio, ollama）
./mvnw spring-boot:run

# 带 MCP profile 运行
./mvnw spring-boot:run -Dspring-boot.run.profiles=mcp

# 运行全部测试
./mvnw test

# 运行单个测试类/方法
./mvnw test -Dtest=ChatServiceTest
./mvnw test -Dtest=ChatServiceTest#testChatResponse
```

### 前端（React 19 + Vite 7）

```bash
cd frontend

npm install             # 安装依赖
npm run dev              # 开发服务器（热重载，代理 /api → localhost:8080）
npm run build            # 生产构建
npm run lint             # ESLint 检查
npm run preview          # 预览生产构建
```

### 基础设施

```bash
# 开发环境启动基础设施（不含后端/前端容器）
cd backend
docker compose up -d postgres redis minio ollama ollama-init

# 查看容器状态
docker compose ps

# 查看日志
docker compose logs -f postgres
```

### 生产部署

```bash
# 服务器一键部署（脚本含健康检查）
cd deploy
cp .env.example .env    # 编辑 .env 填写真实配置
./deploy.sh             # Linux/macOS

# 停止
docker compose down
# 清除全部数据
docker compose down -v
```

### 集成测试

```bash
# 需要后端已运行
bash scripts/chat-module-integration-test.sh
```

## 技术架构

```
用户请求（React 前端）
  └─ /api/chat  POST → Controller
       └─ ChatService
            ├─ ToolIntentClassifier（意图分类 + 参数校验）
            ├─ AgentPlanParser（多步计划解析执行）
            ├─ ReActPlanningService（ReAct 工作流编排）
            ├─ Advisor 链（Spring AI Advisor 管道）
            │   ├─ SecurityAdvisor       (order=10) 敏感词/注入防护
            │   ├─ MessageChatMemoryAdvisor       Redis 多轮记忆
            │   ├─ ReReadingAdvisor      (order=50) 复核答案准确性
            │   ├─ QuestionAnswerAdvisor          RAG 检索增强
            │   └─ LoggingAdvisor        (order=90) 用量/延时/引用日志
            └─ LLM 调用（DynamicChatClientFactory）
                 └─ DashScope / 第三方 OpenAI 兼容模型

RAG 检索链路：
  用户问题 → 嵌入模型（Ollama bge-m3 / DashScope text-embedding-v3）
    → pgvector 向量检索（HNSW, COSINE_DISTANCE）
    → Reranker（BGE-reranker-v2-m3 Python 微服务）
    → QuestionAnswerAdvisor 注入上下文

工具调用：
  calculateSubsidy → 补贴金额计算
  parseFile        → 发票/旧机参数文件解析
  webSearch        → Tavily 联网搜索
  ToolFailurePolicyCenter → 重试/退避/兜底
```

### 三层项目结构

| 目录 | 说明 |
|------|------|
| `backend/` | Spring Boot 后端，Maven 构建，Java 21 |
| `frontend/` | React + Vite 前端，JavaScript/JSX |
| `rerank-service/` | Python FastAPI 重排序微服务（独立容器） |
| `deploy/` | 生产环境 Docker Compose + 部署脚本 |
| `data/` | 政策文档（PDF/DOCX），知识库原料 |
| `scripts/` | 集成测试脚本 + 政策数据爬虫 |
| `docs/` | API 文档、PRD、设计文档 |

### 后端包结构关键路径

`com.shandong.policyagent`

- **advisor/** — 自定义 Spring AI Advisor（安全过滤、重读校验、日志审计）
- **agent/** — 意图分类器、计划解析器、ReAct 工作流编排
- **controller/** — REST API（含 4 套：chat/auth/conversations、admin、knowledge、multimodal）
- **service/** — 核心业务逻辑层：
  - `ChatService` — 对话编排核心，组合意图分类→计划执行→Advisor 链→降级策略
  - `DynamicChatClientFactory` — 运行时根据管理员绑定的模型动态创建 ChatClient
  - `SessionFactCacheService` — 将对话中提取的关键事实写入 Redis 供多轮复用
  - `FastPathService` — 语义匹配置信度高时跳过 RAG，直接回答
  - `QuestionSemanticCacheService` — 语义级 QA 缓存（余弦阈值 0.92）
  - `ModelProviderService` — 模型提供者管理（AES-GCM 加密 API Key）
- **rag/** — 完整 RAG 管道：文档加载→切片→嵌入→向量存储→检索→重排序→运行时模型路由
- **tool/** — LLM 可调用工具（Spring AI Tool 注解），含统一失败策略中心
- **multimodal/** — DashScope SDK 封装：ASR（qwen3-asr-flash）、视觉分析（qwen-vl-plus）
- **ingestion/** — 多源知识导入：网站爬取（jsoup）→ 结构化入库
- **security/** — 双层 JWT 认证（用户 ROLE_USER / 管理员 ROLE_ADMIN）

### 前端组件结构关键路径

`frontend/src/`

- **pages/** — 8 个页面级组件（ChatPage, LoginPage, HomePage, AdminConsolePage 等）
- **components/admin/** — 管理员控制台整套 UI（含 ConfigPanel, KnowledgeBaseTab, ModelsTab 等 13 个组件）
- **services/** — 3 个 API 服务层（user API, admin API, knowledge API）
- **context/AuthContext.jsx** — 全局认证状态（React Context）
- CSS 设计标记集中定义在 `variables.css`（HSL 色彩体系）

**路由结构：**

| 路径 | 组件 | 鉴权 |
|------|------|------|
| `/chat` | ChatPage | 需要用户 JWT |
| `/admin-console` | AdminConsolePage | 需要管理员 JWT |
| `/login`, `/register` | LoginPage, RegisterPage | 公开 |
| `/home`, `/policies`, `/matching` | HomePage, PolicyQueryPage, PolicyMatchingPage | 公开 |

## 配置体系与环境变量

**配置文件层次：**
1. `deploy/.env` — 生产环境变量（Docker Compose 加载）
2. `backend/src/main/resources/application.yml` — Spring Boot 配置（含所有默认值）
3. `backend/src/main/resources/application-mcp.yml` — MCP 客户端 profile

**必须配置的环境变量：**
- `DASHSCOPE_API_KEY` — 阿里云 DashScope API Key（对话 + 嵌入 + 多模态）
- `APP_JWT_SECRET` — Base64 编码的 JWT 签名密钥

**建议配置：**
- `APP_MODEL_PROVIDER_ENCRYPTION_SECRET` — 模型 API Key 加密主密钥（不配置则回退使用 JWT 密钥）
- `APP_EMBEDDING_OLLAMA_BASE_URL` — Ollama 地址（容器内默认 http://ollama:11434）

**知识库嵌入模型（配置于 application.yml `app.knowledge.embedding.models`）：**
3 个预设嵌入模型：`ollama:bge-m3`（1024维）、`ollama:nomic-embed-text`（768维）、`dashscope:text-embedding-v3`（1024维），各使用独立 pgvector 表存储向量。运行时通过 `RuntimeRagVectorStore` 动态路由。

**RAG 检索配置（application.yml `app.rag.retrieval`）：**
topK=3, similarityThreshold=0.5, candidateTopK=20, rerank 默认启用。

## 关键设计决策

### 对话处理流程
1. `FastPathService` 检查是否可直接回答（语义相似度 ≥ 0.86）
2. 否则执行 ReAct 计划：意图分类 → 工具调用（补贴计算/文件解析/联网搜索）
3. Advisor 链处理：安全过滤 → 记忆注入 → 复核校验 → RAG 上下文注入 → 日志记录
4. 模型调用默认使用管理员绑定的 LLM，支持降级
5. 纯同步模式（SSE 流式已禁用，返回 410）

### 模型管理机制
- 五类模型可动态配置：LLM、视觉、语音、嵌入、重排序
- API Key 存储时 AES-GCM 加密（`ApiKeyCipherService`）
- 通过 `DynamicChatClientFactory` 运行时切换模型
- 管理员在"智能体配置"中绑定模型 → 运行时自动生效

### 知识库多源导入
- 上传 PDF/DOCX → MinIO 存储 → Tika 解析 → 文本切片 → 嵌入 → 存入 pgvector
- 网站导入 → jsoup 爬取 → 人工确认入库 → 切片嵌入
- 支持归档导出/导入
- 后台调度器自动处理待入库切片（轮询间隔 2s，批大小 20）

### 缓存策略
- 对话记忆：Redis，7 天 TTL，6 轮对话后自动摘要
- QA 语义缓存：24h TTL，余弦相似度 0.92 阈值
- 价格缓存：24h TTL，支持预取
- 联网搜索缓存：2h TTL，过期重验证

### 安全设计
- 双层 JWT（用户/管理员），Token 24h 过期
- Secret 密码过滤 + 敏感词检测 + Prompt 注入防护（SecurityAdvisor）
- API Key 加密存储（AES-GCM）
- 工具调用前意图校验（ToolIntentClassifier）
- 工具失败统一兜底策略（ToolFailurePolicyCenter）
- CORS 严格域名白名单

### 多模态
- ASR：qwen3-asr-flash（短音频）/ filetrans（长音频）
- 视觉：qwen-vl-plus（默认）/ qwen-vl-max（高精度）
- 扫描 PDF OCR 兜底：文本提取失败时自动走视觉模型

## 默认管理员

首次启动时自动初始化为 `admin / admin`（由 `AdminInitializer` 完成），建议立即修改密码。

## 容器端口

| 服务 | 内部端口 | 映射端口 |
|------|---------|---------|
| 前端（Nginx） | 80 | 5173 |
| 后端 | 8080 | 8080 |
| PostgreSQL | 5432 | 5432 |
| Redis | 6379 | 6379 |
| MinIO API | 9000 | 9000 |
| MinIO Console | 9001 | 9001 |
| Ollama | 11434 | 11434 |
| Reranker | 80 | 8989 |

## 常见陷阱

- **`APP_JWT_SECRET` 必须是标准 Base64 字符串**，否则管理员登录报 `Illegal base64 character`。
- **先启动基础设施容器，再启动后端**，否则数据库连接拒绝。
- **修改 `.env` 后需重建 `backend` 容器**（`docker compose up -d --force-recreate backend`），环境变量才能生效。
- **流式接口已禁用**（统一走 `/api/chat` 同步响应），`/api/chat/stream` 返回 410。
- **知识库嵌入模型互斥**：每个知识库文件夹绑定一个嵌入模型，切换后需重新入库旧文档。
- **本地开发时前端代理**：Vite 将 `/api` 代理到 `localhost:8080`，后端无需处理 CORS。
