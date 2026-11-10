# 部署与本地开发指南

本项目只有 **两套** 启动方式，二者完全独立、可同时运行。

| | 方式一：服务器 / 新机器一键部署 | 方式二：本地开发 |
|---|---|---|
| **入口** | `./scripts/deploy.sh` | `./scripts/dev.sh` |
| **编排文件** | `deploy/docker-compose.yml` | `backend/docker-compose.yml` |
| **配置文件** | `deploy/.env` | `backend/.env.local` |
| **Compose 项目名** | `policy-agent` | `policy-agent-dev` |
| **后端运行位置** | 容器内（打包运行） | 宿主机（`spring-boot:run` 热重载） |
| **前端运行位置** | 容器内（Nginx） | 宿主机（Vite HMR） |
| **中间件** | 容器内 | 容器内 |
| **目标环境** | Linux / macOS / Windows(Git Bash) | macOS / Linux / Windows(Git Bash) |

---

## 方式一：服务器 / 新机器一键部署

### 快速开始

```bash
# 全新机器上唯一需要的命令
./scripts/deploy.sh up
```

脚本会自动完成：检测并安装 Docker → 生成配置 → 构建前后端镜像 → 启动全部容器 → 等待就绪 → 健康校验。

### 首次使用建议分两步（便于先确认配置）

```bash
./scripts/deploy.sh init    # 交互式生成 deploy/.env（密钥自动生成）
vi deploy/.env              # 按需修改域名等
./scripts/deploy.sh up      # 执行部署
```

### 命令一览

| 命令 | 说明 |
|------|------|
| `up` | 一键部署（默认）：装依赖 → 校验配置 → 构建 → 启动 → 健康检查 |
| `init` | 交互式生成 `deploy/.env` |
| `restart [svc...]` | 重启服务（默认 backend + frontend） |
| `status` | 容器状态 + 健康检查 |
| `logs [svc]` | 跟踪日志（默认 backend；`svc=all` 看全部容器） |
| `logfiles` | 打印容器内按天归档的日志文件位置 |
| `down` | 停止全部服务（数据卷保留） |
| `help` | 帮助 |

### 日志

所有自研组件（后端、重排服务、前端 nginx）的日志**统一格式、统一命名、统一保存在容器内的 /app/logs**：

| 组件 | 活动文件（容器内） | 说明 |
|------|------|------|
| 后端 | `/app/logs/backend-<日期>.log` | 超过 100MB 切分为 `.1.log` / `.2.log` |
| 重排服务 | `/app/logs/reranker-<日期>.log` | 同上 |
| 前端 | `/app/logs/frontend-<日期>.log` | 另有 `frontend-error.log`（nginx 错误日志） |

- 命名：`<组件名>-<YYYY-MM-DD>.log`
- **不压缩**，方便直接 `tail` / `grep` 查看排查
- 保留 30 天（容器启动时按 mtime 清理）
- 日期切换：后端/重排服务靠进程重启（重启即用新日期）；前端靠每日 00:05 的 nginx reopen

查看方式：

```bash
./scripts/deploy.sh logs backend      # 容器 stdout
./scripts/deploy.sh logs all          # 全部容器 stdout
./scripts/deploy.sh logfiles          # 打印容器内日志文件位置（含当天文件名）
docker exec policy-agent-backend-1 tail -f /app/logs/backend-$(date +%F).log
```

> 注意：日志写在容器可写层，**容器重建后会被清空**。需要跨重建保留请把 `/app/logs` 挂成数据卷。
> 完整规范见 [`../docs/notes/日志规范.md`](../docs/notes/日志规范.md)。

### 启动顺序

```
1. ensure_docker        检测 Docker + Compose 插件，缺失则自动安装
                          ├ Linux : get.docker.com 官方脚本
                          ├ macOS : brew install --cask docker
                          └ Windows: 提示手动装 Docker Desktop
2. ensure_env           校验 deploy/.env 必填项（缺失则引导执行 init）
3. compose config       静态校验 compose 配置
4. compose build        构建 backend / frontend / reranker 镜像
                          （默认 --no-cache，规避 lombok 升级后的 stale class 问题）
5. compose up -d        启动全部容器
6. 等待就绪（按依赖顺序）
     postgres → redis → minio → ollama → ollama-init(拉模型)
     → reranker(下模型) → backend → frontend
7. 功能校验
     ollama 模型 / 后端 status=UP + knowledgeMigration=UP / 前端 /health
```

