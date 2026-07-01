# 用户认证模块

> 普通用户的注册、登录与身份查询。

- **路径前缀**：`/api/auth`
- **控制器**：`AuthController`

## 接口一览

| 方法 | 路径 | 认证 | 说明 |
| :--- | :--- | :--- | :--- |
| POST | `/api/auth/register` | 无 | 用户注册 |
| POST | `/api/auth/login` | 无 | 用户登录 |
| GET | `/api/auth/me` | 需要 | 获取当前登录用户信息 |

---

## 1. 用户注册

### 请求

```
POST /api/auth/register
Content-Type: application/json
```

**请求体**（`RegisterRequest`）：

| 字段 | 类型 | 必填 | 校验规则 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `username` | String | 是 | 长度 3~50 | 用户名 |
| `password` | String | 是 | 长度 6~100 | 密码 |

**请求示例：**

```json
{
  "username": "zhangsan",
  "password": "123456"
}
```

### 响应

**状态码**：`200 OK`

**响应体**（`AuthResponse`）：

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `token` | String | JWT 令牌 |
| `username` | String | 用户名 |
| `role` | Role | 角色（`USER` / `ADMIN`） |

**响应示例：**

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "username": "zhangsan",
  "role": "USER"
}
```

### 错误

| 状态码 | 场景 |
| :--- | :--- |
| 400 | 用户名/密码为空或长度不符；用户名已存在 |

---

## 2. 用户登录

### 请求

```
POST /api/auth/login
Content-Type: application/json
```

**请求体**（`LoginRequest`）：

| 字段 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `username` | String | 是 | 用户名（不能为空） |
| `password` | String | 是 | 密码（不能为空） |

**请求示例：**

```json
{
  "username": "zhangsan",
  "password": "123456"
}
```

### 响应

**状态码**：`200 OK`

**响应体**（`AuthResponse`）：同 [注册](#1-用户注册)。

### 错误

| 状态码 | 场景 |
| :--- | :--- |
| 401 | 用户名或密码错误、用户不存在 |

---

## 3. 获取当前用户信息

### 请求

```
GET /api/auth/me
Authorization: Bearer {token}
```

### 响应

**状态码**：`200 OK`

**响应体**（`UserInfo`）：

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long | 用户 ID |
| `username` | String | 用户名 |
| `role` | Role | 角色（`USER` / `ADMIN`） |

**响应示例：**

```json
{
  "id": 1,
  "username": "zhangsan",
  "role": "USER"
}
```

### 错误

| 状态码 | 场景 |
| :--- | :--- |
| 401 | 未携带或携带了无效的 Token |
