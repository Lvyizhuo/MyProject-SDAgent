#!/bin/bash

echo "======================================"
echo "Policy Agent API 测试脚本"
echo "======================================"
echo ""

echo "1. 测试健康检查端点..."
echo "----------------------------"
curl -s http://localhost:8080/api/chat/health
echo -e "\n"

echo "2. 测试 Actuator 健康检查..."
echo "----------------------------"
curl -s http://localhost:8080/actuator/health | jq '.'
echo ""

echo "3. 测试标准对话接口..."
echo "----------------------------"
echo "注意: 需要设置 DASHSCOPE_API_KEY 环境变量才能使用对话功能"
echo "示例命令:"
echo "  curl -X POST http://localhost:8080/api/chat \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    -d '{\"conversationId\": \"test-001\", \"message\": \"你好\"}'"
echo ""

echo "4. 测试流式对话接口..."
echo "----------------------------"
echo "示例命令:"
echo "  curl -X POST http://localhost:8080/api/chat/stream \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    -d '{\"conversationId\": \"test-001\", \"message\": \"你好\"}'"
echo ""

echo "======================================"
echo "测试完成"
echo "======================================"
