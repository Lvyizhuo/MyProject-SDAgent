# 阿里云服务器部署指南（宝塔 + Docker）

适用场景（你的当前信息）：
- 服务器公网 IP：`47.76.229.30`（香港）
- 域名：`mmgg.dpdns.org`
- 部署方式：Docker Compose + 宝塔 Nginx 反向代理
- 数据策略：不迁移历史数据（全新部署）
- Ollama 模型：`all-minilm:latest`

## 1. 先做 DNS 与安全组

1) 在 `dpdns.org` 添加 A 记录：
- 主机记录：`mmgg`
- 记录值：`47.76.229.30`

2) 阿里云安全组放行：
- `22/tcp`（SSH）
- `80/tcp`（HTTP）
- `443/tcp`（HTTPS）

说明：容器端口使用 `127.0.0.1` 绑定，不需要对公网放行 `8080/5173/5432/6379/9000/9001/11434`。

## 2. 安装 Docker 与宝塔

```bash
sudo apt update && sudo apt upgrade -y
curl -fsSL https://get.docker.com | sh
sudo systemctl enable docker --now
sudo usermod -aG docker $USER
```

重新登录 SSH 后安装宝塔：

```bash
wget -O install.sh http://download.bt.cn/install/install-ubuntu_6.0.sh && sudo bash install.sh
```

## 3. 拉取代码并配置环境变量

```bash
cd /opt
git clone <你的仓库地址> MyProject-SDAgent
cd /opt/MyProject-SDAgent/deploy
cp .env.example .env
vi .env
```

必须填写：
- `DASHSCOPE_API_KEY`
- `POSTGRES_PASSWORD`
- `MINIO_PASSWORD`
- `APP_JWT_SECRET`（推荐：`openssl rand -base64 48`）
- `APP_MODEL_PROVIDER_ENCRYPTION_SECRET`（启用管理员模型管理时建议配置独立密钥）

建议确认：
- `APP_SECURITY_CORS_ALLOWED_ORIGIN_PATTERNS=http://mmgg.dpdns.org,https://mmgg.dpdns.org`
- 如需平滑轮换模型密钥，可配置 `APP_MODEL_PROVIDER_LEGACY_ENCRYPTION_SECRETS`

## 4. 一键启动整套容器（推荐）

```bash
cd /opt/MyProject-SDAgent/deploy
./deploy.sh
```

关键说明：
- `deploy.sh` 会自动执行 `docker compose up -d --build --remove-orphans`
- `deploy.sh` 会等待 `postgres / redis / minio / ollama / backend / frontend` 全部就绪
- `deploy.sh` 会检查 `all-minilm:latest` 是否已拉取
- `deploy.sh` 会检查 `/actuator/health` 中 `knowledgeMigration=UP`，确认知识库迁移和重入库已完成
- 如果任何一步失败，脚本会自动打印 `ollama-init / backend / frontend` 的关键日志并退出非 0
- `ollama-init` 会自动执行 `ollama pull all-minilm:latest`
- 后端启动后会自动把旧知识库文档迁移到 `ollama:all-minilm` 并重新入库，健康检查会等迁移结束再通过
- 前端仅监听 `127.0.0.1:5173`
- 后端仅监听 `127.0.0.1:8080`
- 若启用了管理员“模型管理”，生产环境应显式配置模型密钥加密主密钥，避免依赖默认回退逻辑

如果你只想看 Compose 状态，可再执行：

```bash
docker compose ps
```

## 5. 如需手工复核（通常不用）

脚本已经自动校验以下项目：
- `ollama-init` 成功退出
- `policy-agent-ollama` 中存在 `all-minilm:latest`
- `http://127.0.0.1:8080/actuator/health` 返回 `status=UP`
- `knowledgeMigration.status=UP`
- `http://127.0.0.1:5173/health` 返回成功

只有在脚本失败时，才建议手工查看：

```bash
docker compose logs ollama-init --tail=200
docker compose logs backend --tail=200
docker compose logs frontend --tail=100
docker exec -it policy-agent-ollama ollama list
```

## 6. 宝塔站点与反向代理

1) 在宝塔新增站点：`mmgg.dpdns.org`  
2) 将站点 Nginx 配置替换为仓库模板：`deploy/bt-site-mmgg.dpdns.org.conf`  
3) 重载 Nginx  

模板核心逻辑：
- `/` -> `http://127.0.0.1:5173`
- `/api/` -> `http://127.0.0.1:8080/api/`（已关闭 buffering，支持 SSE 流式）
- `/actuator/` -> `http://127.0.0.1:8080/actuator/`

## 7. 配置 HTTPS（宝塔）

在站点 SSL 中申请 Let’s Encrypt 证书并开启“强制 HTTPS”。  
完成后再测：

```bash
curl -I https://mmgg.dpdns.org
curl https://mmgg.dpdns.org/actuator/health
```

## 8. 验收清单

```bash
# 如果 deploy.sh 成功返回，这三项应该已经成立
docker compose ps
curl http://127.0.0.1:8080/actuator/health
curl http://mmgg.dpdns.org/actuator/health
```

浏览器访问：
- `http://mmgg.dpdns.org`（或 HTTPS）

## 9. 运维命令

```bash
cd /opt/MyProject-SDAgent/deploy

# 查看日志
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f ollama

# 更新
git pull
./deploy.sh

# 重启
docker compose restart backend frontend

# 停止（保留数据卷）
docker compose down
```

注意：
- `docker compose down -v` 会删除数据库、对象存储、Ollama 模型等卷数据。
- 默认管理员账号初始化后请立即改密。
- 若要确认知识库迁移已完成，可执行 `docker compose logs backend | grep "Knowledge embedding migration"` 查看迁移汇总日志。
