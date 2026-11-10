#!/usr/bin/env bash
# ===========================================
# Compose 封装：统一调用方式 + 项目名隔离
#
# 两套部署方式通过 COMPOSE_PROJECT_NAME 彻底隔离：
#   - 一键部署：policy-agent          （容器前缀 policy-agent-）
#   - 本地开发：policy-agent-dev      （容器前缀 policy-agent-dev-）
# 容器名、网络、数据卷、配置全部互不覆盖。
# ===========================================

# compose_cmd <子命令> <项目名> <compose文件> <env文件> [额外参数...]
#
# 例：compose_cmd up -d policy-agent deploy/docker-compose.yml deploy/.env postgres redis
#
# 设计要点：前 4 个参数位置固定，额外参数一律追加在子命令之后，
# 避免"服务名被当成 --env-file 的值"这类参数错位。
compose_cmd() {
  local subcommand="$1"; shift
  local project_name="$1"; shift
  local compose_file="$1"; shift
  local env_file="$1"; shift

  if [[ "$(detect_platform)" == "windows" ]]; then
    MSYS_NO_PATHCONV=1 docker compose \
      --project-name "$project_name" \
      --env-file "$env_file" \
      -f "$compose_file" \
      "$subcommand" "$@"
  else
    docker compose \
      --project-name "$project_name" \
      --env-file "$env_file" \
      -f "$compose_file" \
      "$subcommand" "$@"
  fi
}

# compose_prefix <项目名> <compose文件> <env文件>
# 用于 dump_failure_context 这类需要把 compose 前缀整体展开的场景
compose_prefix() {
  local project_name="$1" compose_file="$2" env_file="$3"
  if [[ "$(detect_platform)" == "windows" ]]; then
    echo "MSYS_NO_PATHCONV=1 docker compose --project-name $project_name --env-file $env_file -f $compose_file"
  else
    echo "docker compose --project-name $project_name --env-file $env_file -f $compose_file"
  fi
}

# compose_service_container <项目名> <服务名> → 容器名
# 容器名规则：<project>-<service>-1（Compose 默认命名，不使用 container_name 固定名）
compose_service_container() {
  local project_name="$1" service="$2"
  echo "${project_name}-${service}-1"
}
