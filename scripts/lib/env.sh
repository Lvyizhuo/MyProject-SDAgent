#!/usr/bin/env bash
# ===========================================
# 环境变量库：加载、校验、模板渲染
# ===========================================

# env_load <env文件> <compose文件> [extra env文件...]
# 用法：env_load deploy/.env deploy/docker-compose.yml
# 读取顺序：extra 文件（可选） → env 文件 → compose 文件的 env_file 声明
# 优先级：后加载的覆盖先加载的
env_load() {
  local env_file="$1"; shift
  local compose_file="$1"; shift
  local extra

  for extra in "$@"; do
    if [[ -f "$extra" ]]; then
      set -a
      # shellcheck disable=SC1090
      source "$extra"
      set +a
    fi
  done

  if [[ ! -f "$env_file" ]]; then
    log_error "未找到环境变量文件: $env_file"
    return 1
  fi

  set -a
  # shellcheck disable=SC1090
  source "$env_file"
  set +a
  return 0
}

# env_require <变量名...>  —— 缺失则退出
env_require() {
  local missing=()
  local key
  for key in "$@"; do
    if [[ -z "${!key:-}" ]]; then
      missing+=("$key")
    fi
  done
  if (( ${#missing[@]} > 0 )); then
    log_error "以下环境变量未配置: ${missing[*]}"
    return 1
  fi
  return 0
}

# env_has_placeholder <变量名>
# 检测变量是否仍是模板占位符（replace-with-... / your-...）
env_has_placeholder() {
  local key="$1"
  local value="${!key:-}"
  [[ -z "$value" ]] && return 1
  [[ "$value" == replace-with-* ]] && return 0
  [[ "$value" == your-* ]] && return 0
  [[ "$value" == change-me* ]] && return 0
  return 1
}

# env_require_real <变量名...> —— 既要非空，又不能是占位符
env_require_real() {
  local missing=() placeholder=()
  local key
  for key in "$@"; do
    if [[ -z "${!key:-}" ]]; then
      missing+=("$key")
    elif env_has_placeholder "$key"; then
      placeholder+=("$key")
    fi
  done
  if (( ${#missing[@]} > 0 )); then
    log_error "以下环境变量未配置: ${missing[*]}"
    return 1
  fi
  if (( ${#placeholder[@]} > 0 )); then
    log_error "以下环境变量仍是模板占位符，请改成真实值: ${placeholder[*]}"
    return 1
  fi
  return 0
}

# ---------- 模板渲染 ----------

# random_secret [字节数] —— 生成 Base64 随机密钥（优先 openssl，回退 urandom）
random_secret() {
  local bytes="${1:-48}"
  if has_cmd openssl; then
    openssl rand -base64 "$bytes" | tr -d '\n'
  else
    head -c "$bytes" /dev/urandom 2>/dev/null | base64 | tr -d '\n' || \
      echo "change-me-$(date +%s)-$RANDOM$RANDOM"
  fi
}

# render_env_template <模板文件> <输出文件> [KEY=VALUE ...]
# 逐行复制模板；遇到 "KEY=" 且该 KEY 在传入列表中，则用传入值替换；
# 否则保留模板原值。传入的 KEY 不在模板中时追加到文件末尾。
render_env_template() {
  local template="$1" output="$2"; shift 2
  local -A overrides=()
  local pair key value

  for pair in "$@"; do
    key="${pair%%=*}"
    value="${pair#*=}"
    overrides["$key"]="$value"
  done

  local tmp
  tmp="$(mktemp)"
  cp "$template" "$tmp"

  local line
  while IFS= read -r line || [[ -n "$line" ]]; do
    if [[ "$line" =~ ^([A-Z_][A-Z0-9_]*)= ]]; then
      key="${BASH_REMATCH[1]}"
      if [[ -n "${overrides[$key]+x}" ]]; then
        printf '%s=%s\n' "$key" "${overrides[$key]}" >> "$tmp"
        unset "overrides[$key]"
        continue
      fi
    fi
    printf '%s\n' "$line" >> "$tmp"
  done < "$template"

  # 模板中没有的键追加到末尾
  for key in "${!overrides[@]}"; do
    printf '%s=%s\n' "$key" "${overrides[$key]}" >> "$tmp"
  done

  mv "$tmp" "$output"
}
