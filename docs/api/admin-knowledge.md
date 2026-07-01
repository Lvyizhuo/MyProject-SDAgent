# 知识库管理模块

> 管理员对知识库文件夹、文档、切片、配置、网站导入任务及归档的完整管理。

- **路径前缀**：`/api/admin/knowledge`
- **认证**：管理员（`ADMIN` 角色，类级别 `@PreAuthorize("hasRole('ADMIN')")`）
- **控制器**：`AdminKnowledgeController`

## 接口总览

| 分组 | 方法 | 路径 | 说明 |
| :--- | :--- | :--- | :--- |
| 文件夹 | GET | `/folders` | 获取文件夹树 |
| 文件夹 | POST | `/folders` | 创建文件夹 |
| 文件夹 | PUT | `/folders/{id}` | 更新文件夹 |
| 文件夹 | DELETE | `/folders/{id}` | 删除文件夹 |
| 文档 | POST | `/documents` | 上传文档 |
| 文档 | POST | `/documents/extract-metadata` | 智能提取文档元数据 |
| 文档 | GET | `/documents` | 分页查询文档 |
| 文档 | GET | `/documents/selection` | 获取筛选范围文档 ID |
| 文档 | GET | `/documents/{id}` | 获取文档详情 |
| 文档 | GET | `/documents/{id}/chunks` | 查询文档切片 |
| 文档 | GET | `/documents/{id}/download` | 下载原始文档 |
| 文档 | GET | `/documents/{id}/preview` | 获取预览地址 |
| 文档 | DELETE | `/documents/{id}` | 删除文档 |
| 文档 | POST | `/documents/{id}/reingest` | 重新入库文档 |
| 文档 | POST | `/documents/batch-delete` | 批量删除 |
| 文档 | POST | `/documents/batch-move` | 批量移动 |
| 配置 | GET | `/config` | 获取知识库配置 |
| 配置 | PUT | `/config` | 更新知识库配置 |
| 嵌入模型 | GET | `/embedding-models` | 获取可用嵌入模型 |
| 归档 | GET | `/archive/export` | 导出知识库归档 |
| 归档 | POST | `/archive/import` | 导入知识库归档 |
| 网站导入 | POST | `/url-imports` | 创建网站导入任务 |
| 网站导入 | GET | `/url-imports` | 查询导入任务与候选条目 |
| 网站导入 | GET | `/url-imports/{id}` | 获取单个导入条目详情 |
| 网站导入 | POST | `/url-imports/{id}/confirm` | 确认入库单个条目 |
| 网站导入 | POST | `/url-imports/batch-confirm` | 批量确认入库 |
| 网站导入 | POST | `/url-imports/{id}/reject` | 驳回单个条目 |
| 网站导入 | POST | `/url-imports/{id}/cancel` | 取消导入任务 |
| 网站导入 | DELETE | `/url-imports/{id}` | 删除导入任务 |
| 网站导入 | DELETE | `/url-import-items/{id}` | 删除导入条目 |

---

# 一、文件夹管理

## 通用响应结构

`FolderTreeResponse` 字段：

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long | 文件夹 ID |
| `parentId` | Long | 父文件夹 ID |
| `name` | String | 名称 |
| `description` | String | 描述 |
| `path` | String | 路径 |
| `depth` | Integer | 深度 |
| `embeddingModel` | String | 嵌入模型 |
| `vectorTableName` | String | 向量表名 |
| `rerankModelId` | Long | 重排序模型 ID |
| `rerankModelName` | String | 重排序模型名称 |
| `initStatus` | String | 初始化状态（`INITIALIZING` / `READY` / `FAILED`） |
| `initError` | String | 初始化错误信息 |
| `initializedAt` | LocalDateTime | 初始化完成时间 |
| `children` | List\<FolderTreeResponse\> | 子文件夹列表 |

---

## 1. 获取文件夹树

### 请求

```
GET /api/admin/knowledge/folders
Authorization: Bearer {token}
```

### 响应

**状态码**：`200 OK`

```json
{
  "folders": [ FolderTreeResponse, ... ]
}
```

---

## 2. 创建文件夹

