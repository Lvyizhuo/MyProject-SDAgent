这里为您提供了《山东省智能政策咨询助手》项目的中文版开发规范文档。

---

### AGENTS.md - 政策智能体（山东省智能政策咨询助手）

一个基于 AI/LLM 的山东省以旧换新政策咨询聊天机器人。

#### 项目结构

```text
/
├── backend/               # 后端：Spring Boot 3.4 + Spring AI (Java 21)
│   └── src/main/java/com/shandong/policyagent/
│       ├── advisor/       # Spring AI Advisors (安全、记忆、日志等)
│       ├── agent/         # Agent 计划解析与意图分类 (AgentPlanParser/ToolIntentClassifier)
│       ├── config/        # Spring 配置类
│       ├── controller/    # REST API 控制器 (含 admin 配置与知识库接口)
│       ├── entity/        # JPA 实体类
│       ├── exception/     # 全局异常处理器
│       ├── model/         # 数据传输对象和领域模型
│       ├── multimodal/    # 多模态能力 (ASR/视觉)
│       ├── rag/           # RAG 相关服务 (文档加载、切片、检索)
│       ├── repository/    # 数据访问层
│       ├── security/      # JWT 认证相关
│       ├── service/       # 业务逻辑服务 (包括 SessionFactCacheService 会话事实缓存)
│       └── tool/          # LLM 可调用工具 (补贴/文件解析/联网搜索/ToolFailurePolicyCenter)
├── frontend/              # 前端：React 19 + Vite 7 (JavaScript/JSX)
│   └── src/
│       ├── components/    # React 组件 (含 components/admin)
│       └── *.css          # CSS 模块 (variables.css 用于设计标记)
└── data/                  # 政策文档数据
```

#### 构建与运行命令

**后端 (Spring Boot)**

```bash
cd backend

# 构建项目
./mvnw clean package

# 运行 (需要先启动 PostgreSQL + Redis + MinIO)
./mvnw spring-boot:run

# mcp profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=mcp

# 运行所有测试
./mvnw test

# 运行单个测试类
./mvnw test -Dtest=ChatServiceTest

# 运行单个测试方法
./mvnw test -Dtest=ChatServiceTest#testChatResponse
```

**前端 (Vite + React)**

```bash
cd frontend

# 安装依赖
npm install

# 启动开发服务器 (热重载)
npm run dev

# 构建生产版本
npm run build

# 代码检查
npm run lint

# 预览生产构建
npm run preview
```

**基础设施 (Docker)**

```bash
cd backend

# 启动 PostgreSQL (pgvector) + Redis + MinIO
docker compose up -d

# 停止服务
docker compose down
```

**服务器部署 (宝塔 + Docker Compose)**

```bash
cd /www/wwwroot/MyProject-SDAgent
cp deploy/.env.example deploy/.env
vi deploy/.env

cd deploy
docker compose --env-file .env up -d --build
docker compose ps
```

健康检查：

```bash
curl -f http://127.0.0.1:8080/actuator/health
curl -f http://127.0.0.1:8080/api/chat/health
curl -f http://127.0.0.1:5173/health
```

宝塔 Nginx 注意事项：
- 自定义配置中不要再写 `server {}`。
- 同一个站点只保留一个 `location /`（避免 `duplicate location "/"`）。
- 路由建议：`/ -> 127.0.0.1:5173`，`/api/ -> 127.0.0.1:8080/api/`。
- 配置后执行 `nginx -t && nginx -s reload`。

更新流程：

```bash
cd /www/wwwroot/MyProject-SDAgent
git pull
cd deploy
docker compose --env-file .env up -d --build
```

#### 代码风格规范

**后端 (Java)**

**导入顺序：**
1. Java 标准库 (`java.*`, `jakarta.*`)
2. 第三方库 (`org.springframework.*`, `lombok.*`)
3. 项目内部类 (`com.shandong.policyagent.*`)

**命名规范：**
- 类名：帕斯卡命名法 (`ChatController`, `ChatService`)
- 方法/变量：驼峰命名法 (`chatStream`, `conversationId`)
- 常量：全大写下划线命名法 (`MAX_RETRIES`)
- 包名：全小写 (`controller`, `service`, `model`)

**类结构规范：**
1. 首先是 Lombok 注解 (`@Slf4j`, `@Data`, `@Builder`)
2. 紧接着是 Spring 注解 (`@RestController`, `@Service`)
3. 类级别注解独占一行
4. 使用 `final` 字段进行依赖注入 (通过 `@RequiredArgsConstructor` 构造器注入)

