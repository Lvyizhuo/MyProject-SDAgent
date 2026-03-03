# RAG 知识库重构设计文档

| 项目 | 内容 |
|------|------|
| 文档名称 | RAG 知识库重构 - 管理员控制台文档管理 |
| 创建日期 | 2026-03-03 |
| 版本 | v1.0 |
| 状态 | 已评审 |

---

## 1. 项目概述

### 1.1 背景

山东省智能政策咨询助手当前的 RAG 知识库来源有三种：本地文档向量转换、爬虫脚本获取的政策文件、联网搜索内容。现有 RAG 效果不够理想，且缺少可视化的文档管理界面。

### 1.2 目标

- 通过管理员控制台手动上传文档进行解析、切分、向量化
- 支持 PDF、DOC、DOCX、Markdown、TXT、HTML 等多种格式
- 支持文件夹管理（嵌套结构），可构建多个知识库
- 保留上传的文档文件，供后期"政策查询"模块展示
- 支持多种嵌入模型（默认 Ollama qwen3-embedding）
- 知识库隔离检索

### 1.3 范围

- 废弃原有的自动加载本地目录和爬虫 JSON 方式
- 从零开始构建新的知识库
- 管理员可以管理所有知识库和文档
- Agent 可以使用知识库进行检索

---

## 2. 需求决策记录

| 需求项 | 决策 |
|--------|------|
| 文档来源 | 完全迁移到管理员手动上传，废弃自动加载 |
| 功能范围 | 标准版（上传、列表、删除、预览、分类标签、批量操作、进度） |
| 切片配置 | 全局可配置 |
| 元数据 | 结构化（分类+标签+发布日期+来源+有效期） |
| 文档存储 | MinIO 自托管对象存储 |
| 文件夹结构 | 支持嵌套文件夹 |
| 嵌入模型 | 知识库级选择，默认 Ollama qwen3-embedding (4096维) |
| 检索方式 | 知识库隔离检索 |
| 权限 | 管理员管理，Agent 使用 |
| 旧数据 | 废弃，从零开始 |

---

## 3. 系统架构