### 配置

配置文件 `deploy/.env`，模板 `deploy/.env.example`（由 `deploy.sh init` 渲染）。

**必填**

| 变量 | 说明 |
|------|------|
| `DASHSCOPE_API_KEY` | 阿里云百炼 Key，驱动对话与嵌入 |
| `POSTGRES_PASSWORD` | 数据库密码 |
| `MINIO_USER` / `MINIO_PASSWORD` | MinIO 账号密码 |
| `APP_JWT_SECRET` | JWT 签名密钥（Base64） |

**常用可选**

| 变量 | 说明 |
|------|------|
| `TAVILY_API_KEY` | 联网搜索，留空则该功能不可用 |
| `AMAP_MAPS_API_KEY` | 高德地图 MCP，留空则地图功能不可用 |
| `APP_SECURITY_CORS_ALLOWED_ORIGIN_PATTERNS` | 正式域名（逗号分隔），用于 CORS 放行 |
| `APP_MODEL_PROVIDER_ENCRYPTION_SECRET` | 模型管理 API Key 加密主密钥 |
| `POSTGRES_HOST_PORT` 等 | 宿主机端口映射，默认见 `.env.example` 注释 |

**临时覆盖**（优先于 `.env`，适合一次性测试）

```bash
DEPLOY_DASHSCOPE_API_KEY=xxx \
DEPLOY_CORS_ORIGINS=https://example.com \
./scripts/deploy.sh up
```

**超时调整**（新机器首次拉模型较慢）

```bash
DEPLOY_RERANKER_TIMEOUT_SEC=1800 \
DEPLOY_BACKEND_TIMEOUT_SEC=2400 \
./scripts/deploy.sh up
```

### 反向代理

容器端口只绑定 `127.0.0.1`，需由本机 Nginx（宝塔）反代：

- `/` → `127.0.0.1:5173`（前端）
- `/api/` → `127.0.0.1:8080`（后端）

详细步骤见同目录 `DEPLOY.md`。

---

## 方式二：本地开发

### 快速开始

```bash
./scripts/dev.sh
```

启动中间件容器 + 后端（`spring-boot:run`）+ 前端（Vite），均带热重载。

### 前后端独立启停

```bash
./scripts/dev.sh start backend     # 只启动后端
./scripts/dev.sh start frontend    # 只启动前端
./scripts/dev.sh stop backend      # 只停后端，前端继续跑
./scripts/dev.sh stop frontend     # 只停前端，后端继续跑
./scripts/dev.sh stop              # 停掉全部（前后端 + 中间件）
```

### 命令一览

| 命令 | 说明 |
|------|------|
| `start [all\|backend\|frontend]` | 启动，默认全部 |
| `stop [all\|backend\|frontend]` | 停止，默认全部 |
| `infra` | 仅启动中间件容器 |
| `infra-stop` | 仅停止中间件容器 |
| `status` | 容器与进程状态 |
| `logs [backend\|frontend]` | 查看日志 |
| `help` | 帮助 |

### 启动顺序

```
1. ensure_tools      检测 docker / java / pnpm / curl
2. cmd_infra         启动中间件容器并等待就绪
                      postgres → redis → minio → ollama → ollama-init → reranker
3. start_backend     ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,mcp
                      等待 /actuator/health 返回 200
4. start_frontend    pnpm dev（依赖缺失时自动 pnpm install）
                      等待 5173 端口可访问
```

### 独立启动依赖

单独启动某一端时，另一端的依赖需要已在运行：

- `start backend` 需要中间件已就绪 → 先 `./scripts/dev.sh infra`
- `start frontend` 不依赖后端（Vite 代理会在后端就绪后自动生效）

用 IDE 跑后端时，也可以只起中间件：`./scripts/dev.sh infra`。