创建知识库文件夹，会触发向量表初始化（`initStatus` 由 `INITIALIZING` 转为 `READY`）。

### 请求

```
POST /api/admin/knowledge/folders
Authorization: Bearer {token}
Content-Type: application/json
```

**请求体**（`CreateFolderRequest`）：

| 字段 | 类型 | 必填 | 校验规则 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `parentId` | Long | 否 | - | 父文件夹 ID（顶级文件夹不传） |
| `name` | String | 是 | 长度 ≤ 200 | 文件夹名称 |
| `description` | String | 否 | 长度 ≤ 1000 | 描述 |
| `embeddingModel` | String | 是 | 长度 ≤ 200 | 嵌入模型 |
| `rerankModelId` | Long | 否 | - | 重排序模型 ID |

**请求示例：**

```json
{
  "parentId": 1,
  "name": "家电以旧换新",
  "description": "家电类政策文档",
  "embeddingModel": "ollama:bge-m3",
  "rerankModelId": 5
}
```

### 响应

**状态码**：`200 OK`

**响应体**：`FolderTreeResponse`

---

## 3. 更新文件夹

仅支持更新名称和描述（嵌入模型在创建时绑定，不可更改）。

### 请求

```
PUT /api/admin/knowledge/folders/{id}
Authorization: Bearer {token}
Content-Type: application/json
```

**路径参数：**

| 参数 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long | 文件夹 ID |

**请求体**（`UpdateFolderRequest`）：

| 字段 | 类型 | 必填 | 校验规则 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `name` | String | 是 | 长度 ≤ 200 | 文件夹名称 |
| `description` | String | 否 | 长度 ≤ 1000 | 描述 |

### 响应

**状态码**：`200 OK`

**响应体**：`FolderTreeResponse`

---

## 4. 删除文件夹

### 请求

```
DELETE /api/admin/knowledge/folders/{id}
Authorization: Bearer {token}
```

**路径参数：**

| 参数 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long | 文件夹 ID |

### 响应

**状态码**：`204 No Content`，无响应体。

### 错误

| 状态码 | 场景 |
| :--- | :--- |
| 400 | 文件夹下仍有文档，无法删除 |

---

# 二、文档管理

## 通用响应结构

`DocumentResponse` 字段：

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long | 文档 ID |
| `folderId` | Long | 文件夹 ID |
| `folderPath` | String | 文件夹路径 |
| `title` | String | 标题 |
| `fileName` | String | 文件名 |
| `fileSize` | Long | 文件大小（字节） |
| `fileType` | String | 文件类型（MIME） |
| `embeddingModel` | String | 嵌入模型 |
| `category` | String | 分类 |
| `tags` | List\<String\> | 标签 |
| `publishDate` | LocalDate | 发布日期 |
| `source` | String | 来源 |
| `websiteImported` | Boolean | 是否网站导入 |
| `externalSourceUrl` | String | 外部来源 URL |
| `externalSourcePage` | String | 外部来源页面 |
| `externalSourceSite` | String | 外部来源站点 |
| `importJobId` | Long | 导入任务 ID |
| `importItemId` | Long | 导入条目 ID |
| `validFrom` | LocalDate | 生效起始日期 |
| `validTo` | LocalDate | 生效截止日期 |
| `summary` | String | 摘要 |
| `status` | DocumentStatus | 状态（`PENDING` / `PROCESSING` / `COMPLETED` / `FAILED`） |
| `errorMessage` | String | 错误信息 |
| `chunkCount` | Integer | 分块数量 |
| `createdAt` | LocalDateTime | 创建时间 |
| `updatedAt` | LocalDateTime | 更新时间 |

---

## 5. 上传文档

上传文档到指定文件夹，自动切片并写入向量库。

### 请求

```
POST /api/admin/knowledge/documents
Authorization: Bearer {token}
Content-Type: multipart/form-data
```

**表单参数：**