### 3.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────────┐
│                         前端层 (React)                               │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │              AdminConsolePage / KnowledgeBaseTab              │ │
│  │  ┌──────────────────────┐  ┌──────────────────────────────┐ │ │
│  │  │  文件夹树 (左侧)     │  │  文档列表 (右侧)            │ │ │
│  │  │  - 嵌套文件夹        │  │  - 网格/列表视图            │ │ │
│  │  │  - 新建/删除文件夹   │  │  - 上传按钮                 │ │ │
│  │  │  - 拖拽移动          │  │  - 批量操作                 │ │ │
│  │  └──────────────────────┘  └──────────────────────────────┘ │ │
│  │  ┌──────────────────────────────────────────────────────────┐ │ │
│  │  │  上传对话框                                              │ │ │
│  │  │  - 文件选择                                             │ │ │
│  │  │  - 选择知识库/文件夹                                    │ │ │
│  │  │  - 选择嵌入模型 (默认 Ollama)                          │ │ │
│  │  │  - 填写元数据 (分类/标签/日期等)                       │ │ │
│  │  │  - 进度显示                                             │ │ │
│  │  └──────────────────────────────────────────────────────────┘ │ │
│  │  ┌──────────────────────────────────────────────────────────┐ │ │
│  │  │  全局配置面板                                            │ │ │
│  │  │  - 切片配置 (chunk size/overlap)                        │ │ │
│  │  │  - 嵌入模型管理 (添加/删除模型配置)                     │ │ │
│  │  │  - MinIO 配置                                           │ │ │
│  │  └──────────────────────────────────────────────────────────┘ │ │
│  └───────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────────┐
│                    后端层 (Spring Boot)                              │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │              AdminKnowledgeController                          │ │
│  │  POST   /api/admin/knowledge/folders        (创建文件夹)    │ │
│  │  GET    /api/admin/knowledge/folders        (文件夹树)      │ │
│  │  PUT    /api/admin/knowledge/folders/{id}   (更新文件夹)    │ │
│  │  DELETE /api/admin/knowledge/folders/{id}   (删除文件夹)    │ │
│  │  POST   /api/admin/knowledge/documents      (上传文档)      │ │
│  │  GET    /api/admin/knowledge/documents      (文档列表)      │ │
│  │  GET    /api/admin/knowledge/documents/{id} (文档详情)      │ │
│  │  GET    /api/admin/knowledge/documents/{id}/preview (预览)  │ │
│  │  DELETE /api/admin/knowledge/documents/{id} (删除文档)      │ │
│  │  POST   /api/admin/knowledge/documents/{id}/reingest (重入) │ │
│  │  GET    /api/admin/knowledge/config         (获取配置)      │ │
│  │  PUT    /api/admin/knowledge/config         (更新配置)      │ │
│  │  GET    /api/admin/knowledge/embedding-models (模型列表)    │ │
│  └───────────────────────────────────────────────────────────────┘ │
│  ┌──────────────────┐  ┌──────────────────┐  ┌───────────────┐ │
│  │KnowledgeService  │  │  EmbeddingService│  │StorageService │ │
│  │  - 文件夹CRUD    │  │  - Ollama        │  │  - MinIO      │ │
│  │  - 文档CRUD      │  │  - DashScope     │  │  - 文件存取   │ │
│  │  - 文档摄取流程  │  │  - 模型注册表    │  │  - URL生成    │ │
│  └──────────────────┘  └──────────────────┘  └───────────────┘ │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │               复用现有服务                                      │ │
│  │  - DocumentLoaderService (Tika解析)                            │ │
│  │  - TextSplitterService (文档切片)                              │ │
│  │  - VectorStoreService (扩展为多VectorStore支持)                │ │
│  └───────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────────┐
│                        数据层                                         │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐ │
│  │  PostgreSQL  │  │    MinIO     │  │      Redis             │ │
│  │              │  │              │  │                        │ │
│  │  - knowledge_│  │  - buckets:  │  │  - 上传进度缓存       │ │
│  │    folders   │  │    policy-   │  │                        │ │
│  │  - knowledge_│  │    documents │  │                        │ │
│  │    documents │  │              │  │                        │ │
│  │  - knowledge_│  │              │  │                        │ │
│  │    config    │  │              │  │                        │ │
│  │  - vector_   │  │              │  │                        │ │
│  │    store_*   │  │              │  │                        │ │
│  │    (按模型)  │  │              │  │                        │ │
│  └──────────────┘  └──────────────┘  └────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 4. 数据模型设计

### 4.1 数据库表结构

#### knowledge_folders 表（知识库文件夹）

```sql
CREATE TABLE knowledge_folders (
    id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT REFERENCES knowledge_folders(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    path VARCHAR(500) NOT NULL UNIQUE,
    depth INTEGER NOT NULL DEFAULT 1,
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by BIGINT REFERENCES users(id)
);

CREATE INDEX idx_knowledge_folders_parent_id ON knowledge_folders(parent_id);
CREATE INDEX idx_knowledge_folders_path ON knowledge_folders(path);
```

#### knowledge_documents 表（文档元数据）

```sql
CREATE TABLE knowledge_documents (
    id BIGSERIAL PRIMARY KEY,
    folder_id BIGINT REFERENCES knowledge_folders(id) ON DELETE SET NULL,

    title VARCHAR(500) NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    file_type VARCHAR(100) NOT NULL,

    storage_path VARCHAR(1000) NOT NULL,
    storage_bucket VARCHAR(100) NOT NULL,

    embedding_model VARCHAR(200) NOT NULL,
    vector_table_name VARCHAR(100) NOT NULL,

    category VARCHAR(200),
    tags VARCHAR(500)[],
    publish_date DATE,
    source VARCHAR(500),
    valid_from DATE,
    valid_to DATE,
    summary TEXT,

    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    chunk_count INTEGER DEFAULT 0,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by BIGINT REFERENCES users(id)
);

CREATE INDEX idx_knowledge_documents_folder_id ON knowledge_documents(folder_id);
CREATE INDEX idx_knowledge_documents_embedding_model ON knowledge_documents(embedding_model);
CREATE INDEX idx_knowledge_documents_status ON knowledge_documents(status);
CREATE INDEX idx_knowledge_documents_category ON knowledge_documents(category);
CREATE INDEX idx_knowledge_documents_tags ON knowledge_documents USING GIN(tags);
```

