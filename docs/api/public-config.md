# 公开配置模块

> 前端无需登录即可获取的智能体公开配置信息（如开场白）。

- **路径前缀**：`/api/public/config`
- **认证**：无（公开接口）
- **控制器**：`PublicConfigController`

## 接口一览

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| GET | `/api/public/config/agent` | 获取智能体公开配置 |

---

## 1. 获取智能体公开配置

返回当前运行时生效的智能体公开配置（目前仅包含开场白）。

### 请求

```
GET /api/public/config/agent
```

无请求参数与请求体。

### 响应

**状态码**：`200 OK`

**响应体**（`PublicAgentConfigResponse`）：

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `greetingMessage` | String | 开场白内容（未配置时为 `null`） |

**响应示例：**

```json
{
  "greetingMessage": "您好！我是山东省智能政策咨询助手，可以为您解答以旧换新相关政策问题，请问有什么可以帮您？"
}
```

### 说明

- 该接口从运行时配置（`DynamicAgentConfigHolder`）读取，反映管理员在 [智能体配置模块](./admin-agent-config.md) 中设置的最新开场白。
- 未配置开场白时返回 `greetingMessage: null`。
