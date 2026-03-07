# 阿里云服务器部署指南（宝塔 + Docker）

适用场景（你的当前信息）：
- 服务器公网 IP：`47.76.229.30`（香港）
- 域名：`mmgg.dpdns.org`
- 部署方式：Docker Compose + 宝塔 Nginx 反向代理
- 数据策略：不迁移历史数据（全新部署）
- Ollama 模型：`qwen3-embedding:0.6b`

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

建议确认：
- `APP_SECURITY_CORS_ALLOWED_ORIGIN_PATTERNS=http://mmgg.dpdns.org,https://mmgg.dpdns.org`

## 4. 启动整套容器（含 Ollama）

```bash
cd /opt/MyProject-SDAgent/deploy
docker compose up -d --build
docker compose ps
```

关键说明：
- `ollama-init` 会自动执行 `ollama pull qwen3-embedding:0.6b`
- 前端仅监听 `127.0.0.1:5173`
- 后端仅监听 `127.0.0.1:8080`

## 5. 校验模型是否成功拉取

```bash
docker compose logs ollama-init --tail=200
docker exec -it policy-agent-ollama ollama list
```

若未成功：

```bash
docker exec -it policy-agent-ollama ollama pull qwen3-embedding:0.6b
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
# 容器都应为 Up/healthy
docker compose ps

# 本机健康
curl http://127.0.0.1:8080/actuator/health

# 域名健康（经宝塔反代）
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
docker compose up -d --build

# 重启
docker compose restart backend frontend

# 停止（保留数据卷）
docker compose down
```

注意：
- `docker compose down -v` 会删除数据库、对象存储、Ollama 模型等卷数据。
- 默认管理员账号初始化后请立即改密。