| 参数 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `file` | MultipartFile | 是 | 文件（≤ 30MB） |
| `folderId` | Long | 否 | 目标文件夹 ID |
| `title` | String | 否 | 标题 |
| `embeddingModel` | String | 否 | 嵌入模型（不传则用文件夹/默认） |
| `category` | String | 否 | 分类 |
| `tags` | List\<String\> | 否 | 标签（可重复传参） |
| `publishDate` | LocalDate | 否 | 发布日期（`yyyy-MM-dd`） |
| `source` | String | 否 | 来源 |
| `validFrom` | LocalDate | 否 | 生效起始日期 |
| `validTo` | LocalDate | 否 | 生效截止日期 |
| `summary` | String | 否 | 摘要 |

### 响应

**状态码**：`200 OK`

**响应体**：`DocumentResponse`

### 错误

| 状态码 | 场景 |
| :--- | :--- |
| 400 | 文件为空；不支持的文件类型 |
| 413 | 文件超过 30MB |

---

## 6. 智能提取文档元数据

上传文件后，由 AI 自动提取标题、分类、标签、来源、摘要等元数据，供前端预填。

### 请求

```
POST /api/admin/knowledge/documents/extract-metadata
Authorization: Bearer {token}
Content-Type: multipart/form-data
```

**表单参数：**

| 参数 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `file` | MultipartFile | 是 | 文件 |

### 响应

**状态码**：`200 OK`

**响应体**（`DocumentMetadataExtractResponse`）：

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `title` | String | 提取的标题 |
| `category` | String | 提取的分类 |
| `tags` | List\<String\> | 提取的标签 |
| `source` | String | 提取的来源 |
| `summary` | String | 提取的摘要 |

---

## 7. 分页查询文档

### 请求

```
GET /api/admin/knowledge/documents
Authorization: Bearer {token}
```

**查询参数：**

| 参数 | 类型 | 默认 | 说明 |
| :--- | :--- | :--- | :--- |
| `folderId` | Long | - | 按文件夹过滤 |
| `importJobId` | Long | - | 按网站导入任务过滤 |
| `category` | String | - | 按分类过滤 |
| `tag` | String | - | 按标签过滤 |
| `status` | DocumentStatus | - | 按状态过滤 |
| `q` | String | - | 关键词搜索 |
| `page` | int | `0` | 页码 |
| `size` | int | `20` | 每页条数 |
| `sortBy` | String | `createdAt` | 排序字段 |
| `sortDir` | String | `DESC` | 排序方向 |

### 响应

**状态码**：`200 OK`

```json
{
  "content": [ DocumentResponse, ... ],
  "page": 0,
  "size": 20,
  "totalElements": 100,
  "totalPages": 5
}
```

---

## 8. 获取筛选范围文档 ID

返回当前筛选条件下的全部文档 ID（用于批量操作前置选择）。

### 请求

```
GET /api/admin/knowledge/documents/selection
Authorization: Bearer {token}
```

**查询参数：**

| 参数 | 类型 | 说明 |
| :--- | :--- | :--- |
| `folderId` | Long | 按文件夹过滤 |
| `importJobId` | Long | 按导入任务过滤 |
| `status` | DocumentStatus | 按状态过滤 |
| `q` | String | 关键词搜索 |

### 响应

**状态码**：`200 OK`

```json
{
  "ids": [1, 2, 3],
  "count": 3
}
```

---

## 9. 获取文档详情

### 请求

```
GET /api/admin/knowledge/documents/{id}
Authorization: Bearer {token}
```

### 响应

**状态码**：`200 OK`（存在）或 `404 Not Found`（不存在）

**响应体**：`DocumentResponse`

---

## 10. 查询文档切片

分页查看文档的切片结果。

### 请求

```
GET /api/admin/knowledge/documents/{id}/chunks
Authorization: Bearer {token}
```

**查询参数：**

| 参数 | 类型 | 默认 | 说明 |
| :--- | :--- | :--- | :--- |
| `page` | int | `0` | 页码 |
| `size` | int | `20` | 每页条数 |
| `chunkLevel` | String | `parent` | 切片层级 |

### 响应

**状态码**：`200 OK`

**响应体**（`DocumentChunksPageResponse`）：

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `documentId` | Long | 文档 ID |
| `title` | String | 文档标题 |
| `vectorTableName` | String | 向量表名 |
| `chunkCount` | Integer | 分块总数 |
| `page` | Integer | 当前页码 |
| `size` | Integer | 每页大小 |
| `totalElements` | Long | 总元素数 |
| `totalPages` | Integer | 总页数 |
| `content` | List\<DocumentChunkResponse\> | 分块列表 |

