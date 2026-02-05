# 智能政策咨询助手 任务清单
## 📅 第一阶段：MVP 原型 (3周)
### 阶段目标
跑通 RAG + ChatClient 流程，实现基础问答。

#### 环境准备 & 基础设施
- [x] 初始化 Spring Boot 3 + Java 21 项目结构 ✅ 2026-02-05
- [x] 配置 Docker Compose (PostgreSQL + PgVector, Redis) ✅ 2026-02-05
- [x] 验证 Spring AI 依赖 (OpenAI/DashScope API 连接) ✅ 2026-02-05

#### RAG 引擎核心 (ETL)
- [x] 实现文档读取器 (PDF/Markdown Reader) ✅ 2026-02-05
- [x] 实现文本切片逻辑 (TokenTextSplitter) ✅ 2026-02-05
- [x] 集成 Embedding 模型 (DashScope text-embedding-v3) ✅ 2026-02-05
- [x] 实现 VectorStore 写入 (PgVector) ✅ 2026-02-05
- [x] 开发 RAG 检索服务 (Retrieval Service) ✅ 2026-02-05

#### 基础对话服务
- [x] 开发 ChatController 接口 (/api/chat) ✅ 2026-02-05
- [x] 实现基础 Prompt Template 管理 ✅ 2026-02-05
- [x] 集成 ChatClient (含 QuestionAnswerAdvisor) ✅ 2026-02-05

#### 前端接入 (MVP)
- [x] 搭建基础对话界面 (H5/React) ✅ 2026-02-05
- [x] 对接后端流式接口 (SSE) ✅ 2026-02-05
- [ ] 展示 Markdown 渲染 & 引用卡片

## 📅 第二阶段：Agent 增强 (4周)
### 阶段目标
引入 Advisors 和 Tools，提升智能度。

#### Advisors 智能拦截系统
- [x] 实现 ChatMemory Advisor (Redis 存储会话) ✅ 2026-02-05
- [x] 实现 Security Advisor (敏感词过滤) ✅ 2026-02-05
- [x] 实现 ReReading Advisor (答案校验) ✅ 2026-02-05
- [x] 开发结构化日志 Advisor ✅ 2026-02-05

#### Tools 工具体系
- [x] 开发"补贴计算器"工具 (Java Function) ✅ 2026-02-05
- [x] 开发"文件解析"工具 (处理用户上传) ✅ 2026-02-05
- [x] 配置 Function Callback 机制 ✅ 2026-02-05

#### 模型升级
- [x] 接入 语音识别模型"qwen3-asr-flash"、视觉理解模型"qwen-vl-plus" ✅ 2026-02-05
- [x] 实现多模型路由策略 (ModelRouterService) ✅ 2026-02-05
- [ ] 实现Manus智能体

## 📅 第三阶段：MCP 与 生产化 (3周)
### 阶段目标
MCP 协议落地，后台管理，系统稳健性。

#### MCP 协议集成
- [x] 升级 Spring AI 到 1.0.0-M6 (支持 MCP) ✅ 2026-02-05
- [x] 添加 MCP Client 依赖 ✅ 2026-02-05
- [x] 实现联网搜索工具 (WebSearchTool) ✅ 2026-02-05
- [ ] 集成外部 API (如京东/苏宁估价)

#### 后台管理系统
- [ ] 开发向量库管理界面 (查看/修正切片)
- [ ] 开发配置管理 (Advisors 开关)
- [ ] 开发日志分析仪表盘

#### 部署与运维
- [ ] 构建 Docker 镜像
- [ ] 编写 K8s/Serverless 部署配置

## 📅 第四阶段：上线运维 (持续)
### 阶段目标
系统性能优化与成本管控

#### 性能优化
- [ ] 压力测试 (JMeter/Gatling)
- [ ] Token 成本分析与优化




# 智能政策咨询助手 - 核心技术栈 (Tech Stack)
| 模块         | 技术选型                  | 说明                                  |
|--------------|---------------------------|---------------------------------------|
| 语言         | Java 21 (LTS)             | 利用 Virtual Threads 提升并发性能    |
| 框架         | Spring Boot 3.4+          | 核心容器                              |
| AI 框架      | Spring AI 1.0.0-M6        | 提供统一 AI 抽象 + MCP 支持           |
| 数据库       | PostgreSQL 16 + pgvector  | 关系型数据与向量数据共存              |
| 缓存/会话    | Redis 7                   | 存储 ChatMemory 和会话状态            |
| 模型服务     | DeepSeek-V3 / Qwen-Max    | 通过 OpenAI 兼容接口接入              |
| 前端         | React/Vite / UniApp       | H5 与 小程序跨端开发                  |
| 部署         | Docker / Serverless       | 容器化交付                            |
