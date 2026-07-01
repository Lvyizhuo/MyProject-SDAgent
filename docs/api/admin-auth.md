# 管理员认证模块

> 管理控制台的登录与密码修改。

- **路径前缀**：`/api/admin/auth`
- **控制器**：`AdminAuthController`

## 接口一览

| 方法 | 路径 | 认证 | 说明 |
| :--- | :--- | :--- | :--- |
| POST | `/api/admin/auth/login` | 无 | 管理员登录 |
| POST | `/api/admin/auth/change-password` | 管理员 | 修改管理员密码 |

---

## 1. 管理员登录

验证用户名密码，并校验用户角色必须为 `ADMIN`，通过后签发 JWT。

### 请求

```
POST /api/admin/auth/login
Content-Type: application/json
```

**请求体**（`AdminLoginRequest`）：

| 字段 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `username` | String | 是 | 用户名（不能为空） |
| `password` | String | 是 | 密码（不能为空） |

**请求示例：**

```json
{
  "username": "admin",
  "password": "admin123"
}
```

### 响应

**状态码**：`200 OK`

**响应体**（`AuthResponse`）：

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `token` | String | JWT 令牌 |
| `username` | String | 用户名 |
| `role` | Role | 角色（此处为 `ADMIN`） |

**响应示例：**

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "username": "admin",
  "role": "ADMIN"
}
```

### 错误

| 状态码 | 场景 |
| :--- | :--- |
| 401 | 用户名或密码错误 |
| 400 | 该用户不是管理员，无法访问管理控制台 |

---

## 2. 修改管理员密码

修改当前登录管理员的密码。

### 请求

```
POST /api/admin/auth/change-password
Authorization: Bearer {token}
Content-Type: application/json
```

**请求体**（`AdminChangePasswordRequest`）：

| 字段 | 类型 | 必填 | 校验规则 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `oldPassword` | String | 是 | 不能为空 | 旧密码 |
| `newPassword` | String | 是 | 长度 ≥ 6 | 新密码 |
| `confirmPassword` | String | 是 | 不能为空 | 确认密码 |

**请求示例：**

```json
{
  "oldPassword": "admin123",
  "newPassword": "newAdmin456",
  "confirmPassword": "newAdmin456"
}
```

### 响应

**状态码**：`200 OK`

**响应体**：

```json
{
  "message": "密码修改成功"
}
```

### 错误

| 状态码 | 场景 |
| :--- | :--- |
| 400 | 旧密码错误；新密码与确认密码不一致；新密码长度不足 |
| 401 | 未认证 |
| 403 | 非管理员角色 |
