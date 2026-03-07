# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

山东省智能政策咨询助手是一个基于 AI 的大语言模型驱动问答系统，面向山东省以旧换新补贴政策咨询，采用 RAG（检索增强生成）实现政策知识检索与回答。

## 技术栈

- **前端**: React 19 + Vite 7 (JavaScript/JSX)
- **后端**: Spring Boot 3.4.1 + Spring AI 1.0.3 (Java 21)
- **AI 模型**: 阿里云 DashScope（默认 `qwen3.5-plus`，支持可配置嵌入模型）
- **向量数据库**: PostgreSQL 16 + pgvector
- **会话存储**: Redis 7
- **对象存储**: MinIO
- **身份验证**: Spring Security + JWT

## 项目结构

```text
├── backend/
│   ├── src/main/java/com/shandong/policyagent/
│   │   ├── advisor/            # Security / ReReading / Logging / RedisChatMemory
│   │   ├── agent/              # ToolIntentClassifier / AgentPlanParser（工具意图分类与计划解析）
│   │   ├── config/             # ChatClient, Security, Embedding, Minio 等配置
│   │   ├── controller/         # Chat/Auth/Conversation/Admin/Knowledge/MultiModal API
│   │   ├── entity/             # JPA 实体
│   │   ├── exception/          # 全局异常处理器
│   │   ├── model/              # DTO 和领域模型
│   │   ├── multimodal/         # ASR 与视觉能力
│   │   ├── rag/                # 文档加载、切片、检索
│   │   ├── repository/         # 数据访问层
│   │   ├── security/           # JWT 相关
│   │   ├── service/            # 业务服务（包括 SessionFactCacheService 会话事实缓存）
│   │   └── tool/               # calculateSubsidy / parseFile / webSearch / ToolFailurePolicyCenter
│   ├── src/main/resources/     # application.yml 等配置
│   ├── docker-compose.yml      # PostgreSQL + Redis + MinIO
│   └── pom.xml
├── frontend/
│   └── src/
└── data/
```

## 开发命令

### 后端 (Maven/Spring Boot)

```bash
cd backend

# 启动基础设施
# 推荐：docker compose up -d
# 兼容：docker-compose up -d

# 构建项目
./mvnw clean package

# 运行应用
./mvnw spring-boot:run

# mcp profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=mcp

# 运行测试
./mvnw test
./mvnw test -Dtest=ChatServiceTest
./mvnw test -Dtest=ChatServiceTest#testChatResponse
```

### 前端 (Vite/React)

```bash
cd frontend

npm install
npm run dev
npm run build
npm run lint
npm run preview
```

## 环境变量

- `DASHSCOPE_API_KEY` - 必需，DashScope API 密钥
- `TAVILY_API_KEY` - 可选，联网搜索工具密钥

## API 端点

- `POST /api/chat` - 标准对话
- `POST /api/chat/stream` - 流式对话（SSE）
- `GET /api/chat/health` - 健康检查
- `POST /api/documents/load` - 加载默认文档目录
- `POST /api/documents/load-directory?path=xxx` - 加载指定目录文档
- `DELETE /api/documents?ids=...` - 删除文档向量
- `POST /api/auth/register` - 用户注册
- `POST /api/auth/login` - 用户登录
- `GET /api/auth/me` - 当前用户信息（需 JWT）
- `GET /api/conversations` - 会话列表（需 JWT）
- `GET /api/conversations/{sessionId}` - 获取会话（需 JWT）
- `DELETE /api/conversations/{sessionId}` - 删除会话（需 JWT）
- `POST /api/admin/auth/login` - 管理员登录
- `POST /api/admin/auth/change-password` - 管理员修改密码（需管理员 JWT）
- `GET /api/admin/agent-config` - 获取智能体配置（需管理员 JWT）
- `PUT /api/admin/agent-config` - 更新智能体配置（需管理员 JWT）
- `POST /api/admin/agent-config/reset` - 重置智能体配置（需管理员 JWT）
- `POST /api/admin/agent-config/test` - 配置测试对话（需管理员 JWT）
- `GET /api/admin/knowledge/folders` - 获取知识库目录树（需管理员 JWT）
- `POST /api/admin/knowledge/documents` - 上传知识库文档（需管理员 JWT）
- `GET /api/admin/knowledge/documents/{id}/chunks` - 查询文档切片（需管理员 JWT）
- `POST /api/admin/knowledge/documents/{id}/reingest` - 重新入库文档（需管理员 JWT）
- `POST /api/multimodal/transcribe` - 语音识别
- `POST /api/multimodal/analyze-image` - 图像分析
- `POST /api/multimodal/analyze-invoice` - 发票识别
- `POST /api/multimodal/analyze-device` - 设备识别

## Advisor 执行顺序

`ChatClientConfig` 默认链路：

1. `SecurityAdvisor`（order=10）
2. `MessageChatMemoryAdvisor`
3. `ReReadingAdvisor`（order=50）
4. `QuestionAnswerAdvisor`
5. `LoggingAdvisor`（order=90）

## 可用工具

- `calculateSubsidy` - 补贴金额计算
- `parseFile` - 发票/旧机参数文件解析
- `webSearch` - 联网搜索（价格/新闻/政策动态）

## Agent 相关组件

- **ToolIntentClassifier** - 工具调用前意图分类器，用于降低无效工具调用，在执行工具前进行参数校验
- **AgentPlanParser** - Agent 计划解析器，解析和执行多步计划
- **SessionFactCacheService** - 会话事实缓存服务，将关键事实（价格、地区、设备型号等）结构化写入 Redis 供多轮对话复用
- **ToolFailurePolicyCenter** - 工具失败策略中心，统一管理重试、退避与兜底提示模板

## 配置文件

- `backend/src/main/resources/application.yml` - 主配置
- `backend/pom.xml` - Maven 依赖与构建
- `backend/docker-compose.yml` - 基础设施编排（PostgreSQL/Redis/MinIO）
- `frontend/package.json` - 前端依赖与脚本
- `frontend/vite.config.js` - 前端构建配置

## 端口分配

- 前端开发服务器: `5173`
- 后端 API: `8080`
- PostgreSQL: `5432`
- Redis: `6379`
- MinIO API: `9000`
- MinIO Console: `9001`
