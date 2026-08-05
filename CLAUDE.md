# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概览

**AI 政策通·智能体服务平台** — 基于 AI/LLM 的山东省以旧换新政策咨询系统。前后端分离架构，核心围绕 RAG（检索增强生成）+ 多智能体（Multi-Agent）构建政策问答、补贴计算、多模态识别能力。

## 开发命令

### 后端（Spring Boot 3.4 + Java 21）

```bash
cd backend

# 构建（跳过测试以加速）
./mvnw clean package -DskipTests

# 本地开发运行（需先启动基础设施容器：postgres, redis, minio, ollama, reranker）
./mvnw spring-boot:run

# 带 MCP profile 运行
./mvnw spring-boot:run -Dspring-boot.run.profiles=mcp

# 运行全部测试
./mvnw test

# 运行单个测试类/方法
./mvnw test -Dtest=ChatServiceTest
./mvnw test -Dtest=ChatServiceTest#testChatResponse
```

### 前端（React 19 + Vite 7，包管理器：pnpm）

```bash
cd frontend

pnpm install           # 安装依赖
pnpm run dev           # 开发服务器（热重载，代理 /api → localhost:8080）
pnpm run build         # 生产构建
pnpm run lint          # ESLint 检查
pnpm run preview       # 预览生产构建
```

### 基础设施

```bash
# 开发环境启动基础设施（不含后端/前端容器）
cd backend
docker compose up -d postgres redis minio ollama ollama-init reranker

# 查看容器状态
docker compose ps

# 查看日志
docker compose logs -f postgres
```

### 本地开发一键启动

```bash
# 项目根目录下，自动检查基础设施、启动后端+前端
./dev-start.sh

# 停止所有本地服务
./dev-start.sh stop
```

### 生产部署

```bash
# 服务器一键部署（脚本含健康检查）
cd deploy
cp .env.example .env    # 编辑 .env 填写真实配置
./deploy.sh             # Linux/macOS

# 子命令
./deploy.sh restart     # 仅重启 backend + frontend（不重建镜像）
./deploy.sh status      # 查看所有容器状态 + 健康检查
./deploy.sh logs <svc>  # 跟踪指定服务日志
./deploy.sh down        # 停止（保留数据卷）

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
用户请求（React 前端 — DeepSeek 风格聊天界面）
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

多智能体管理层（Admin Console）：
  AgentInstanceService ─── 管理多个智能体实例（政策咨询/补贴计算/合同审查等）
    ├─ AgentModelBinding ─ 每个智能体可独立绑定 LLM/VISION/AUDIO/EMBEDDING/RERANK 模型
    ├─ ToolDefinition ──── 工具卡片化展示与集中开关（webSearch/subsidyCalc/fileParse）
    └─ syncDefaultAgentToRuntime ─ 将默认智能体配置同步到运行时 AgentConfig

RAG 检索链路：
  用户问题 → 嵌入模型（Ollama bge-m3 / DashScope text-embedding-v3）
    → pgvector 向量检索（HNSW, COSINE_DISTANCE）
    → Reranker（bge-reranker-v2-m3，支持 TEI/Xinference/DashScope 三 provider）
    → QuestionAnswerAdvisor 注入上下文

工具调用：
  calculateSubsidy → 补贴金额计算
  parseFile        → 发票/旧机参数文件解析
  webSearch        → Tavily 联网搜索
  ToolFailurePolicyCenter → 重试/退避/兜底
```

### 五层项目结构

| 目录 | 说明 |
|------|------|
| `backend/` | Spring Boot 后端，Maven 构建，Java 21 |
| `frontend/` | React + Vite 前端，JavaScript/JSX，pnpm 包管理 |
| `rerank-service/` | Python FastAPI 重排序微服务（独立容器，TEI 兼容协议） |
| `deploy/` | 生产环境 Docker Compose + 部署脚本（含子命令系统） |
| `data/` | 政策文档（PDF/DOCX），知识库原料 |
| `scripts/` | 集成测试脚本 + 政策数据爬虫 |
| `docs/` | API 文档、PRD、设计文档 |

