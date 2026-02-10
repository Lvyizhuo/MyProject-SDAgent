# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

山东省智能政策咨询助手是一个基于AI的大语言模型驱动的智能问答系统，专为山东省以旧换新补贴政策提供咨询服务，采用RAG（检索增强生成）技术实现精准政策解答。

## 技术栈

- **前端**: React 19 + Vite 7 (JavaScript/JSX)
- **后端**: Spring Boot 3.4 + Spring AI 1.0.0-M5 (Java 21)
- **AI模型**: 阿里云DashScope（通义千问qwen3-max用于聊天，text-embedding-v3用于嵌入）
- **向量数据库**: PostgreSQL 16 + pgvector
- **会话存储**: Redis 7
- **身份验证**: JWT-based 安全认证

## 项目结构

```
├── backend/                    # 后端服务
│   ├── src/main/java/com/shandong/policyagent/
│   │   ├── advisor/            # Spring AI Advisors (安全、内存、日志)
│   │   ├── config/             # Spring 配置
│   │   ├── controller/         # REST API 控制器
│   │   ├── entity/             # JPA 实体
│   │   ├── exception/          # 全局异常处理器
│   │   ├── model/              # DTOs 和领域模型
│   │   ├── multimodal/         # ASR 和视觉服务
│   │   ├── rag/                # RAG 服务（文档加载、分块、检索）
│   │   ├── repository/         # 数据访问层
│   │   ├── security/           # JWT 认证
│   │   ├── service/            # 业务逻辑服务
│   │   └── tool/               # LLM可调用工具
│   ├── src/main/resources/     # 配置文件
│   ├── docker-compose.yml      # 基础设施编排
│   └── pom.xml                 # Maven 依赖
├── frontend/                   # 前端应用
│   └── src/                    # React 源文件
└── data/                       # 政策文档数据
```

## 开发命令

### 后端 (Maven/Spring Boot)

```bash
cd backend

# 启动基础设施 (PostgreSQL + Redis)
docker-compose up -d

# 构建项目
./mvnw clean package

# 运行应用 (需要 DASHSCOPE_API_KEY 环境变量)
./mvnw spring-boot:run

# 运行测试
./mvnw test

# 运行特定测试类
./mvnw test -Dtest=ChatServiceTest

# 运行特定测试方法
./mvnw test -Dtest=ChatServiceTest#testChatResponse
```

### 前端 (Vite/React)

```bash
cd frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 构建生产版本
npm run build

# 代码检查
npm run lint

# 预览生产构建
npm run preview
```

### 基础设施 (Docker)

```bash
cd backend

# 启动 PostgreSQL (pgvector) + Redis
docker-compose up -d

# 停止服务
docker-compose down

# 停止并删除数据卷
docker-compose down -v
```

## 环境变量

- `DASHSCOPE_API_KEY` - 阿里云DashScope API密钥，用于通义千问模型

## API 端点

- `POST /api/chat` - 标准对话（完整响应）
- `POST /api/chat/stream` - 流式对话（SSE）
- `GET /api/chat/health` - 健康检查
- `POST /api/documents/load-directory?path=xxx` - 从目录加载文档
- `POST /api/auth/login` - 认证端点
- `POST /api/auth/register` - 用户注册

## Advisor 执行顺序

Advisors按优先级值顺序执行：

| Advisor | 顺序 | 功能 |
|---------|------|------|
| SecurityAdvisor | 10 | 敏感词过滤，防止提示注入 |
| ReReadingAdvisor | 50 | 强制模型验证答案准确性 |
| RedisChatMemory | 100 | 多轮对话上下文 |
| QuestionAnswerAdvisor | - | RAG检索增强 |
| LoggingAdvisor | 90 | 令牌使用情况、响应延迟、来源引用 |

## 可用工具

- `calculateSubsidy` - 计算山东省以旧换新补贴金额（家电/手机/平板/智能手表等）
- `webSearchTool` - 网络搜索功能
- `fileParserTool` - 解析各种文档格式

## 配置文件

- `backend/src/main/resources/application.yml` - 主Spring Boot配置
- `backend/pom.xml` - Maven依赖和构建配置
- `backend/docker-compose.yml` - PostgreSQL + Redis编排
- `frontend/package.json` - 前端依赖和脚本
- `frontend/vite.config.js` - Vite构建配置

## 代码风格指南

### Java (后端)
- 类名: PascalCase (`ChatController`, `ChatService`)
- 方法/变量: camelCase (`chatStream`, `conversationId`)
- 常量: UPPER_SNAKE_CASE (`MAX_RETRIES`)
- 使用构造函数注入和`@RequiredArgsConstructor`配合`final`字段
- Lombok注解减少样板代码（`@Data`, `@Builder`, `@Slf4j`）
- 使用`@Valid`注解验证请求参数
- 使用Slf4j进行日志记录

### JavaScript/React (前端)
- 组件名称: PascalCase (`ChatWindow.jsx`)
- Hook/函数: camelCase (`useState`, `handleSend`)
- CSS类: kebab-case (`message-row`, `input-field`)
- 事件处理程序以`handle`为前缀 (`handleSend`, `handleSubmit`)
- 在`variables.css`中使用HSL格式的设计标记: `--color-primary: 215 90% 35%`

## 端口分配

- 前端开发服务器: 5173
- 后端API: 8080
- PostgreSQL: 5432
- Redis: 6379