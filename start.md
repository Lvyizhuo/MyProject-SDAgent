# 小白启动指南

> 从零开始，把项目跑起来！跟着步骤一步一步做就行。

---

## 目录

1. [需要安装的软件](#1-需要安装的软件)
2. [克隆项目](#2-克隆项目)
3. [获取 API Key](#3-获取-api-key)
4. [方式一：本地开发部署（推荐新手）](#4-方式一本地开发部署推荐新手)
5. [方式二：Docker 一键部署](#5-方式二docker-一键部署)
6. [打开网页验证](#6-打开网页验证)
7. [导入知识库文档](#7-导入知识库文档)
8. [服务器部署（宝塔 + Docker）](#8-服务器部署宝塔--docker)
9. [常见问题](#9-常见问题)
10. [快速命令速查表](#10-快速命令速查表)

---

## 1. 需要安装的软件

在开始之前，你的电脑上需要装好以下软件。点击链接去官网下载安装包，双击安装，一路"下一步"就行。

| 软件 | 用途 | 下载地址 | 安装后验证命令 |
|------|------|----------|---------------|
| **Git** | 下载项目代码 | https://git-scm.com/downloads | `git --version` |
| **Docker Desktop** | 运行数据库、缓存等基础设施（**两种方式都需要**） | https://www.docker.com/products/docker-desktop/ | `docker --version` |

> 如果你选择**方式一（本地开发）**，还需要额外安装：

| 软件 | 用途 | 下载地址 | 安装后验证命令 |
|------|------|----------|---------------|
| **Java 21 (JDK)** | 运行后端程序 | https://adoptium.net/ 选 JDK 21 | `java -version` |
| **Node.js 20+** | 运行前端程序 | https://nodejs.org/ 选 LTS 版本 | `node -v` |

> 如果你选择**方式二（Docker 一键）**，只需要 Git + Docker Desktop 就够了，后端和前端都在容器里跑。

### 安装注意事项

- **Docker Desktop**：安装后需要**重启电脑**，并确保 Docker Desktop 应用处于运行状态（任务栏/托盘能看到小鲸鱼图标）
- **Java 21**：如果安装后 `java -version` 不是 21，需要配置环境变量 `JAVA_HOME`
- **Node.js**：安装时勾选"Add to PATH"，安装完会自带 `npm`

### Windows 用户额外注意

- 推荐使用 **Windows Terminal** 或 **PowerShell** 执行命令
- 如果某个命令报"不是内部命令"，说明环境变量没配好，重新打开终端再试
- Docker Desktop 安装时如提示启用 WSL2 或 Hyper-V，按提示操作即可
- 如果 BIOS 中虚拟化未开启（Intel VT-x / AMD-V），需要进 BIOS 开启

### macOS 用户额外注意

- Docker Desktop 安装后可能需要在"系统设置 → 隐私与安全性"中允许加载系统扩展
- Apple Silicon (M1/M2/M3) 芯片的 Mac，Docker Desktop 默认就能跑 x86 镜像，不用担心

### Linux 用户额外注意

- Docker 安装后记得执行 `sudo usermod -aG docker $USER`，然后重新登录，这样不用每次加 `sudo`
- Java 和 Node.js 也可以用系统包管理器安装，如 `sudo apt install openjdk-21-jdk nodejs npm`

---

## 2. 克隆项目

打开终端（命令行），执行以下命令把代码下载到本地：

```bash
# 进入你想放项目的目录，比如桌面
cd ~/Desktop

# 克隆项目
git clone https://github.com/你的用户名/MyProject-SDAgent.git

# 进入项目目录
cd MyProject-SDAgent
```

> 如果你是从公司内部 GitLab 或其他平台克隆，把上面的 URL 换成你实际的仓库地址。

---

## 3. 获取 API Key

无论哪种部署方式，你都需要一个**阿里云 DashScope API Key**，它用来驱动 AI 对话和知识库嵌入。

1. 打开 [阿里云百炼平台](https://bailian.console.aliyun.com/)
2. 注册/登录阿里云账号
3. 在左侧菜单找到"API-KEY 管理"，点击"创建 API Key"
4. 复制保存好你的 Key（格式类似 `sk-xxxxxxxx`）

**可选**：如果你需要联网搜索功能，还需获取 [Tavily API Key](https://tavily.com/)，不配置则联网搜索不可用，其他功能正常。

---

## 4. 方式一：本地开发部署（推荐新手）

> 适合：本地开发、学习调试、不熟悉 Docker 的用户
>
> 原理：只用 Docker 跑数据库等基础服务，后端和前端在你电脑上直接运行，方便看日志和改代码

### 4.1 启动基础设施

项目依赖 4 个基础服务：PostgreSQL（数据库）、Redis（缓存）、MinIO（文件存储）、Ollama（本地嵌入模型）。我们已经用 Docker 配好了，一条命令就能全部启动。

```bash
# 进入后端目录
cd backend

# 启动所有基础设施（不含后端本身）
docker compose up -d postgres redis minio ollama ollama-init
```

**这一步做了什么？**
- 下载 4 个 Docker 镜像（首次需要几分钟，取决于网速）
- 启动 4 个容器 + 1 个初始化任务
- `ollama-init` 会自动下载嵌入模型 `nomic-embed-text`（约 274MB），下载完自动退出

**检查是否启动成功：**

```bash
docker compose ps
```

你应该看到类似这样的输出（状态都是 `running` 或 `exited(0)`）：

```
postgres      running   127.0.0.1:5432->5432/tcp
redis         running   127.0.0.1:6379->6379/tcp
minio         running   127.0.0.1:9000->9000/tcp, 127.0.0.1:9001->9001/tcp
ollama        running   127.0.0.1:11434->11434/tcp
ollama-init   exited (0)   ← 这个退出是正常的，它只是一次性初始化
```

> 如果某个容器状态不是 `running`，查看日志：`docker compose logs <服务名>`

### 4.2 配置环境变量

后端启动前需要设置 API Key 等环境变量。

**macOS / Linux（临时生效，关掉终端就没了）：**

```bash
# 必填
export DASHSCOPE_API_KEY=sk-你的真实key

# 可选
export TAVILY_API_KEY=tvly-你的真实key
```

如果想永久生效，把上面两行加到 `~/.zshrc` 或 `~/.bashrc` 末尾，然后执行 `source ~/.zshrc`。

**Windows PowerShell（临时）：**

```powershell
$env:DASHSCOPE_API_KEY="sk-你的真实key"
$env:TAVILY_API_KEY="tvly-你的真实key"    # 可选
```

**Windows CMD（临时）：**

```cmd
set DASHSCOPE_API_KEY=sk-你的真实key
set TAVILY_API_KEY=tvly-你的真实key
```

### 4.3 启动后端

```bash
# 确保你在 backend 目录
cd backend

# 启动后端（首次运行会自动下载 Maven 依赖，需要几分钟）
./mvnw spring-boot:run
```

**Windows 用户请用：**

```cmd
mvnw.cmd spring-boot:run
```

> 如果 `./mvnw` 报权限错误，先执行 `chmod +x mvnw`。如果你本地安装了 Maven，也可以用 `mvn spring-boot:run`。

**等待启动完成**，当你看到类似这样的日志时，说明启动成功：

```
Started PolicyAgentApplication in xx.xx seconds
```

**验证后端是否正常** —— 新开一个终端窗口，执行：

```bash
curl http://localhost:8080/api/chat/health
```

如果返回包含 `"status":"UP"` 的内容，说明后端已经跑起来了。

### 4.4 启动前端

**再新开一个终端窗口**（后端的终端不要关），执行：

```bash
# 进入前端目录
cd frontend

# 安装依赖（首次需要，以后不用重复执行）
npm install

# 启动开发服务器
npm run dev
```

当你看到类似这样的输出时，说明前端已经启动：

```
  ➜  Local:   http://localhost:5173/
```

> 前端会自动把 `/api` 开头的请求代理到后端 `localhost:8080`，所以不需要额外配置。

---

## 5. 方式二：Docker 一键部署

> 适合：想快速把整个项目跑起来、不想装 Java 和 Node.js 的用户
>
> 原理：所有服务（包括后端和前端）都在 Docker 容器中运行，通过 `deploy-windows.sh`（Windows）或 `deploy.sh`（Linux/macOS）脚本一键启动

### 5.1 Windows 用户：使用 deploy-windows.sh

这个脚本自带**交互式配置向导**，你只需要跟着提示输入就行，不需要手动写配置文件。

**第一步：打开 Git Bash**

Windows 用户在项目文件夹上**右键 → Git Bash Here**（Git 安装时会自带 Git Bash）。

> 为什么用 Git Bash？因为它自带 `bash`、`curl`、`openssl` 等工具，脚本能正常运行。PowerShell 和 CMD 不支持 bash 脚本。

**第二步：运行部署脚本**

```bash
# 进入 deploy 目录
cd deploy

# 给脚本执行权限（首次需要）
chmod +x deploy-windows.sh

# 运行！
./deploy-windows.sh
```

**第三步：跟着配置向导填写**

脚本会依次问你以下配置项（每项都有默认值，直接回车可用默认；必填项不能留空）：

| 配置项 | 说明 | 是否必填 |
|--------|------|---------|
| DASHSCOPE_API_KEY | 阿里云大模型 API Key（[获取地址](https://dashscope.console.aliyun.com/apiKey)） | 必填 |
| TAVILY_API_KEY | 联网搜索 Key（[获取地址](https://tavily.com/)） | 可选，直接回车跳过 |
| POSTGRES_PASSWORD | 数据库密码 | 必填（已自动生成强随机值，直接回车即可） |
| MINIO_USER | 文件存储管理员用户名 | 必填（默认 minioadmin） |
| MINIO_PASSWORD | 文件存储管理员密码 | 必填（已自动生成强随机值，直接回车即可） |
| APP_JWT_SECRET | 登录令牌签名密钥 | 必填（已自动生成，直接回车即可） |
| APP_MODEL_PROVIDER_ENCRYPTION_SECRET | 模型管理加密密钥 | 可选（已自动生成，直接回车即可） |
| APP_MODEL_PROVIDER_LEGACY_ENCRYPTION_SECRETS | 历史加密密钥兼容 | 可选，直接回车跳过 |
| APP_SECURITY_CORS_ALLOWED_ORIGIN_PATTERNS | CORS 跨域白名单 | 必填（默认已含 localhost） |
| APP_EMBEDDING_OLLAMA_BASE_URL | Ollama 嵌入服务地址 | 必填（默认 http://ollama:11434，不用改） |
| TZ | 容器时区 | 必填（默认 Asia/Shanghai） |

填写完毕后，脚本会显示配置汇总让你确认。确认后，脚本会自动：

1. 检测 Docker 是否就绪
2. 检测端口是否被占用（5432/6379/9000/9001/11434/8080/5173）
3. 将配置写入 `deploy/.env` 文件
4. 构建并启动所有 7 个容器（postgres / redis / minio / ollama / ollama-init / backend / frontend）
5. 等待每个容器健康就绪
6. 验证后端 API 和前端页面可访问

> 首次部署需要下载镜像 + 构建后端 + 拉取 Ollama 模型，总共可能需要 **10~20 分钟**，请耐心等待。

**看到以下输出说明部署成功：**

```
============================================
  一键部署完成！
============================================

访问地址:
  前端页面:  http://localhost:5173
  后端健康:  http://localhost:8080/actuator/health
  MinIO 控制台: http://localhost:9001
```

> 如果部署失败，脚本会自动打印相关容器的日志，帮助定位问题。修复后重新运行 `./deploy-windows.sh` 即可。

### 5.2 Linux / macOS 用户：使用 deploy.sh

**第一步：准备配置文件**

```bash
# 进入 deploy 目录
cd deploy

# 从模板复制配置文件
cp .env.example .env

# 编辑配置文件，填入你的真实值
vi .env
```

**必须修改的配置项：**

| 变量名 | 说明 | 怎么填 |
|--------|------|--------|
| `DASHSCOPE_API_KEY` | 阿里云大模型 Key | 填入你的真实 Key |
| `POSTGRES_PASSWORD` | 数据库密码 | 用 `openssl rand -base64 48` 生成一个强密码 |
| `MINIO_PASSWORD` | MinIO 管理员密码 | 用 `openssl rand -base64 48` 生成一个强密码 |
| `APP_JWT_SECRET` | JWT 签名密钥 | 用 `openssl rand -base64 48` 生成，**必须是标准 Base64** |

**可选修改：**

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `TAVILY_API_KEY` | 联网搜索 Key | 空（不配置则联网搜索不可用） |
| `MINIO_USER` | MinIO 用户名 | `minioadmin` |
| `APP_MODEL_PROVIDER_ENCRYPTION_SECRET` | 模型管理加密密钥 | 空（不配置会回退使用 JWT 密钥） |
| `APP_SECURITY_CORS_ALLOWED_ORIGIN_PATTERNS` | CORS 域名白名单 | `http://mmgg.dpdns.org,https://mmgg.dpdns.org`（本地用改成 `http://localhost:5173,http://127.0.0.1:5173`） |
| `APP_EMBEDDING_OLLAMA_BASE_URL` | Ollama 地址 | `http://ollama:11434`（容器内默认，不用改） |

> 注意：不要保留占位符如 `replace-with-strong-password`，否则会报错。

**第二步：运行部署脚本**

```bash
# 确保在 deploy 目录
cd deploy

# 给脚本执行权限（首次需要）
chmod +x deploy.sh

# 运行！
./deploy.sh
```

脚本会自动构建并启动所有容器，等待各服务就绪，并做健康验证。看到 `一键部署完成` 即为成功。

---

## 6. 打开网页验证

在浏览器中访问：

```
http://localhost:5173
```

你应该能看到政策咨询助手的聊天界面。

**试试发一条消息**，比如"你好"，看是否能正常回复。

### 访问管理后台

1. 在页面中找到管理员登录入口
2. 管理后台可以：配置智能体、管理知识库、管理模型等

### MinIO 文件管理控制台

```
http://localhost:9001
```

默认账号：`minioadmin`，密码：`minioadmin`（Docker 一键部署方式中密码是你配置的 `MINIO_PASSWORD`）

---

## 7. 导入知识库文档

项目根目录下的 `data/` 文件夹里有一些政策文档（PDF、DOCX）。要让 AI 能回答政策问题，需要把这些文档导入到向量数据库。

**方法一：通过 API 导入（快速）**

```bash
curl -X POST "http://localhost:8080/api/documents/load-directory?path=/app/data"
```

> 本地开发部署方式中，路径使用相对路径：`curl -X POST "http://localhost:8080/api/documents/load-directory?path=../data"`

**方法二：通过管理后台导入**

1. 登录管理后台
2. 进入"知识库"页面
3. 上传 `data/` 目录下的 PDF/DOCX 文件

导入完成后，AI 就能基于这些文档回答问题了。

---

## 8. 服务器部署（宝塔 + Docker）

如果你有一台云服务器（阿里云、腾讯云等），想把项目部署到线上让其他人访问，推荐使用宝塔面板 + Docker Compose。

### 8.1 服务器环境准备

```bash
# 更新系统
sudo apt update && sudo apt upgrade -y

# 安装 Docker
curl -fsSL https://get.docker.com | sh
sudo systemctl enable docker --now
sudo usermod -aG docker $USER

# 重新登录 SSH 后安装宝塔面板
wget -O install.sh http://download.bt.cn/install/install-ubuntu_6.0.sh && sudo bash install.sh
```

### 8.2 安全组/防火墙放行

在云服务商控制台放行以下端口：
- `22/tcp`（SSH）
- `80/tcp`（HTTP）
- `443/tcp`（HTTPS）

> Docker 容器端口已绑定 `127.0.0.1`，**不需要**对公网放行 8080/5173/5432 等端口。

### 8.3 克隆代码并配置

```bash
# 克隆项目
cd /opt
git clone https://github.com/你的用户名/MyProject-SDAgent.git
cd MyProject-SDAgent/deploy

# 从模板复制配置文件
cp .env.example .env
vi .env
```

**必须填写的配置：**

| 变量名 | 怎么填 |
|--------|--------|
| `DASHSCOPE_API_KEY` | 你的真实 Key |
| `POSTGRES_PASSWORD` | `openssl rand -base64 48` 生成 |
| `MINIO_PASSWORD` | `openssl rand -base64 48` 生成 |
| `APP_JWT_SECRET` | `openssl rand -base64 48` 生成（必须是标准 Base64） |
| `APP_SECURITY_CORS_ALLOWED_ORIGIN_PATTERNS` | 你的域名，如 `http://example.com,https://example.com` |

### 8.4 一键部署

```bash
cd /opt/MyProject-SDAgent/deploy
./deploy.sh
```

脚本会自动构建和启动所有容器，并等待服务就绪。首次部署可能需要 10~20 分钟。

### 8.5 配置宝塔 Nginx 反向代理

1. 在宝塔面板创建站点，域名填你的域名
2. 将站点 Nginx 配置替换为仓库中的 `deploy/bt-site-mmgg.dpdns.org.conf` 模板
3. 或者在宝塔"自定义配置"中添加后端路由：

```nginx
location /api/ {
    proxy_pass http://127.0.0.1:8080/api/;
    proxy_http_version 1.1;
    proxy_set_header Connection "";
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_buffering off;
    proxy_cache off;
    proxy_read_timeout 600s;
    proxy_send_timeout 600s;
}

location /actuator/ {
    proxy_pass http://127.0.0.1:8080/actuator/;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
}
```

4. 重载 Nginx：`nginx -t && nginx -s reload`

### 8.6 配置 HTTPS

在宝塔站点 SSL 中申请 Let's Encrypt 证书并开启"强制 HTTPS"。

### 8.7 验证

```bash
curl -f http://你的域名/health
curl -f http://你的域名/api/chat/health
```

### 8.8 日常更新

```bash
cd /opt/MyProject-SDAgent
git pull
cd deploy
./deploy.sh
```

---

## 9. 常见问题

### Q: Docker 镜像下载太慢怎么办？

配置 Docker 镜像加速器。打开 Docker Desktop → Settings → Docker Engine，在 JSON 中加入：

```json
{
  "registry-mirrors": ["https://你的镜像加速地址"]
}
```

国内常用的镜像加速器可以在网上搜索"Docker 镜像加速"获取最新可用地址。

### Q: Maven 依赖下载太慢怎么办？（方式一）

在 `backend/` 目录下创建 `settings.xml` 或修改 Maven 全局配置，添加国内镜像源（如阿里云 Maven 镜像）。

### Q: npm install 太慢怎么办？（方式一）

```bash
# 使用淘宝镜像
npm config set registry https://registry.npmmirror.com
```

### Q: 启动后端报数据库连接错误（方式一）

确认 PostgreSQL 容器在运行：

```bash
cd backend && docker compose ps postgres
```

如果没运行：`docker compose up -d postgres`

### Q: 启动后端报 Redis 连接错误（方式一）

确认 Redis 容器在运行：

```bash
cd backend && docker compose ps redis
```

如果没运行：`docker compose up -d redis`

### Q: 端口被占用

**方式一**：如果 8080 端口被其他程序占用，后端会报错。两种解法：
1. 找到并关掉占用端口的程序
2. 在 `application.yml` 中修改端口：`server.port: 8081`

同理，前端 5173 端口被占用时，Vite 会自动尝试 5174、5175……

**方式二**：`deploy-windows.sh` 会自动检测端口冲突并提示你处理。Windows 上可用 PowerShell 查找占用进程：

```powershell
netstat -ano | findstr :8080
```

### Q: Ollama 模型下载失败

`ollama-init` 容器负责下载嵌入模型，如果网络不好可能失败。手动下载：

```bash
docker exec -it policy-agent-ollama ollama pull nomic-embed-text
```

### Q: 前端页面打开了但聊天没反应

1. 检查后端是否正常运行：`curl http://localhost:8080/api/chat/health`
2. 检查 `DASHSCOPE_API_KEY` 是否设置正确
3. 查看后端终端日志或 `docker compose logs backend` 有没有报错

### Q: deploy-windows.sh 报 Docker Desktop 未运行

1. 启动 Docker Desktop（开始菜单搜索 Docker Desktop）
2. 等待左下角状态变为 "Engine running"
3. 重新运行脚本

### Q: 管理员登录时报 `Illegal base64 character`

检查 `.env` 中 `APP_JWT_SECRET` 是否仍是占位符或非标准 Base64。修正后重新部署：

```bash
cd deploy
./deploy-windows.sh   # Windows
./deploy.sh           # Linux/macOS
```

### Q: 怎么停止所有服务？

**方式一（本地开发）：**

- 前端：在终端按 `Ctrl + C`
- 后端：在终端按 `Ctrl + C`
- Docker 基础设施：

```bash
cd backend
docker compose down
```

**方式二（Docker 一键）：**

```bash
cd deploy
docker compose down
```

> `docker compose down` 只停止容器，不会删除数据。下次启动用 `docker compose up -d` 或重新运行部署脚本即可。

### Q: 怎么完全清除数据重新开始？

```bash
# 方式一
cd backend && docker compose down -v

# 方式二
cd deploy && docker compose down -v
```

> `-v` 会删除所有数据卷（数据库、文件存储、Ollama 模型等），慎用！

---

## 10. 快速命令速查表

### 方式一：本地开发部署

```bash
# === 基础设施 ===
cd backend
docker compose up -d postgres redis minio ollama ollama-init   # 启动
docker compose ps                                                # 查看状态
docker compose logs -f <服务名>                                  # 查看日志
docker compose down                                              # 停止（保留数据）

# === 环境变量 ===
export DASHSCOPE_API_KEY=sk-你的key       # 必填
export TAVILY_API_KEY=tvly-你的key         # 可选

# === 后端 ===
cd backend
./mvnw spring-boot:run                                          # 启动

# === 前端 ===
cd frontend
npm install                                                      # 安装依赖（首次）
npm run dev                                                      # 启动开发服务器

# === 导入知识库 ===
curl -X POST "http://localhost:8080/api/documents/load-directory?path=../data"

# === 健康检查 ===
curl http://localhost:8080/api/chat/health                       # 后端健康
```

### 方式二：Docker 一键部署

```bash
# === Windows（在 Git Bash 中运行）===
cd deploy
chmod +x deploy-windows.sh     # 首次加执行权限
./deploy-windows.sh            # 一键部署（含交互式配置向导）

# === Linux / macOS ===
cd deploy
cp .env.example .env           # 复制配置模板
vi .env                        # 编辑配置（填写必填项）
chmod +x deploy.sh             # 首次加执行权限
./deploy.sh                    # 一键部署

# === 常用运维 ===
cd deploy
docker compose ps                                               # 查看容器状态
docker compose logs -f backend                                  # 查看后端日志
docker compose logs -f frontend                                 # 查看前端日志
docker compose down                                             # 停止（保留数据）
docker compose down -v                                          # 停止并清除数据
```

---

## 端口一览

| 端口 | 服务 | 说明 |
|------|------|------|
| 5173 | 前端 | 浏览器访问的网页 |
| 8080 | 后端 API | 前端通过代理调用 |
| 5432 | PostgreSQL | 数据库 |
| 6379 | Redis | 缓存 |
| 9000 | MinIO API | 文件存储接口 |
| 9001 | MinIO Console | 文件管理网页 |
| 11434 | Ollama | 本地嵌入模型 |

---

## 两种方式对比

| | 方式一：本地开发 | 方式二：Docker 一键 |
|---|---|---|
| **需要安装** | Git + Docker Desktop + Java 21 + Node.js | Git + Docker Desktop |
| **后端/前端运行在** | 你的电脑上（方便调试） | Docker 容器内（隔离干净） |
| **配置方式** | 手动 export 环境变量 | 交互式向导（Windows）或编辑 .env（Linux） |
| **启动命令数** | 多步（基础设施 → 变量 → 后端 → 前端） | 一条命令 |
| **适合场景** | 开发调试、改代码、看日志 | 快速体验、不想装 Java/Node |
| **日志查看** | 直接看终端输出 | `docker compose logs -f backend` |
| **停止服务** | 分别按 Ctrl+C + docker compose down | `docker compose down` |