**设计模式：**
- 使用 Lombok 减少样板代码 (`@Data`, `@Builder`, `@RequiredArgsConstructor`)
- 在请求参数上使用 `@Valid` 进行校验 (Jakarta Validation)
- 控制器层仅负责转发请求，不包含业务逻辑，全部委托给服务层
- 使用 Slf4j 进行日志记录 (`@Slf4j` 注解，使用 `log.info()`)
- 成功响应直接返回数据，错误响应使用 `ResponseEntity` 包装

**异常处理：**
- 在 `GlobalExceptionHandler` 类中统一处理异常
- 返回包含时间戳、状态码和消息的结构化错误响应
- 使用 `log.error()` 记录异常并包含堆栈跟踪
- 异常处理器按具体异常 -> 通用异常的顺序排列

**前端 (React/JavaScript)**

**导入顺序：**
1. React 及其钩子 (`import React, { useState } from 'react'`)
2. 第三方库 (`import { marked } from 'marked'`)
3. 本地组件 (`import MessageBubble from './MessageBubble'`)
4. CSS 文件最后导入 (`import './Component.css'`)

**命名规范：**
- 组件：帕斯卡命名法 (文件名和导出名均为 `ChatWindow.jsx`)
- 钩子/工具函数：驼峰命名法 (`useState`, `scrollToBottom`)
- CSS 类名：短横线命名法 (`message-row`, `input-field`)
- 事件处理函数：`handle` 前缀 (`handleSend`, `handleSubmit`)

**组件结构：**
1. 导入语句
2. 函数式组件声明
3. 状态声明 (`useState`)
4. 引用声明 (`useRef`)
5. 副作用处理 (`useEffect`)
6. 事件处理函数
7. 返回 JSX 结构
8. 默认导出

**状态管理：**
- 使用 `useState` 钩子管理局部状态
- 无全局状态库 (应用较简单)
- 通过属性 (Props) 进行父子组件通信

**CSS 规范：**
- 设计标记定义在 `variables.css` 中
- 颜色格式使用 HSL：`--color-primary: 215 90% 35%`
- 样式中使用 `hsl(var(--color-name))` 引用变量
- 组件样式文件独立 (例如 `ChatWindow.css`)
- 间距标记：`--space-xs`, `--space-sm`, `--space-md`, `--space-lg`, `--space-xl`

#### 配置文件说明

| 文件 | 用途 |
| :--- | :--- |
| `backend/pom.xml` | Maven 构建配置，依赖管理 |
| `backend/src/main/resources/application.yml` | Spring Boot 配置文件 |
| `backend/docker-compose.yml` | PostgreSQL (pgvector) + Redis + MinIO 服务编排 |
| `frontend/package.json` | npm 依赖包和脚本 |
| `frontend/vite.config.js` | Vite 构建配置 |
| `frontend/eslint.config.js` | ESLint 代码检查规则 |

#### 关键技术栈

**后端：**
- Java 21 + Spring Boot 3.4.1
- Spring AI 1.0.3 (兼容 OpenAI，使用阿里云 DashScope/通义千问)
- PostgreSQL 16 配合 pgvector 扩展 (用于 RAG 向量存储)
- Redis 7 (用于聊天记忆/会话存储)
- MinIO (用于知识库原始文档对象存储)
- Spring Security + JWT (认证鉴权)
- Lombok + Jakarta Validation

**前端：**
- React 19.2 + Vite 7.2
- react-router-dom (路由)
- lucide-react (图标库)
- marked (Markdown 渲染)
- uuid (生成唯一 ID)
- ESLint 9 配合 React hooks 插件

#### API 接口列表