#### knowledge_config 表（全局配置）

```sql
CREATE TABLE knowledge_config (
    id BIGSERIAL PRIMARY KEY,

    chunk_size INTEGER DEFAULT 1600,
    chunk_overlap INTEGER DEFAULT 300,
    min_chunk_size_chars INTEGER DEFAULT 350,
    no_split_max_chars INTEGER DEFAULT 6000,

    default_embedding_model VARCHAR(200) DEFAULT 'ollama:qwen3-embedding',

    minio_endpoint VARCHAR(500),
    minio_access_key VARCHAR(200),
    minio_secret_key VARCHAR(200),
    minio_bucket_name VARCHAR(100) DEFAULT 'policy-documents',

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT single_config CHECK (id = 1)
);
```

#### 向量表（按模型分离）

```sql
-- Ollama qwen3-embedding (4096维)
CREATE TABLE vector_store_ollama_qwen3 (
    id UUID PRIMARY KEY,
    content TEXT,
    metadata JSONB,
    embedding vector(4096)
);
CREATE INDEX ON vector_store_ollama_qwen3 USING hnsw (embedding vector_cosine_ops);

-- DashScope text-embedding-v3 (1024维，可选保留)
CREATE TABLE vector_store_dashscope_v3 (
    id UUID PRIMARY KEY,
    content TEXT,
    metadata JSONB,
    embedding vector(1024)
);
CREATE INDEX ON vector_store_dashscope_v3 USING hnsw (embedding vector_cosine_ops);
```

### 4.2 JPA 实体类

**KnowledgeFolder.java**
```java
@Entity
@Table(name = "knowledge_folders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeFolder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private KnowledgeFolder parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<KnowledgeFolder> children = new ArrayList<>();

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, unique = true, length = 500)
    private String path;

    @Column(nullable = false)
    private Integer depth = 1;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

**KnowledgeDocument.java**
```java
@Entity
@Table(name = "knowledge_documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private KnowledgeFolder folder;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, length = 500)
    private String fileName;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false, length = 100)
    private String fileType;

    @Column(name = "storage_path", nullable = false, length = 1000)
    private String storagePath;

    @Column(name = "storage_bucket", nullable = false, length = 100)
    private String storageBucket;

    @Column(name = "embedding_model", nullable = false, length = 200)
    private String embeddingModel;

    @Column(name = "vector_table_name", nullable = false, length = 100)
    private String vectorTableName;

    @Column(length = 200)
    private String category;

    @Column(columnDefinition = "VARCHAR(500)[]")
    private List<String> tags;

    @Column(name = "publish_date")
    private LocalDate publishDate;

    @Column(length = 500)
    private String source;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.PENDING;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "chunk_count")
    private Integer chunkCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

