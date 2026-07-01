# 文档管理模块

> 向量库文档的加载与删除（底层 RAG 维护接口）。

- **路径前缀**：`/api/documents`
- **认证**：无（公开接口）
- **控制器**：`DocumentController`

> 💡 本模块面向运维/初始化场景，用于批量加载文档到向量库。常规知识库管理请使用 [知识库管理模块](./admin-knowledge.md)。

## 接口一览

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| POST | `/api/documents/load` | 加载默认文档目录到向量库 |
| POST | `/api/documents/load-directory` | 从指定目录加载文档到向量库 |
| DELETE | `/api/documents` | 按向量 ID 删除文档 |

---

## 1. 加载默认文档

将默认配置目录下的所有文档加载、切片并写入向量库。

### 请求

```
POST /api/documents/load
```

无请求参数与请求体。

### 响应

**状态码**：`200 OK`

**响应体**：

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `success` | Boolean | 是否成功 |
| `message` | String | 提示信息 |
| `chunksCount` | int | 写入的切片数量 |

**响应示例：**

```json
{
  "success": true,
  "message": "文档加载完成",
  "chunksCount": 356
}
```

---

## 2. 从指定目录加载

从指定文件系统目录加载文档到向量库。

### 请求

```
POST /api/documents/load-directory?path={path}
```

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `path` | String | 是 | 文档目录的绝对路径 |

### 响应

**状态码**：`200 OK`

**响应体**：

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `success` | Boolean | 是否成功 |
| `message` | String | 提示信息 |
| `directoryPath` | String | 加载的目录路径 |
| `chunksCount` | int | 写入的切片数量 |

**响应示例：**

```json
{
  "success": true,
  "message": "目录文档加载完成",
  "directoryPath": "/data/policies",
  "chunksCount": 128
}
```

### 错误

| 状态码 | 场景 |
| :--- | :--- |
| 400 | 目录不存在或不可读 |

---

## 3. 删除文档向量

按向量 ID 删除向量库中的文档切片。

### 请求

```
DELETE /api/documents?ids={id1}&ids={id2}
```

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `ids` | List\<String\> | 是 | 要删除的向量 ID 列表（可重复传参） |

### 响应

**状态码**：`200 OK`

**响应体**：

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `success` | Boolean | 是否成功 |
| `message` | String | 提示信息 |
| `deletedCount` | int | 删除的数量 |

**响应示例：**

```json
{
  "success": true,
  "message": "文档删除完成",
  "deletedCount": 3
}
```