| 方法 | 路径 | 描述 |
| :--- | :--- | :--- |
| POST | `/api/chat` | 标准对话 (完整响应) |
| POST | `/api/chat/stream` | 流式对话 (SSE) |
| GET | `/api/chat/health` | 健康检查 |
| POST | `/api/documents/load` | 加载默认文档 |
| POST | `/api/documents/load-directory` | 加载指定目录文档 |
| DELETE | `/api/documents` | 按 id 删除文档 |
| POST | `/api/auth/register` | 用户注册 |
| POST | `/api/auth/login` | 用户登录 |
| GET | `/api/auth/me` | 当前用户信息 |
| GET | `/api/conversations` | 当前用户会话列表 |
| GET | `/api/conversations/{sessionId}` | 获取/创建指定会话 |
| DELETE | `/api/conversations/{sessionId}` | 删除会话 |
| POST | `/api/admin/auth/login` | 管理员登录 |
| POST | `/api/admin/auth/change-password` | 修改管理员密码 |
| GET | `/api/admin/agent-config` | 获取管理员智能体配置 |
| PUT | `/api/admin/agent-config` | 更新管理员智能体配置 |
| POST | `/api/admin/agent-config/reset` | 重置管理员智能体配置 |
| POST | `/api/admin/agent-config/test` | 管理员配置测试对话 |
| GET | `/api/admin/knowledge/folders` | 知识库目录树 |
| POST | `/api/admin/knowledge/documents` | 上传知识库文档 |
| GET | `/api/admin/knowledge/documents` | 分页查询知识库文档 |
| GET | `/api/admin/knowledge/documents/{id}/chunks` | 查询文档切片结果 |
| POST | `/api/admin/knowledge/documents/{id}/reingest` | 重新入库文档 |
| GET | `/api/admin/knowledge/embedding-models` | 获取可用嵌入模型 |
| POST | `/api/multimodal/transcribe` | 语音识别 |
| POST | `/api/multimodal/analyze-image` | 图像分析 |
| POST | `/api/multimodal/analyze-invoice` | 发票识别 |
| POST | `/api/multimodal/analyze-device` | 设备识别 |

#### 环境变量

后端运行所需环境变量：
- `DASHSCOPE_API_KEY` - 阿里云 DashScope API 密钥（必需）
- `TAVILY_API_KEY` - Tavily 搜索 API 密钥（启用联网搜索时需要）
- `APP_JWT_SECRET` - JWT 签名密钥（生产环境必需）
- `APP_SECURITY_CORS_ALLOWED_ORIGIN_PATTERNS` - CORS 放行来源（生产域名）
- `APP_EMBEDDING_OLLAMA_BASE_URL` - Ollama 嵌入服务地址（容器内默认 `http://ollama:11434`）

#### 类型安全

**后端：** 使用 Lombok 生成的 Getter/Setter 与 Java 类型系统实现强类型约束
**前端：** 使用原生 JavaScript (无 TypeScript)

#### 常见注意事项

1. **后端：** 在 `@RequestBody` 参数上优先使用 `@Valid` 注解进行校验。
2. **前端：** 别忘了在组件中导入对应的 CSS 文件。
3. **Docker：** 确保 PostgreSQL、Redis、MinIO 均健康后，再启动 Spring Boot 应用。
4. **API：** 流式接口返回类型为 `text/event-stream`。
5. **鉴权：** `/api/conversations/**` 与 `/api/auth/me` 需要 `Authorization: Bearer <JWT>`。
6. **管理员鉴权：** `/api/admin/**` 需要管理员角色 JWT；默认管理员账号在首次启动时由 `AdminInitializer` 初始化为 `admin/admin`（建议立即修改密码）。
7. **工具调用：** 通过 `ToolIntentClassifier` 进行前置校验，参数不足时会先向用户补充必要参数。
8. **会话事实：** `SessionFactCacheService` 会将关键事实（价格、地区、设备型号等）缓存到 Redis 供多轮对话复用。

#### Advisor 执行顺序

当前默认链路配置顺序（`ChatClientConfig`）：

| Advisor | 顺序/位置 | 功能 |
| :--- | :--- | :--- |
| SecurityAdvisor | order=10 | 敏感词过滤、Prompt Injection 防护 |
| MessageChatMemoryAdvisor | 默认链路第2位 | 多轮对话上下文管理 |
| ReReadingAdvisor | order=50 | 强制模型核对答案准确性 |
| QuestionAnswerAdvisor | 默认链路第4位 | RAG 检索增强 |
| LoggingAdvisor | order=90 | Token 消耗、响应延迟、引用来源记录 |

#### 可用工具 (Tools)

| 工具名 | 功能 |
| :--- | :--- |
| `calculateSubsidy` | 计算山东省以旧换新补贴金额 |
| `parseFile` | 解析发票/旧机参数文件并提取结构化字段 |
| `webSearch` | 联网查询实时价格、新闻与政策动态 |

#### Agent 相关组件

| 组件名 | 功能 |
| :--- | :--- |
| `ToolIntentClassifier` | 工具调用前意图分类器，用于降低无效工具调用，在执行工具前进行参数校验 |
| `AgentPlanParser` | Agent 计划解析器，解析和执行多步计划 |
| `SessionFactCacheService` | 会话事实缓存服务，将关键事实（价格、地区、设备型号等）结构化写入 Redis 供多轮对话复用 |
| `ToolFailurePolicyCenter` | 工具失败策略中心，统一管理重试、退避与兜底提示模板 |