public enum DocumentStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}
```

**KnowledgeConfig.java**
```java
@Entity
@Table(name = "knowledge_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeConfig {
    @Id
    private Long id = 1L;

    @Column(name = "chunk_size")
    private Integer chunkSize = 1600;

    @Column(name = "chunk_overlap")
    private Integer chunkOverlap = 300;

    @Column(name = "min_chunk_size_chars")
    private Integer minChunkSizeChars = 350;

    @Column(name = "no_split_max_chars")
    private Integer noSplitMaxChars = 6000;

    @Column(name = "default_embedding_model", length = 200)
    private String defaultEmbeddingModel = "ollama:qwen3-embedding";

    @Column(name = "minio_endpoint", length = 500)
    private String minioEndpoint;

    @Column(name = "minio_access_key", length = 200)
    private String minioAccessKey;

    @Column(name = "minio_secret_key", length = 200)
    private String minioSecretKey;

    @Column(name = "minio_bucket_name", length = 100)
    private String minioBucketName = "policy-documents";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

---

## 5. API 设计

### 5.1 文件夹管理 API

#### POST /api/admin/knowledge/folders
创建文件夹

**请求体：**
```json
{
  "parentId": null,
  "name": "济南市",
  "description": "济南市相关政策文档"
}
```

**响应体：**
```json
{
  "id": 1,
  "parentId": null,
  "name": "济南市",
  "path": "/济南市",
  "depth": 1,
  "description": "济南市相关政策文档",
  "createdAt": "2026-03-03T10:00:00Z"
}
```

#### GET /api/admin/knowledge/folders
获取文件夹树

**响应体：**
```json
{
  "folders": [
    {
      "id": 1,
      "parentId": null,
      "name": "济南市",
      "path": "/济南市",
      "depth": 1,
      "description": "...",
      "children": [
        {
          "id": 2,
          "parentId": 1,
          "name": "历下区",
          "path": "/济南市/历下区",
          "depth": 2,
          "children": []
        }
      ]
    }
  ]
}
```

#### PUT /api/admin/knowledge/folders/{id}
更新文件夹

#### DELETE /api/admin/knowledge/folders/{id}
删除文件夹

#### POST /api/admin/knowledge/folders/{id}/move
移动文件夹

**请求体：**
```json
{
  "newParentId": 3
}
```

### 5.2 文档管理 API

#### POST /api/admin/knowledge/documents
上传文档（Multipart Form）

```
Form Data:
- file: (binary)
- folderId: 1
- title: "2024年济南市以旧换新补贴政策"
- embeddingModel: "ollama:qwen3-embedding"
- category: "补贴政策"
- tags: ["济南市", "2024", "以旧换新"]
- publishDate: "2024-01-01"
- source: "济南市人民政府"
- validFrom: "2024-01-01"
- validTo: "2024-12-31"
- summary: "关于2024年济南市以旧换新补贴政策的详细说明..."
```

**响应体：**
```json
{
  "id": 1,
  "title": "2024年济南市以旧换新补贴政策",
  "status": "PROCESSING",
  "message": "文档上传成功，正在处理中..."
}
```

#### GET /api/admin/knowledge/documents
获取文档列表（分页、筛选）

```
Query Params:
- folderId: 1 (可选)
- category: "补贴政策" (可选)
- tag: "济南市" (可选)
- status: "COMPLETED" (可选)
- page: 0
- size: 20
```

#### GET /api/admin/knowledge/documents/{id}
获取文档详情

#### GET /api/admin/knowledge/documents/{id}/preview
获取文档预览

#### GET /api/admin/knowledge/documents/{id}/download
下载原文件

#### DELETE /api/admin/knowledge/documents/{id}
删除文档

#### POST /api/admin/knowledge/documents/{id}/reingest
重新向量化文档

#### POST /api/admin/knowledge/documents/batch-delete
批量删除

**请求体：**
```json
{
  "ids": [1, 2, 3]
}
```

### 5.3 配置 API

#### GET /api/admin/knowledge/config
获取全局配置

**响应体：**
```json
{
  "chunkSize": 1600,
  "chunkOverlap": 300,
  "minChunkSizeChars": 350,
  "noSplitMaxChars": 6000,
  "defaultEmbeddingModel": "ollama:qwen3-embedding",
  "minioEndpoint": "http://localhost:9000",
  "minioAccessKey": "minioadmin",
  "minioSecretKey": "minioadmin",
  "minioBucketName": "policy-documents"
}
```

#### PUT /api/admin/knowledge/config
更新全局配置

#### GET /api/admin/knowledge/embedding-models
获取可用嵌入模型列表

**响应体：**
```json
{
  "models": [
    {
      "id": "ollama:qwen3-embedding",
      "name": "Ollama Qwen3 Embedding",
      "provider": "ollama",
      "dimensions": 4096,
      "isDefault": true,
      "config": {
        "baseUrl": "http://localhost:11434",
        "modelName": "qwen3-embedding:latest"
      }
    },
    {
      "id": "dashscope:text-embedding-v3",
      "name": "DashScope Text Embedding V3",
      "provider": "dashscope",
      "dimensions": 1024,
      "isDefault": false,
      "config": {
        "apiKey": "${DASHSCOPE_API_KEY}"
      }
    }
  ]
}
```

---

## 6. 前端设计

### 6.1 组件结构

```
frontend/src/components/admin/
├── KnowledgeBaseTab.jsx
├── KnowledgeBaseTab.css
└── knowledge/
    ├── FolderTree.jsx
    ├── FolderTree.css
    ├── DocumentList.jsx
    ├── DocumentList.css
    ├── DocumentGrid.jsx
    ├── UploadDialog.jsx
    ├── UploadDialog.css
    ├── DocumentPreview.jsx
    ├── KnowledgeConfigPanel.jsx
    └── KnowledgeConfigPanel.css
```

### 6.2 主界面布局

```
┌─────────────────────────────────────────────────────────────────┐
│  [新建文件夹] [上传文档] [配置]    [搜索...]        [视图切换]  │
├──────────────┬──────────────────────────────────────────────────┤
│  文件夹树    │              文档列表/网格                         │
│              │                                                      │
│  📁 济南市   │  ┌──────────┐ ┌──────────┐ ┌──────────┐       │
│    📁 历下区 │  │ 📄 文档1  │ │ 📄 文档2  │ │ 📄 文档3  │       │
│    📁 市中区 │  │ 2024-01-01│ │ 2024-01-15│ │          │       │
│  📁 青岛市   │  └──────────┘ └──────────┘ └──────────┘       │
│              │                                                      │
│              │  [ ] 全选   [批量删除]  [移动到]                  │
│              │                                                      │
└──────────────┴──────────────────────────────────────────────────┘
```

### 6.3 上传对话框

```
┌─────────────────────────────────────────────┐
│           上传文档                     [X]    │
├─────────────────────────────────────────────┤
│  选择文件                                     │
│  ┌───────────────────────────────────────┐  │
│  │  点击或拖拽文件到此处                │  │
│  │  支持 PDF/DOC/DOCX/MD/TXT/HTML     │  │
│  └───────────────────────────────────────┘  │
│                                              │
│  目标文件夹: [济南市 / 历下区 ▼]            │
│                                              │
│  嵌入模型:   [Ollama Qwen3 Embedding ▼]    │
│              (默认，4096维)                  │
│                                              │
│  文档标题: [_______________]                 │
│                                              │
│  分类:       [补贴政策 ▼]                    │
│  标签:       [济南市] [2024] [+添加标签]    │
│  发布日期:   [2024-01-01]                   │
│  来源:       [济南市人民政府]                │
│  有效期:     [2024-01-01] 至 [2024-12-31] │
│  摘要:       ┌───────────────────────────┐  │
│              │                           │  │
│              └───────────────────────────┘  │
│                                              │
│  [上传进度: ████████░░░░ 60%]              │
│                                              │
│                  [取消]  [上传]             │
└─────────────────────────────────────────────┘
```

---

## 7. 配置与部署

### 7.1 docker-compose.yml 更新

在 `backend/docker-compose.yml` 中添加 MinIO 服务：

```yaml
services:
  postgres:
    # ... 现有配置 ...

  redis:
    # ... 现有配置 ...

  minio:
    image: minio/minio:latest
    container_name: policy-agent-minio
    command: server /data --console-address ":9001"
    ports:
      - "9000:9000"
      - "9001:9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    volumes:
      - minio_data:/data
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 30s
      timeout: 20s
      retries: 3

volumes:
  postgres_data:
  redis_data:
  minio_data:
```

### 7.2 application.yml 更新

```yaml
spring:
  ai:
    vectorstore:
      pgvector:
        dimensions: 4096

app:
  knowledge:
    minio:
      endpoint: http://localhost:9000
      access-key: minioadmin
      secret-key: minioadmin
      bucket-name: policy-documents

    embedding:
      default-model: ollama:qwen3-embedding
      models:
        - id: ollama:qwen3-embedding
          provider: ollama
          base-url: http://localhost:11434
          model-name: qwen3-embedding:latest
          dimensions: 4096
          vector-table: vector_store_ollama_qwen3
        - id: dashscope:text-embedding-v3
          provider: dashscope
          api-key: ${DASHSCOPE_API_KEY}
          model-name: text-embedding-v3
          dimensions: 1024
          vector-table: vector_store_dashscope_v3
```

### 7.3 Maven 依赖

在 `pom.xml` 中添加 MinIO 客户端：

```xml
<dependency>
    <groupId>io.minio</groupId>
    <artifactId>minio</artifactId>
    <version>8.5.11</version>
</dependency>
```

---

## 8. 实施计划

### 阶段 1：基础设施与数据模型（预计 1 天）
- [ ] 更新 docker-compose.yml 添加 MinIO
- [ ] 更新 application.yml 配置
- [ ] 创建 JPA 实体类（KnowledgeFolder, KnowledgeDocument, KnowledgeConfig）
- [ ] 创建 Repository 接口
- [ ] 测试 MinIO 连接和基本操作

### 阶段 2：后端核心服务（预计 2-3 天）
- [ ] 创建 StorageService（MinIO 封装）
- [ ] 创建 EmbeddingService（多模型支持：Ollama + DashScope）
- [ ] 扩展 VectorStoreService 支持多向量表
- [ ] 创建 KnowledgeService（文件夹 + 文档 CRUD）
- [ ] 创建文档摄取流水线（解析 → 切片 → 向量化 → 存储）
- [ ] 更新 RagConfig，废弃旧的自动加载配置

### 阶段 3：后端 API（预计 1-2 天）
- [ ] 创建 AdminKnowledgeController
- [ ] 实现文件夹管理 API
- [ ] 实现文档管理 API（含上传、下载、预览）
- [ ] 实现配置管理 API
- [ ] 实现嵌入模型列表 API
- [ ] 添加管理员权限校验

### 阶段 4：前端开发（预计 2-3 天）
- [ ] 更新 KnowledgeBaseTab.jsx（替换占位符）
- [ ] 创建 FolderTree 组件
- [ ] 创建 DocumentList/DocumentGrid 组件
- [ ] 创建 UploadDialog 组件
- [ ] 创建 DocumentPreview 组件
- [ ] 创建 KnowledgeConfigPanel 组件
- [ ] 创建 adminKnowledgeApi.js 服务
- [ ] 集成上传进度显示

### 阶段 5：RAG 检索集成（预计 1-2 天）
- [ ] 修改 RAG 检索逻辑，支持知识库隔离检索
- [ ] 更新 QuestionAnswerAdvisor，支持按知识库/模型筛选
- [ ] 提供根据用户问题自动选择知识库的机制

### 阶段 6：测试与优化（预计 1-2 天）
- [ ] 完整功能测试
- [ ] 性能测试（大文件上传、向量化）
- [ ] 错误处理优化
- [ ] 用户体验调整

**总体预计：10-13 天**

---

## 9. 附录

### 9.1 支持的文档格式

| 格式 | 扩展名 | 说明 |
|------|--------|------|
| PDF | .pdf | 可移植文档格式 |
| Word | .doc, .docx | Microsoft Word 文档 |
| Markdown | .md | Markdown 文本 |
| 纯文本 | .txt | 纯文本文件 |
| HTML | .html, .htm | HTML 网页 |

### 9.2 嵌入模型配置

**Ollama qwen3-embedding**
- 维度：4096
- 模型：qwen3-embedding:latest
- 端点：http://localhost:11434
- 默认启用

**DashScope text-embedding-v3**
- 维度：1024
- 模型：text-embedding-v3
- 端点：https://dashscope.aliyuncs.com/compatible-mode
- 可选启用

---

**文档结束**