### 后端包结构关键路径

`com.shandong.policyagent`

- **advisor/** — 自定义 Spring AI Advisor（安全过滤、重读校验、日志审计）
- **agent/** — 意图分类器、计划解析器、ReAct 工作流编排
- **controller/** — REST API（含 6 套：chat/auth/conversations、admin/agents、admin/models-card、admin/tools、admin/knowledge、multimodal、public-config）
- **service/** — 核心业务逻辑层：
  - `ChatService` — 对话编排核心，组合意图分类→计划执行→Advisor 链→降级策略
  - `DynamicChatClientFactory` — 运行时根据管理员绑定的模型动态创建 ChatClient
  - `AgentInstanceService` — 多智能体实例 CRUD、默认设置、复制、同步到运行时配置
  - `ConsoleCardSeedService` — 启动时自动种子化默认智能体/工具/模型数据
  - `ModelCardService` — 卡片视图专用模型服务（只读 + 简化测试）
  - `ToolDefinitionService` — 工具定义的 CRUD 和启用管理
  - `ModelProviderService` — 模型提供者管理（AES-GCM 加密 API Key、连接测试、运行时解密、内置模型合并）
  - `AgentConfigBindingSanitizer` — 自动清理失效的模型绑定和知识库目录绑定
  - `SessionFactCacheService` — 将对话中提取的关键事实写入 Redis 供多轮复用
  - `FastPathService` — 语义匹配置信度高时跳过 RAG，直接回答
  - `QuestionSemanticCacheService` — 语义级 QA 缓存（余弦阈值 0.92）
- **entity/** — JPA 实体：
  - `AgentInstance` — 智能体实例（名称/编码/分类/状态/系统提示词/开场白/技能/MCP配置）
  - `AgentModelBinding` — 智能体-模型多对多绑定（LLM/VISION/AUDIO/EMBEDDING/RERANK 角色）
  - `ToolDefinition` — 工具定义（编码/名称/分类/启用状态/调用统计）
  - `ModelProvider` — 模型提供者（含 builtinCode/dimensions/builtIn 内置模型标识）
  - `User` — 用户实体（含 @Builder 支持，实现 UserDetails）
- **model/dto/** — 卡片视图与请求 DTO：
  - `AgentCardDto` / `AgentUpsertRequest` — 智能体卡片/编辑请求
  - `ModelCardDto` / `ToggleEnabledRequest` — 模型卡片/切换请求
  - `ToolCardDto` / `ToolUpsertRequest` — 工具卡片/编辑请求
- **rag/** — 完整 RAG 管道：文档加载→切片→嵌入→向量存储→检索→多 provider 重排序（TEI/Xinference/DashScope）→运行时模型路由
- **tool/** — LLM 可调用工具（Spring AI Tool 注解），含统一失败策略中心
- **multimodal/** — DashScope SDK 封装：ASR（qwen3-asr-flash）、视觉分析（qwen-vl-plus）
- **ingestion/** — 多源知识导入：网站爬取（jsoup）→ 结构化入库
- **exception/** — 统一异常处理（BusinessException + GlobalExceptionHandler）
- **security/** — 双层 JWT 认证（用户 ROLE_USER / 管理员 ROLE_ADMIN）

### 前端组件结构关键路径

`frontend/src/`

- **components/ui/** — DeepSeek 风格聊天界面（新增）：
  - `ChatPage.jsx` — 顶层布局，会话管理（创建/选择/删除），移动端侧边栏 FAB
  - `ChatWindow.jsx` — 消息列表、欢迎界面、地理位置检测、建议卡片、引用渲染
  - `InputArea.jsx` — 输入工具栏（文本域/语音输入/文件附件/联网搜索/补贴计算开关）
  - `MessageBubble.jsx` — AI/用户消息渲染（Markdown/思考过程折叠/地图卡片/操作按钮）
  - `Sidebar.jsx` — 可折叠侧边栏（会话历史/搜索/用户卡片），移动端叠加层
- **components/admin/** — 管理员控制台：
  - `AdminSidebar.jsx` — 左侧折叠导航栏（智能体/知识库/工具/模型 + 分析组）
  - `TopTabs.jsx` — 顶部粘性导航栏（毛玻璃效果，品牌标识 + 标签切换 + 用户菜单）
  - `AgentsTab.jsx` — 智能体管理（卡片网格/筛选/编辑模态框/模型绑定/技能开关）
  - `KnowledgeTab.jsx` — 知识库管理（目录树/文档列表/上传/网站导入/详情抽屉）
  - `ModelsTab.jsx` — 模型管理（类型筛选/供应商预设/卡片展示/连接测试）
  - `ToolsTab.jsx` — 工具管理（分类筛选/开关切换/详情面板）
  - `AdminNavbar.jsx` — 移动端导航栏（菜单切换/通知/用户下拉）
  - `admin-card.css` / `admin-modal.css` — 共享卡片/模态框样式系统
- **pages/** — 页面级组件（HomePage, LoginPage, AdminConsolePage 等）
- **services/** — 6 个 API 服务层：
  - `api.js` — 用户 API（chat/auth/conversations）
  - `adminApi.js` — 管理员旧 API（agent-config/knowledge）
  - `adminAgentApi.js` — 智能体管理 API（新增）
  - `adminModelApi.js` — 模型管理 API（新增，含连接测试）
  - `adminToolApi.js` — 工具管理 API（新增）
  - `adminKnowledgeApi.js` — 知识库 API
- **context/AuthContext.jsx** — 全局认证状态（React Context）
- CSS 设计标记集中定义在 `variables.css`（HSL 色彩体系），chat-ui.css 定义聊天界面样式

**路由结构：**

| 路径 | 组件 | 鉴权 |
|------|------|------|
| `/chat` | ChatPage（DeepSeek 风格新 UI） | 需要用户 JWT |
| `/admin-console` | AdminConsolePage（TopTabs 导航 + 4 标签页） | 需要管理员 JWT |
| `/login`, `/register` | LoginPage, RegisterPage | 公开 |
| `/home`, `/policies`, `/matching` | HomePage, PolicyQueryPage, PolicyMatchingPage | 公开 |
| `/user` | UserCenterPage | 公开 |

## 配置体系与环境变量

**配置文件层次：**
1. `deploy/.env` — 生产环境变量（Docker Compose 加载）
2. `backend/src/main/resources/application.yml` — Spring Boot 配置（含所有默认值，大量属性已环境变量化）
3. `backend/src/main/resources/application-mcp.yml` — MCP 客户端 profile
4. `backend/lombok.config` — Lombok 全局行为配置（关闭 fluent/chain 模式）
5. `backend/maven-settings.xml` — Maven 阿里云镜像加速（Docker 构建时使用）

**必须配置的环境变量：**
- `DASHSCOPE_API_KEY` — 阿里云 DashScope API Key（对话 + 嵌入 + 多模态）
- `APP_JWT_SECRET` — Base64 编码的 JWT 签名密钥

**建议配置：**
- `APP_MODEL_PROVIDER_ENCRYPTION_SECRET` — 模型 API Key 加密主密钥（不配置则回退使用 JWT 密钥）
- `APP_EMBEDDING_OLLAMA_BASE_URL` — Ollama 地址（容器内默认 http://ollama:11434）

**重排序配置（环境变量化）：**
- `APP_RAG_RERANK_PROVIDER` — 重排序 provider（tei/dashscope/xinference，默认 tei）
- `APP_RAG_RERANK_MODEL` — 重排序模型名（默认 bge-reranker-v2-m3）
- `APP_RAG_RERANK_ENDPOINT` — 重排序服务端点（默认 http://localhost:8989/rerank）

**知识库嵌入模型（配置于 application.yml `app.knowledge.embedding.models`）：**
3 个预设嵌入模型：`ollama:bge-m3`（1024维，默认）、`ollama:nomic-embed-text`（768维）、`dashscope:text-embedding-v3`（1024维），各使用独立 pgvector 表存储向量。运行时通过 `RuntimeRagVectorStore` 动态路由。

**RAG 检索配置（application.yml `app.rag.retrieval`）：**
topK=3, similarityThreshold=0.5, candidateTopK=20, rerank 默认启用。rerankMaxDocChars=3000, promptMaxDocChars=1200。

**模型调用超时配置（application.yml `app.model-provider`）：**
- OpenAI 兼容调用：connect=10s, read=90s
- 直连模型测试/降级：connect=10s, read=90s

## 关键设计决策

### 对话处理流程
1. `FastPathService` 检查是否可直接回答（语义相似度 ≥ 0.86）
2. 否则执行 ReAct 计划：意图分类 → 工具调用（补贴计算/文件解析/联网搜索）
3. Advisor 链处理：安全过滤 → 记忆注入 → 复核校验 → RAG 上下文注入 → 日志记录
4. 模型调用默认使用管理员绑定的 LLM，支持降级
5. 纯同步模式（SSE 流式已禁用，返回 410）

### 多智能体架构
- 系统支持创建多个智能体实例（AgentInstance），每个可独立配置模型绑定、工具、系统提示词和开场白
- 智能体分类：对话型（chat）/ 工作流（workflow）/ 客服型（support），状态：draft/published
- 模型绑定通过 AgentModelBinding 实现多对多关联，支持 LLM/VISION/AUDIO/EMBEDDING/RERANK 五种角色
- 默认智能体的配置自动同步到运行时 AgentConfig（`syncDefaultAgentToRuntime`），确保前台聊天立即生效
- 启动时 `ConsoleCardSeedService` 自动种子化 5 个默认智能体和 4 个默认工具

### 模型管理机制
- 五类模型可动态配置：LLM、视觉、语音、嵌入、重排序
- API Key 存储时 AES-GCM 加密（`ApiKeyCipherService`），支持历史密钥兼容解密
- 通过 `DynamicChatClientFactory` 运行时切换模型
- 内置模型与托管模型合并逻辑：数据库无记录时自动插入系统内置嵌入/重排序模型，避免重复显示
- 管理员在"智能体配置"中绑定模型 → 运行时自动生效
- 模型卡片视图（ModelCardService）提供轻量级只读 + 简化测试，不修改底层数据

### 工具管理系统
- 工具定义（ToolDefinition）实体化管理，支持全局启用/禁用开关
- 4 个默认工具类别：联网搜索（web）、补贴计算（calc）、文件解析（parse）、外部 API（external）
- 工具调用前意图校验（ToolIntentClassifier）+ 失败统一兜底策略（ToolFailurePolicyCenter）

### 重排序服务重构
- 支持三种 Provider：TEI（本地容器，默认）、DashScope（阿里云 API）、Xinference（本地推理框架）
- 端点自动补全与 404 自动重试回退（DashScope 兼容模式 ↔ API v1 路径切换）
- 本地推理（TEI/Xinference）无需 API Key，DashScope 需要 Authorization header
- 独立 `rerank-service/` 微服务（Python FastAPI + sentence-transformers），提供 TEI 兼容 `/rerank` 端点

### 知识库多源导入
- 上传 PDF/DOCX → MinIO 存储 → Tika 解析 → 文本切片 → 嵌入 → 存入 pgvector
- 网站导入 → jsoup 爬取 → 人工确认入库 → 切片嵌入
- 支持归档导出/导入
- 后台调度器自动处理待入库切片（轮询间隔 2s，批大小 20）
- 扫描 PDF OCR 兜底：文本提取失败时自动走视觉模型

### 缓存策略
- 对话记忆：Redis，7 天 TTL，6 轮对话后自动摘要
- QA 语义缓存：24h TTL，余弦相似度 0.92 阈值
- 价格缓存：24h TTL，支持预取
- 联网搜索缓存：2h TTL，过期重验证

### 安全设计
- 双层 JWT（用户/管理员），Token 24h 过期
- Secret 密码过滤 + 敏感词检测 + Prompt 注入防护（SecurityAdvisor）
- API Key 加密存储（AES-GCM），支持多历史密钥兼容解密
- 工具调用前意图校验（ToolIntentClassifier）
- 工具失败统一兜底策略（ToolFailurePolicyCenter）
- CORS 严格域名白名单
- 统一异常处理（BusinessException + GlobalExceptionHandler，支持 400/413/异步断开等场景）

### 多模态
- ASR：qwen3-asr-flash（短音频）/ filetrans（长音频）
- 视觉：qwen-vl-plus（默认）/ qwen-vl-max（高精度）
- 扫描 PDF OCR 兜底：文本提取失败时自动走视觉模型

### 启动初始化
- 默认管理员账号：`admin / admin`（由 `AdminInitializer` 完成）
- `ConsoleCardSeedService` 在 `ApplicationReadyEvent` 时检查空库并灌入默认数据：
  - 5 个默认智能体：政策咨询助手（默认）/ 补贴计算专家 / 合同审查助手 / 数据分析助手（草稿）/ 语音接待助手
  - 4 个默认工具：联网搜索 / 补贴计算器 / 文件解析 / 代码沙箱
  - 内置嵌入模型和重排序模型配置
- 后端 Dockerfile 安装 Node.js（供 MCP stdio servers）+ poppler-utils（供 PDF OCR）

## 默认管理员

首次启动时自动初始化为 `admin / admin`（由 `AdminInitializer` 完成），建议立即修改密码。

## 容器端口

| 服务 | 内部端口 | 映射端口 |
|------|---------|---------|
| 前端（Nginx） | 80 | 5173 |
| 后端 | 8080 | 8080 |
| PostgreSQL | 5432 | 5433 |
| Redis | 6379 | 6379 |
| MinIO API | 9000 | 9000 |
| MinIO Console | 9001 | 9001 |
| Ollama | 11434 | 11434 |
| Reranker | 80 | 8989 |

## 常见陷阱

- **`APP_JWT_SECRET` 必须是标准 Base64 字符串**，否则管理员登录报 `Illegal base64 character`。
- **先启动基础设施容器，再启动后端**，否则数据库连接拒绝。基础设施现包含 5 个服务：postgres, redis, minio, ollama, reranker。
- **修改 `.env` 后需重建 `backend` 容器**（`docker compose up -d --force-recreate backend`），环境变量才能生效。
- **流式接口已禁用**（统一走 `/api/chat` 同步响应），`/api/chat/stream` 返回 410。
- **知识库嵌入模型互斥**：每个知识库文件夹绑定一个嵌入模型，切换后需重新入库旧文档。
- **本地开发时前端代理**：Vite 将 `/api` 代理到 `localhost:8080`，后端无需处理 CORS。
- **前端包管理器已切换为 pnpm**：使用 `pnpm install` 而非 `npm install`，`pnpm-lock.yaml` 为锁文件。
- **Lombok 升级至 1.18.46**：Docker 构建使用 `--no-cache` 避免旧 class 文件缓存。Boolean 字段（isDefault/isEnabled）使用显式 getter/setter 绕开命名歧义。
- **Reranker 首次启动需下载模型**：从 HuggingFace 下载 `bge-reranker-v2-m3`（约 2.2GB），需数分钟。`start_period=600s`。
- **Ollama 同时加载两个模型**：`OLLAMA_MAX_LOADED_MODELS=2`，默认拉取 `bge-m3` + `nomic-embed-text`。
- **数据库迁移**：Flyway V9 新增 `agent_instance`、`agent_model_binding`、`tool_definition` 三张表。Hibernate `ddl-auto: update` 自动建表，SQL 脚本作为 schema 文档保留。