### 配置

配置文件 `backend/.env.local`，同时服务于宿主机后端与开发用中间件容器。

| 变量 | 说明 |
|------|------|
| `DASHSCOPE_API_KEY` / `TAVILY_API_KEY` / `APP_JWT_SECRET` | 同方式一 |
| `POSTGRES_PASSWORD` / `MINIO_USER` / `MINIO_PASSWORD` | 中间件容器凭据 |
| `DEV_POSTGRES_PORT` 等 | 开发专用端口段（15433/16379/19000/19001/21434/18989） |
| `SPRING_DATASOURCE_URL` 等 | 指向 `localhost:<开发端口>` |

### 日志

| 文件 | 内容 |
|------|------|
| `logs/backend.out` | 后端进程 stdout（Maven Wrapper 输出，含构建日志） |
| `logs/backend-<日期>.log` | 后端应用日志（Logback 写入，按天一个文件，不压缩） |
| `logs/backend-<日期>.<序号>.log` | 后端日志超过 100MB 后的容量切分片 |
| `logs/frontend.log` | 前端进程 stdout（Vite 输出） |

保留 30 天（启动时自动清理过期文件）。

> 完整的日志规范（格式、目录、命名、轮转策略、例外说明）见 [`../docs/notes/日志规范.md`](../docs/notes/日志规范.md)。

---

## 两套方式如何互不影响

四重隔离，同一台机器上可同时运行：

| 隔离维度 | 方式一 | 方式二 |
|---------|--------|--------|
| **Compose 项目名** | `policy-agent` | `policy-agent-dev` |
| **容器名** | `policy-agent-<svc>-1` | `policy-agent-dev-<svc>-1` |
| **宿主机端口** | 5433 / 6379 / 9000 / 9001 / 11434 / 8989 / 8080 / 5173 | 15433 / 16379 / 19000 / 19001 / 21434 / 18989 |
| **数据卷** | `policy-agent_*` | `policy-agent-dev_*` |
| **配置文件** | `deploy/.env` | `backend/.env.local` |

因此：

- 停方式一不会影响方式二（`docker compose down` 各自的项目命名空间）
- 两套数据库数据完全独立，方式二随便造脏数据不会污染部署环境
- 端口不重叠，可以一边跑部署实例、一边写代码

> 唯一共享的是宿主机上的 **8080 / 5173**（方式二的后端与前端开发服务器）。
> 若要同时运行两套，需把方式二的后端/前端改到其他端口：
> `DEPLOY_BACKEND_PORT=8081 DEPLOY_FRONTEND_PORT=5174 ./scripts/dev.sh`

---

## 常见问题

**Q：`up` 报 Docker 未安装**
脚本会自动安装（Linux/macOS）。Windows 上需手动装 Docker Desktop，
装完启动它、确认 "Engine running" 再重跑。

**Q：首次部署卡在 Reranker**
首次需从 HuggingFace 下载约 568MB 模型，国内网络可能很慢。
可调大超时：`DEPLOY_RERANKER_TIMEOUT_SEC=1800`。
脚本已正确处理 `HF_ENDPOINT` —— 不要设置 `hf-mirror.com`，
它返回 308 重定向会导致下载永远失败。

**Q：首次部署卡在 Ollama**
同理，模型约 1.2GB。可调大 `DEPLOY_OLLAMA_INIT_TIMEOUT_SEC`。

**Q：端口被占用**
方式一：`ss -lntp | grep 8080` 查占用，或改 `deploy/.env` 里的 `*_HOST_PORT`。
方式二：中间件用 15xxx/16xxx/19xxx 段，一般不会冲突。

**Q：本地开发连不上数据库**
确认 `backend/.env.local` 的端口与 `backend/docker-compose.yml` 一致，
且 `POSTGRES_PASSWORD` 与 `SPRING_DATASOURCE_PASSWORD` 相同。

**Q：忘了 .env 里的密钥**
`./scripts/deploy.sh init` 重新生成。注意这会重置数据库密码，
已有数据卷里的密码不会跟着变，需要手工处理。