**DocumentChunkResponse 字段：**

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `chunkId` | String | 分块 ID |
| `chunkIndex` | Integer | 分块索引 |
| `chunkChars` | Integer | 分块字符数 |
| `splitStrategy` | String | 分割策略 |
| `content` | String | 分块内容 |

---

## 11. 下载原始文档

### 请求

```
GET /api/admin/knowledge/documents/{id}/download
Authorization: Bearer {token}
```

### 响应

**状态码**：`200 OK`

**响应头**：`Content-Disposition: attachment; filename*=UTF-8''{encodedFileName}`

**响应体**：文件二进制流（`InputStreamResource`）

### 错误

| 状态码 | 场景 |
| :--- | :--- |
| 400 | 文档不存在 |

---

## 12. 获取预览地址

返回文档预览 URL。若文档来自网站导入，返回外部来源 URL；否则返回 MinIO 预签名 URL（有效期 60 分钟）。

### 请求

```
GET /api/admin/knowledge/documents/{id}/preview
Authorization: Bearer {token}
```

### 响应

**状态码**：`200 OK`

```json
{
  "previewUrl": "https://minio.example.com/...?signature=...",
  "previewMode": "minio"
}
```

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `previewUrl` | String | 预览地址 |
| `previewMode` | String | 预览模式（`external` 外部链接 / `minio` 预签名） |

---

## 13. 删除文档

删除文档及其全部向量数据。

### 请求

```
DELETE /api/admin/knowledge/documents/{id}
Authorization: Bearer {token}
```

### 响应

**状态码**：`204 No Content`，无响应体。

---

## 14. 重新入库文档

重新对文档进行切片并写入向量库（用于修复失败或更换嵌入模型后重建）。

### 请求

```
POST /api/admin/knowledge/documents/{id}/reingest
Authorization: Bearer {token}
```

### 响应

**状态码**：`200 OK`（成功）或 `404 Not Found`（文档不存在）

**响应体**：`DocumentResponse`（重新入库后的文档状态）

---

## 15. 批量删除文档

### 请求

```
POST /api/admin/knowledge/documents/batch-delete
Authorization: Bearer {token}
Content-Type: application/json
```

**请求体**（`BatchDocumentOperationRequest`）：

| 字段 | 类型 | 必填 | 校验 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `ids` | List\<Long\> | 是 | 不能为空 | 文档 ID 列表 |
| `targetFolderId` | Long | 否 | - | （删除操作不使用） |

**请求示例：**

```json
{
  "ids": [1, 2, 3]
}
```

### 响应

**状态码**：`204 No Content`，无响应体。

---

## 16. 批量移动文档

### 请求

```
POST /api/admin/knowledge/documents/batch-move
Authorization: Bearer {token}
Content-Type: application/json
```

**请求体**（`BatchDocumentOperationRequest`）：

| 字段 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `ids` | List\<Long\> | 是 | 文档 ID 列表（不能为空） |
| `targetFolderId` | Long | 是 | 目标文件夹 ID |

**请求示例：**

```json
{
  "ids": [1, 2, 3],
  "targetFolderId": 5
}
```

### 响应

**状态码**：`204 No Content`，无响应体。

> ⚠️ 移动到不同嵌入模型的文件夹时，文档向量需重新生成。

---

# 三、知识库配置

## 17. 获取知识库配置

### 请求

```
GET /api/admin/knowledge/config
Authorization: Bearer {token}
```

### 响应

**状态码**：`200 OK`

**响应体**（`KnowledgeConfig`，单行配置）：

