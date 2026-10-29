# ===========================================
# 山东省智能政策咨询助手 - 后端 Dockerfile
# ===========================================

# 构建阶段
FROM maven:3.9-eclipse-temurin-26-alpine AS builder

WORKDIR /app

# 复制 Maven 配置（阿里云镜像源，解决国内网络访问 Maven Central 超时）
COPY maven-settings.xml /root/.m2/settings.xml

# 复制 pom.xml 并下载依赖（利用缓存）
# 重要：COPY pom.xml 之后 mvn dependency:go-offline 会把 pom 里所有
# <dependency> 都下载到本地仓库，包括 lombok 1.18.46。改 lombok 版本号
# 之后必须重新 build（docker build --no-cache）才能让缓存失效。
COPY pom.xml .
RUN mvn -B \
    -Dmaven.wagon.http.retryHandler.count=5 \
    -Dmaven.wagon.http.retryHandler.requestSentEnabled=true \
    dependency:go-offline

# 复制源码并构建
COPY src ./src
COPY lombok.config ./
# 强制清理任何残留的 target 目录，避免增量构建导致 stale class 文件。
# 重点：避免 Lombok 命名变化后的旧 class 干扰编译结果。
RUN rm -rf target && mvn clean package -DskipTests -B -Dmaven.compiler.useIncrementalCompilation=false

# 运行阶段
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# MCP stdio servers and PDF OCR fallback require these runtime tools.
RUN apk add --no-cache nodejs npm poppler-utils

# 创建非 root 用户
RUN addgroup -g 1000 appgroup && adduser -u 1000 -G appgroup -s /bin/sh -D appuser

# 复制构建产物
COPY --from=builder /app/target/*.jar app.jar

# 创建数据目录
RUN mkdir -p /app/data && chown -R appuser:appgroup /app

# 切换到非 root 用户
USER appuser

# 暴露端口
EXPOSE 8080

# 健康检查（给 Flyway V9 迁移 + knowledge 迁移 + seed 留 120s 启动窗口）
HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=10 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# 启动应用
ENTRYPOINT ["java", "-jar", "-Xms512m", "-Xmx1024m", "app.jar"]
