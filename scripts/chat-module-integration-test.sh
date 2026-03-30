#!/usr/bin/env bash

set -euo pipefail

API_BASE="${API_BASE:-http://127.0.0.1:8080/api}"
CHAT_ENDPOINT="${API_BASE}/chat"
STREAM_ENDPOINT="${API_BASE}/chat/stream"
AUTH_REGISTER_ENDPOINT="${API_BASE}/auth/register"
AUTH_LOGIN_ENDPOINT="${API_BASE}/auth/login"
CONVERSATIONS_ENDPOINT="${API_BASE}/conversations"
HEALTH_ENDPOINT="${API_BASE}/chat/health"

PASS_COUNT=0
FAIL_COUNT=0

log() {
  echo "[$(date '+%H:%M:%S')] $*"
}

pass() {
  PASS_COUNT=$((PASS_COUNT + 1))
  log "PASS: $*"
}

fail() {
  FAIL_COUNT=$((FAIL_COUNT + 1))
  log "FAIL: $*"
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "缺少命令: $1"
    exit 1
  fi
}

retry_until_ok() {
  local url="$1"
  local max_retry="${2:-30}"
  local sleep_sec="${3:-2}"
  local i
  for ((i=1; i<=max_retry; i++)); do
    if curl -sf "$url" >/dev/null 2>&1; then
      return 0
    fi
    sleep "$sleep_sec"
  done
  return 1
}

require_cmd curl
require_cmd jq
require_cmd uuidgen

log "开始智能问答模块联调测试"
log "API_BASE=${API_BASE}"

if retry_until_ok "$HEALTH_ENDPOINT" 40 2; then
  pass "后端健康检查可用"
else
  fail "后端健康检查不可用: ${HEALTH_ENDPOINT}"
  echo "测试中止"
  exit 1
fi

TEST_USER="chat_it_$(date +%s)_$RANDOM"
TEST_PASS="Test@123456"

log "准备测试账号: ${TEST_USER}"
REGISTER_BODY="$(jq -n --arg u "$TEST_USER" --arg p "$TEST_PASS" '{username:$u,password:$p}')"
REGISTER_RESP="$(curl -s -X POST "$AUTH_REGISTER_ENDPOINT" -H 'Content-Type: application/json' -d "$REGISTER_BODY")"
REGISTER_TOKEN="$(echo "$REGISTER_RESP" | jq -r '.token // empty')"

if [[ -z "$REGISTER_TOKEN" ]]; then
  LOGIN_BODY="$(jq -n --arg u "$TEST_USER" --arg p "$TEST_PASS" '{username:$u,password:$p}')"
  LOGIN_RESP="$(curl -s -X POST "$AUTH_LOGIN_ENDPOINT" -H 'Content-Type: application/json' -d "$LOGIN_BODY")"
  TOKEN="$(echo "$LOGIN_RESP" | jq -r '.token // empty')"
else
  TOKEN="$REGISTER_TOKEN"
fi

if [[ -n "$TOKEN" ]]; then
  pass "测试账号登录成功"
else
  fail "测试账号登录失败"
  echo "响应: $REGISTER_RESP"
  exit 1
fi

AUTH_HEADER="Authorization: Bearer ${TOKEN}"
CONV_ID="it-$(uuidgen)"

run_chat_case() {
  local case_name="$1"
  local message="$2"
  local expect_keyword="${3:-}"

  local payload
  payload="$(jq -n --arg c "$CONV_ID" --arg m "$message" '{conversationId:$c,message:$m}')"

  local tmp_file
  tmp_file="$(mktemp)"
  local duration
  duration="$(curl -s -o "$tmp_file" -w '%{time_total}' -X POST "$CHAT_ENDPOINT" \
    -H 'Content-Type: application/json' \
    -H "$AUTH_HEADER" \
    -d "$payload")"

  local content
  content="$(jq -r '.content // empty' "$tmp_file")"
  local references
  references="$(jq -r '(.references // []) | length' "$tmp_file")"

  if [[ -z "$content" ]]; then
    fail "${case_name} -> 响应内容为空"
    log "原始响应: $(cat "$tmp_file")"
    rm -f "$tmp_file"
    return
  fi

  if [[ -n "$expect_keyword" && "$content" != *"$expect_keyword"* ]]; then
    fail "${case_name} -> 未命中预期关键词: ${expect_keyword}"
    log "响应内容(截断): ${content:0:180}"
    rm -f "$tmp_file"
    return
  fi

  pass "${case_name} -> 成功 (耗时 ${duration}s, references=${references})"
  rm -f "$tmp_file"
}

run_chat_case "场景1-问候" "你好" "您好"
run_chat_case "场景2-政策咨询" "山东省家电以旧换新补贴申请条件是什么？" "补贴"
run_chat_case "场景3-补贴计算" "我在济南买了电视，价格5999元，能补贴多少？" "补贴"
run_chat_case "场景4-实时价格" "帮我查一下 iPhone 16 最新价格，并结合山东补贴估算到手价" "价格"

# 缓存复问（重复问题）
run_chat_case "场景5-缓存复问-第一次" "青岛地区空调以旧换新怎么申请？" "申请"
run_chat_case "场景6-缓存复问-第二次" "青岛地区空调以旧换新怎么申请？" "申请"

# 会话列表验证
CONV_LIST_RESP="$(curl -s -X GET "$CONVERSATIONS_ENDPOINT" -H "$AUTH_HEADER")"
CONV_SIZE="$(echo "$CONV_LIST_RESP" | jq -r 'length // 0')"
if [[ "$CONV_SIZE" -ge 1 ]]; then
  pass "会话列表可查询，数量=${CONV_SIZE}"
else
  fail "会话列表为空或查询失败"
  log "会话列表响应: $CONV_LIST_RESP"
fi

# stream 兼容策略验证（sync-only 下应返回 410）
STREAM_PAYLOAD="$(jq -n --arg c "$CONV_ID" --arg m "你好" '{conversationId:$c,message:$m}')"
STREAM_CODE="$(curl -s -o /tmp/chat_stream_resp.json -w '%{http_code}' -X POST "$STREAM_ENDPOINT" \
  -H 'Content-Type: application/json' \
  -H "$AUTH_HEADER" \
  -d "$STREAM_PAYLOAD")"
if [[ "$STREAM_CODE" == "410" ]]; then
  pass "stream 接口兼容期行为正确（410）"
else
  fail "stream 接口返回码异常，期望 410，实际 ${STREAM_CODE}"
  log "stream 响应: $(cat /tmp/chat_stream_resp.json 2>/dev/null || true)"
fi

echo ""
echo "==================== 测试总结 ===================="
echo "通过: ${PASS_COUNT}"
echo "失败: ${FAIL_COUNT}"
echo "================================================="

if [[ "$FAIL_COUNT" -gt 0 ]]; then
  exit 1
fi