| 字段 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `id` | Long | `1` | 固定为 1 |
| `chunkSize` | Integer | `900` | 分块大小 |
| `chunkOverlap` | Integer | `150` | 分块重叠 |
| `minChunkSizeChars` | Integer | `250` | 最小分块字符数 |
| `noSplitMaxChars` | Integer | `900` | 不分割最大字符数 |
| `defaultEmbeddingModel` | String | `ollama:bge-m3` | 默认嵌入模型 |
| `minioEndpoint` | String | - | MinIO 端点 |
| `minioAccessKey` | String | - | MinIO 访问密钥 |
| `minioSecretKey` | String | - | MinIO 秘密密钥 |
| `minioBucketName` | String | `policy-documents` | MinIO 存储桶名称 |
| `createdAt` | LocalDateTime | - | 创建时间 |
| `updatedAt` | LocalDateTime | - | 更新时间 |

---

## 18. 更新知识库配置

仅更新非空字段。

### 请求

```
PUT /api/admin/knowledge/config
Authorization: Bearer {token}
Content-Type: application/json
```

**请求体**（`UpdateKnowledgeConfigRequest`）：

| 字段 | 类型 | 校验规则 | 说明 |
| :--- | :--- | :--- | :--- |
| `chunkSize` | Integer | 100 ~ 10000 | 分块大小 |
| `chunkOverlap` | Integer | 0 ~ 2000 | 分块重叠 |
| `minChunkSizeChars` | Integer | 50 ~ 2000 | 最小分块字符数 |
| `noSplitMaxChars` | Integer | 100 ~ 10000 | 不分割最大字符数 |
| `defaultEmbeddingModel` | String | 长度 ≤ 200 | 默认嵌入模型 |
| `minioEndpoint` | String | 长度 ≤ 500 | MinIO 端点 |
| `minioAccessKey` | String | 长度 ≤ 200 | MinIO 访问密钥 |
| `minioSecretKey` | String | 长度 ≤ 200 | MinIO 秘密密钥 |
| `minioBucketName` | String | 长度 ≤ 100 | MinIO 存储桶名称 |

### 响应

**状态码**：`200 OK`

**响应体**：`KnowledgeConfig`（更新后的完整配置）

---

# 四、嵌入模型

## 19. 获取可用嵌入模型

返回系统已映射到向量配置的嵌入模型列表（内置 + 已启用的第三方），含向量维度。

### 请求

```
GET /api/admin/knowledge/embedding-models
Authorization: Bearer {token}
```

### 响应

**状态码**：`200 OK`

```json
{
  "models": [
    {
      "id": "ollama:bge-m3",
      "name": "bge-m3",
      "provider": "ollama",
      "dimensions": 1024,
      "isDefault": true
    }
  ]
}
```

**EmbeddingModelResponse 字段：**

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | String | 模型 ID（映射后的系统标识） |
| `name` | String | 模型名称 |
| `provider` | String | 服务商 |
| `dimensions` | Integer | 向量维度 |
| `isDefault` | boolean | 是否默认 |

---

# 五、知识库归档

## 20. 导出知识库归档

将全部知识库（文件夹、文档元数据、切片）导出为 ZIP 压缩包。

### 请求

```
GET /api/admin/knowledge/archive/export
Authorization: Bearer {token}
```

### 响应

**状态码**：`200 OK`

**响应头**：`Content-Disposition: attachment; filename*=UTF-8''knowledge-archive-{date}.zip`

**响应体**：ZIP 文件二进制流（`application/octet-stream`）

---

## 21. 导入知识库归档

上传归档 ZIP 文件，导入到当前系统。

### 请求

```
POST /api/admin/knowledge/archive/import
Authorization: Bearer {token}
Content-Type: multipart/form-data
```

**表单参数：**

| 参数 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `file` | MultipartFile | 是 | 归档 ZIP 文件 |

### 响应

**状态码**：`200 OK`

**响应体**（`KnowledgeArchiveImportResponse`）：

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `importedCount` | Integer | 已导入数量 |
| `skippedCount` | Integer | 已跳过数量 |
| `failedCount` | Integer | 失败数量 |
| `messages` | List\<String\> | 消息列表 |

---

# 六、网站导入

> 通过输入网站 URL，系统自动爬取页面与附件，解析后生成候选条目，经管理员审核确认后入库为知识库文档。

## 22. 创建网站导入任务

### 请求

```
POST /api/admin/knowledge/url-imports
Authorization: Bearer {token}
Content-Type: application/json
```

