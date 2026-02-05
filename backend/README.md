# 山东省政策咨询智能助手 - 后端服务

## ✅ 部署状态

**当前状态**: 应用已成功启动并运行在 http://localhost:8080

### 运行组件
- ✅ Spring Boot 应用  (Java 21)
- ✅ PostgreSQL + PgVector (端口 5432)
- ✅ Redis (端口 6379)
- ✅ Tomcat 服务器 (端口 8080)

## 📋 快速测试

运行测试脚本查看API状态：

```bash
cd backend
./test-api.sh
```

### 健康检查

```bash
# 应用健康检查
curl http://localhost:8080/api/chat/health

# Actuator 健康检查（包含详细组件状态）
curl http://localhost:8080/actuator/health
```

## 🔑 配置 API 密钥

要使用对话功能，需要配置阿里云 DashScope API Key：

### 方法1: 环境变量（推荐用于开发）

```bash
export DASHSCOPE_API_KEY="your-api-key-here"
./mvnw spring-boot:run
```

### 方法2: 在 application.yml 中配置

编辑 `src/main/resources/application.yml`:

```yaml
spring:
  ai:
    openai:
      api-key: your-api-key-here  # 替换为实际的 API Key
```

## 🧪 API 测试示例

### 1. 标准对话接口

```bash
curl -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "conversationId": "test-001",
    "message": "山东省有哪些以旧换新补贴政策？"
  }'
```

**响应示例**:
```json
{
  "id": "abc-123",
  "conversationId": "test-001",
  "content": "山东省针对以旧换新推出了多项补贴政策...",
  "timestamp": "2026-02-05T16:20:00"
}
```

### 2. 流式对话接口 (SSE)

```bash
curl -X POST http://localhost:8080/api/chat/stream \
  -H 'Content-Type: application/json' \
  -d '{
    "conversationId": "test-001",
    "message": "请详细介绍青岛市的家电以旧换新补贴"
  }'
```

**响应**: 服务器推送事件（Server-Sent Events）流式返回

## 🔧 项目信息

### 技术栈
- Java 21
- Spring Boot 3.4.1
- Spring AI 1.0.0-M5
- PostgreSQL 16 + PgVector
- Redis 7
- Lombok 1.18.42

### 数据库结构
- PostgreSQL: 主数据库，存储政策文档向量
- Redis: 存储对话上下文和会话信息

### 监控端点

Actuator 提供以下监控端点（访问地址: `/actuator/{endpoint}`）:

- `/actuator/health` - 健康检查
- `/actuator/info` - 应用信息
- `/actuator/metrics` - 应用指标

## 📝 开发说明

### 启动服务

```bash
# 1. 启动依赖服务 (PostgreSQL + Redis)
docker compose up -d

# 2. 启动应用
./mvnw spring-boot:run

# 或者带环境变量
export DASHSCOPE_API_KEY="your-api-key"
./mvnw spring-boot:run
```

### 停止服务

```bash
# 停止应用: Ctrl+C

# 停止 Docker 容器
docker compose down

# 停止并删除数据卷
docker compose down -v
```

### 编译项目

```bash
# 清理并编译
./mvnw clean compile

# 运行测试
./mvnw test

# 打包
./mvnw package
```

## 🐛 问题排查

### Q: 应用无法启动，提示 Lombok 相关错误？
A: 确保使用 Java 21 并且 Lombok 版本为 1.18.42 或更高。

### Q: 对话请求卡住或超时？  
A: 检查是否设置了 `DASHSCOPE_API_KEY` 环境变量。

### Q: PostgreSQL 连接失败？
A: 确保 Docker 容器已启动：`docker compose ps`

### Q: Redis 连接失败？
A: 检查 Redis 服务状态：`docker compose logs redis`

## 📚 相关文档

- [Spring AI 文档](https://docs.spring.io/spring-ai/reference/index.html)  
- [阿里云 DashScope API](https://dashscope.aliyun.com/)
- [PgVector 文档](https://github.com/pgvector/pgvector)

## ✨ 功能特性

- ✅ RESTful API 接口
- ✅ 流式响应（SSE）
- ✅ 对话上下文管理
- ✅ 向量数据库（RAG）
- ✅ 健康检查和监控
- ✅ 统一异常处理
- ✅ 请求参数校验

---

**项目状态**: 🚀 已成功启动，可以开始测试！
