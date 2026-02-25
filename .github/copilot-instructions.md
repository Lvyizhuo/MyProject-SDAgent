# Copilot 指南 — MyProject-SDAgent

目的：让 AI 编码代理快速上手本仓库，理解架构、关键约定、常用命令及修改点。

- 项目概览：后端为 Spring Boot 3.4 (Java 21) + Spring AI；前端为 React 19 + Vite。向量存储使用 PostgreSQL+pgvector，聊天记忆使用 Redis。

- 快速启动（开发顺序）
  1. 启动基础 infra：在 `backend` 执行 `docker compose up -d`（Postgres(pgvector) + Redis）。
  2. 启动后端：`cd backend && ./mvnw spring-boot:run`（可用 `-Dspring-boot.run.profiles=mcp` 启动 mcp 配置）。
  3. 启动前端：`cd frontend && npm install && npm run dev`。

- 关键文件/目录（阅读优先级）
  - 后端入口与包：backend/src/main/java/com/shandong/policyagent/
  - 配置：[backend/src/main/resources/application.yml](backend/src/main/resources/application.yml)
  - 基础设施：[backend/docker-compose.yml](backend/docker-compose.yml)
  - 构建：[backend/pom.xml](backend/pom.xml)
  - 前端入口：[frontend/src/main.jsx](frontend/src/main.jsx) 与 [frontend/package.json](frontend/package.json)
  - 项目级约定：[AGENTS.md](AGENTS.md)、[CLAUDE.md](CLAUDE.md)、[README.md](README.md)

- 重要工程约定（只记录可发现的、必须遵守的）
  - Java 风格：导入顺序（java/jakarta -> 第三方 -> 本项目）；类用 Lombok 注解优先，接着 Spring 注解；使用 `final` 字段 + `@RequiredArgsConstructor` 构造注入（见 AGENTS.md）。
  - 控制器（`controller/`）应保持薄：校验（`@Valid`）和委托给 `service/` 层。
  - Advisor 链路：模型请求通过 `advisor/` 中的 Spring AI advisors 串联。默认顺序参见 `ChatClientConfig`（SecurityAdvisor order=10, MessageChatMemory, ReReading order=50, QuestionAnswer, Logging order=90）。修改顺序需编辑 `ChatClientConfig`。
  - 工具（LLM 可调用）：实现类放在 `tool/`（如 `calculateSubsidy`, `parseFile`, `webSearch`）。新增工具同时在配置类（ChatClientConfig 或相应 Bean 注册处）注册。
  - RAG/向量：文档切片与检索逻辑在 `rag/`，向量后端是 Postgres+pgvector，embedding 模型配置在 `application.yml`（项目使用 `text-embedding-v3` 类似配置）。

- 运行测试 / 诊断
  - 后端单元/集成测试：`cd backend && ./mvnw test`。运行单个测试类/方法：`-Dtest=ClassName` / `-Dtest=ClassName#methodName`。
  - 日志与报告：maven surefire 报告位于 `backend/target/surefire-reports/`。

- 外部依赖与环境变量
  - 必需：`DASHSCOPE_API_KEY`（DashScope 大模型 API）
  - 可选（联网搜索）：`TAVILY_API_KEY`
  - 在本地调试时优先确认 `backend/src/main/resources/application.yml` 中的 RAG/AI/DB 配置与环境变量映射。

- 修改建议（常见任务的入手点）
  - 增加新 API：在 `controller/` 增加端点，业务逻辑放 `service/`，持久化放 `repository/`。
  - 增加/调整 Advisor：编辑 `backend/src/main/java/com/shandong/policyagent/config/ChatClientConfig.java`（或同目录下相关配置类），保证 bean 顺序。
  - 新增 LLM 工具：新类放 `tool/`，实现必要接口/方法，并在 ChatClient 的配置中注入为 Spring Bean。
  - 修改 RAG 行为：查阅 `rag/` 下实现并同时检查 `application.yml` 的向量/检索参数。

- 注意事项（快速提示）
  - 启动后端前确保 Postgres + Redis 已就绪（`docker compose ps` / `docker compose logs`）。
  - 流式对话使用 SSE：端点 `POST /api/chat/stream` 返回 `text/event-stream`。
  - 鉴权：`/api/conversations/**` 与 `/api/auth/me` 需要 `Authorization: Bearer <JWT>`。

想要我把该文件直接提交到仓库吗？或者你希望在某些段落补充更具体的文件/行号示例？