**请求体**（`UrlImportCreateRequest`）：

| 字段 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `url` | String | 是 | 网站链接（不能为空） |
| `folderId` | Long | 是 | 目标知识库文件夹 ID（不能为空） |
| `embeddingModel` | String | 否 | 嵌入模型（兼容旧参数，当前由文件夹固定绑定，不再使用） |
| `titleOverride` | String | 否 | 标题覆盖 |
| `remark` | String | 否 | 备注 |

**请求示例：**

```json
{
  "url": "https://www.shandong.gov.cn/...",
  "folderId": 1,
  "remark": "省政府以旧换新政策专栏"
}
```

### 响应

**状态码**：`200 OK`

**响应体**（`UrlImportJobResponse`）：

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long | 任务 ID |
| `title` | String | 标题 |
| `sourceUrl` | String | 来源 URL |
| `sourceSite` | String | 来源站点 |
| `targetFolderId` | Long | 目标文件夹 ID |
| `targetFolderPath` | String | 目标文件夹路径 |
| `embeddingModel` | String | 嵌入模型 |
| `status` | UrlImportJobStatus | 任务状态 |
| `discoveredCount` | Integer | 发现数量 |
| `candidateCount` | Integer | 候选数量 |
| `importedCount` | Integer | 已导入数量 |
| `rejectedCount` | Integer | 已驳回数量 |
| `errorMessage` | String | 错误信息 |
| `createdAt` | LocalDateTime | 创建时间 |
| `updatedAt` | LocalDateTime | 更新时间 |

---

## 23. 查询导入任务与候选条目

返回所有导入任务及待处理候选条目。

### 请求

```
GET /api/admin/knowledge/url-imports
Authorization: Bearer {token}
```

### 响应

**状态码**：`200 OK`

**响应体**（`UrlImportListResponse`）：

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `jobs` | List\<UrlImportJobResponse\> | 导入任务列表 |
| `candidates` | List\<UrlImportItemResponse\> | 候选条目列表 |

**UrlImportItemResponse 字段：**

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long | 条目 ID |
| `jobId` | Long | 所属任务 ID |
| `sourceUrl` | String | 来源 URL |
| `sourcePage` | String | 来源页面 |
| `sourceSite` | String | 来源站点 |
| `itemType` | UrlImportItemType | 条目类型（`ARTICLE` / `ATTACHMENT`） |
| `title` | String | 标题 |
| `publishDate` | LocalDate | 发布日期 |
| `qualityScore` | Integer | 质量评分 |
| `parseStatus` | UrlImportParseStatus | 解析状态 |
| `reviewStatus` | UrlImportReviewStatus | 审核状态 |
| `suspectedDuplicate` | Boolean | 是否疑似重复 |
| `category` | String | 分类 |
| `tags` | List\<String\> | 标签 |
| `summary` | String | 摘要 |
| `cleanedText` | String | 清洗后文本 |
| `reviewComment` | String | 审核评论 |
| `errorMessage` | String | 错误信息 |
| `defaultFolderId` | Long | 默认文件夹 ID |
| `defaultFolderPath` | String | 默认文件夹路径 |
| `createdAt` | LocalDateTime | 创建时间 |
| `attachments` | List\<UrlImportAttachmentResponse\> | 附件列表 |

**UrlImportAttachmentResponse 字段：**

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long | 附件 ID |
| `attachmentUrl` | String | 附件 URL |
| `fileName` | String | 文件名 |
| `fileType` | String | 文件类型 |
| `parseStatus` | UrlImportParseStatus | 解析状态 |
| `ocrUsed` | Boolean | 是否使用了 OCR |

---

## 24. 获取单个导入条目详情

### 请求

```
GET /api/admin/knowledge/url-imports/{id}
Authorization: Bearer {token}
```

**路径参数：**

| 参数 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long | 导入条目 ID |

### 响应

**状态码**：`200 OK`

**响应体**：`UrlImportItemResponse`

---

## 25. 确认入库单个条目

将候选条目确认为正式知识库文档并入库。

### 请求

```
POST /api/admin/knowledge/url-imports/{id}/confirm
Authorization: Bearer {token}
Content-Type: application/json
```

**路径参数：**

| 参数 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long | 导入条目 ID |

**请求体**（`UrlImportConfirmRequest`）：

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `folderId` | Long | 文件夹 ID（兼容旧参数，固定使用任务绑定知识库，不支持修改） |
| `title` | String | 标题 |
| `category` | String | 分类 |
| `tags` | List\<String\> | 标签 |
| `publishDate` | LocalDate | 发布日期 |
| `source` | String | 来源 |
| `validFrom` | LocalDate | 生效起始日期 |
| `validTo` | LocalDate | 生效截止日期 |
| `summary` | String | 摘要 |

### 响应

**状态码**：`200 OK`

**响应体**：`DocumentResponse`（入库后的文档）

---

## 26. 批量确认入库

### 请求

```
POST /api/admin/knowledge/url-imports/batch-confirm
Authorization: Bearer {token}
Content-Type: application/json
```

**请求体**（`BatchUrlImportConfirmRequest`）：

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `ids` | List\<Long\> | 条目 ID 列表 |
| `folderId` | Long | 文件夹 ID（兼容旧参数，固定使用各任务绑定知识库，不支持覆盖） |

**请求示例：**

```json
{
  "ids": [10, 11, 12]
}
```

### 响应

**状态码**：`200 OK`

**响应体**（`BatchUrlImportConfirmResponse`）：

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `requestedCount` | Integer | 请求确认数量 |
| `successCount` | Integer | 成功数量 |
| `failedCount` | Integer | 失败数量 |
| `importedIds` | List\<Long\> | 已导入的文档 ID 列表 |
| `errors` | List\<String\> | 错误信息列表 |

---

## 27. 驳回单个条目

### 请求

```
POST /api/admin/knowledge/url-imports/{id}/reject
Authorization: Bearer {token}
Content-Type: application/json
```

**路径参数：**

| 参数 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long | 导入条目 ID |

**请求体**（`UrlImportRejectRequest`）：

| 字段 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `reason` | String | 是 | 驳回原因（不能为空） |

### 响应

**状态码**：`204 No Content`，无响应体。

---

## 28. 取消导入任务

取消整个导入任务（停止后续抓取）。

### 请求

```
POST /api/admin/knowledge/url-imports/{id}/cancel
Authorization: Bearer {token}
```

**路径参数：**

| 参数 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long | 导入任务 ID |

### 响应

**状态码**：`204 No Content`，无响应体。

---

## 29. 删除导入任务

删除导入任务及其关联数据。

### 请求

```
DELETE /api/admin/knowledge/url-imports/{id}
Authorization: Bearer {token}
```

**路径参数：**

| 参数 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long | 导入任务 ID |

### 响应

**状态码**：`204 No Content`，无响应体。

---

## 30. 删除导入条目

删除单个导入候选条目。

### 请求

```
DELETE /api/admin/knowledge/url-import-items/{id}
Authorization: Bearer {token}
```

**路径参数：**

| 参数 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long | 导入条目 ID |

### 响应

**状态码**：`204 No Content`，无响应体。

---

## 网站导入状态流转说明

**任务状态（`UrlImportJobStatus`）：**

```
PENDING → CRAWLING → PROCESSING → WAITING_CONFIRM → COMPLETED
                                      ↓
                              PARTIALLY_IMPORTED
                                      ↓
                                   CANCELED / FAILED
```

| 状态 | 说明 |
| :--- | :--- |
| `PENDING` | 待处理 |
| `CRAWLING` | 抓取中 |
| `PROCESSING` | 处理中 |
| `WAITING_CONFIRM` | 等待确认 |
| `PARTIALLY_IMPORTED` | 部分导入 |
| `COMPLETED` | 已完成 |
| `CANCELED` | 已取消 |
| `FAILED` | 失败 |

**条目审核状态（`UrlImportReviewStatus`）：**

| 状态 | 说明 |
| :--- | :--- |
| `WAITING_CONFIRM` | 等待确认 |
| `CONFIRMED` | 已确认（入库成功） |
| `REJECTED` | 已驳回 |
| `IMPORT_FAILED` | 导入失败 |
