# 多源政策知识库采集与服务化详细设计

## 文档信息

| 项目 | 内容 |
|------|------|
| 文档名称 | 多源政策知识库采集与服务化详细设计 |
| 创建日期 | 2026-03-10 |
| 版本 | v1.0 |
| 状态 | 草案 |
| 对应 PRD | 2026-03-10-multi-source-knowledge-ingestion-prd.md |

---

## 1. 设计目标

### 1.1 目标

在不破坏现有知识库上传、预览、删除、重入库、切片和向量化能力的前提下，为管理员控制台知识库页面增加一种新的知识来源获取方式：网站链接采集。

首期仅实现以下闭环：

1. 管理员在知识库页面输入网站链接。
2. 后端针对指定网站栏目执行抓取。
3. 解析结果以待入库文档形式展示在现有知识库页面。
4. 管理员预览内容后选择确认入库或驳回。
5. 确认入库后复用现有 KnowledgeService 入库链路完成文档存储、切片、embedding 和状态管理。

### 1.2 非目标

首期不实现以下能力：

1. 通用多站点规则编辑后台。
2. 完整任务调度平台。
3. 自动发布策略。
4. 复杂站点动态渲染抓取。
5. 主站查询和匹配链路的全面重构。

---

## 2. 设计约束

### 2.1 必须复用的现有能力

首期必须复用以下现有模块和接口：

1. AdminKnowledgeController 的知识库接口。
2. KnowledgeService 的知识文档创建、状态流转、重入库能力。
3. DocumentLoaderService 的正文提取与 PDF OCR 兜底能力。
4. TextSplitterService 的切片能力。
5. MultiVectorStoreService 的向量入库能力。
6. KnowledgeBaseTab 现有文档列表、文件夹树、通知、预览和状态展示能力。

### 2.2 首期质量约束

首期抓取内容不能“抓到就入库”，必须满足以下要求：

1. 只允许高质量正文或高质量附件正文进入待入库区。
2. 必须保留原文链接，供管理员直接跳转到政策原页面。
3. 必须保留采集结果的原始 HTML 或附件，以便后续回放。
4. 必须在入库前进行正文清洗，去除导航、页脚、版权、无关链接和重复片段。
5. 必须对政策内容和活动新闻进行基础区分，低质量活动稿不能默认进入主知识库。

### 2.3 首期站点范围

首期仅适配以下入口页面：

1. http://commerce.shandong.gov.cn/col/col352659/index.html

首期针对该站点采用定制适配器，而不是抽象完整通用规则系统。

---

## 3. 现状分析

### 3.1 现有前端能力

当前知识库页 [frontend/src/components/admin/KnowledgeBaseTab.jsx](frontend/src/components/admin/KnowledgeBaseTab.jsx) 已具备：

1. 文件夹树加载。
2. 文档列表加载。
3. 文档上传。
4. 文档分段查看。
5. 文档下载。
6. 文档删除与重入库。
7. 管理员通知与确认弹层。

这意味着首期前端改造不应再新增独立页面，而应在现有标签页内部新增一种来源录入和待入库处理能力。

### 3.2 现有后端能力

当前控制器 [backend/src/main/java/com/shandong/policyagent/controller/AdminKnowledgeController.java](backend/src/main/java/com/shandong/policyagent/controller/AdminKnowledgeController.java) 已具备：

1. 文件夹管理。
2. 文档上传。
3. 文档元数据提取。
4. 文档列表与详情。
5. 文档预览。
6. 文档重入库。
7. 知识库配置管理。

当前 KnowledgeService 和 DocumentLoaderService 已足以承接“最终入库”部分，因此首期新增逻辑应放在“网站抓取 -> 待入库候选内容”这一段。

### 3.3 现有抓取基础

仓库已有离线脚本 [scripts/scrape_shandong.py](scripts/scrape_shandong.py)，具备：

1. 列表页抓取。
2. 正文页抽取。
3. 附件发现。
4. 附件下载。
5. 简单政策关键词判断。

首期详细设计不直接复用脚本作为运行态主链路，但可复用其站点判断、正文提取和附件发现经验。

---

## 4. 总体方案

### 4.1 方案概述

首期整体链路如下：

1. 管理员在知识库页提交网站链接。
2. 后端创建一次网站采集任务。
3. 采集服务抓取入口页并发现候选文章页和附件。
4. 对候选内容进行正文抽取、附件下载、清洗、质量评分、去重。
5. 通过质量门槛的内容写入“待入库候选表”。
6. 前端在现有知识库页面展示待入库列表。
7. 管理员预览后执行确认入库。
8. 系统把候选内容转换为正式 KnowledgeDocument，复用现有入库链路。

### 4.2 首期架构图

```text
管理员知识库页
  ├─ 现有文件夹树
  ├─ 现有已入库文档列表
  ├─ 新增网站链接导入表单
  └─ 新增待入库候选列表

前端 adminKnowledgeApi
  ├─ 复用 /api/admin/knowledge/documents*
  └─ 新增 /api/admin/knowledge/url-imports*

AdminKnowledgeController
  ├─ 现有知识库接口
  └─ 新增网站导入接口

UrlImportService
  ├─ 创建采集任务
  ├─ 执行站点抓取
  ├─ 生成候选内容
  └─ 确认入库/驳回

SiteCrawlerAdapter
  └─ ShandongCommerceCrawlerAdapter

CandidateContentService
  ├─ 正文清洗
  ├─ 去重判定
  ├─ 元数据抽取
  └─ 质量评分

KnowledgeImportBridgeService
  └─ 将候选内容转换为正式 KnowledgeDocument 并调用现有入库链路
```

---

## 5. 首期站点适配设计

### 5.1 目标站点特征

首期目标页面为山东省商务厅栏目页：

1. 栏目入口为 col 路径。
2. 文章详情页通常为 art 路径。
3. 页面中存在大量活动稿、宣传稿、政策通知和解读稿混杂情况。
4. 部分政策正文可能以附件形式提供。

因此首期适配的关键不是“抓全”，而是“抓准”。

### 5.2 首期抓取范围

首期抓取范围定义如下：

1. 输入入口页后，抓取当前栏目页中的候选链接。
2. 对命中的候选详情页继续抓取正文和附件。
3. 不进行无限递归，只允许一层栏目页到详情页的深度。
4. 详情页中的附件允许继续下载和解析。

### 5.3 首期候选链接识别规则

对入口页面的所有 a 标签链接，按如下规则筛选：

1. 仅保留同域名链接。
2. 优先保留 /art/ 路径链接。
3. 保留指向 PDF、DOC、DOCX 的附件链接。
4. 排除图片、视频、脚本、下载无关资源。
5. 若标题或链接命中政策关键词，则提高优先级。

推荐的政策关键词初版：

1. 以旧换新
2. 补贴
3. 通知
4. 公告
5. 方案
6. 细则
7. 实施
8. 办法
9. 指南
10. 政策

### 5.4 活动稿和低价值内容过滤规则

首期必须加入基础内容过滤，避免活动新闻大量进入待入库区。

低优先级关键词示例：

1. 启动仪式
2. 圆满成功
3. 接力赛
4. 消费季
5. 走进
6. 活动现场
7. 宣贯活动

处理策略：

1. 若仅命中低优先级关键词且未命中政策关键词，则默认判为低质量候选。
2. 低质量候选默认不进入待入库区，可记录为已过滤。
3. 若同一页面同时含有“通知/细则/方案”等高价值关键词，则以政策内容优先。

### 5.5 正文提取策略

正文提取按以下优先顺序进行：

1. 站点定制正文选择器抽取。
2. 通用正文容器抽取。
3. 退化为段落聚合抽取。
4. 若为附件正文，则走现有 DocumentLoaderService。

首期针对山东省商务厅详情页应优先适配以下信息：

1. 标题。
2. 发布时间。
3. 正文主容器文本。
4. 附件列表。
5. 原文链接。

### 5.6 正文清洗规则

为满足 PRD 的质量要求，首期正文清洗必须包括：

1. 去除页头导航、当前位置、网站声明、版权信息。
2. 去除重复标题和重复段落。
3. 去除连续空行和异常空白字符。
4. 去除页面上的“打印、关闭、分享到”等操作性文本。
5. 对明显 OCR 噪声或乱码做基础正则清洗。

建议清洗后输出两个字段：

1. rawText：原始抽取文本。
2. cleanedText：清洗后的候选入库文本。

### 5.7 质量评分规则

首期质量评分采用规则评分，不引入模型评分。

建议评分项：

1. 标题命中政策关键词，+20。
2. 命中低优先级活动关键词，-15。
3. 正文长度超过 300 字，+10。
4. 正文长度超过 1000 字，+10。
5. 存在发布日期，+10。
6. 存在附件且附件可解析，+15。
7. 正文包含“实施方案/通知/公告/细则”，+15。
8. 正文清洗后为空或近乎为空，直接判 0。

评分阈值建议：

1. 80 分及以上：高质量，可进入待入库。
2. 60 到 79 分：中质量，可进入待入库但标记人工重点复核。
3. 60 分以下：默认过滤，不进入待入库。

---

## 6. 模块设计

### 6.1 后端新增包结构

建议新增如下包：

1. com.shandong.policyagent.ingestion
2. com.shandong.policyagent.ingestion.controller
3. com.shandong.policyagent.ingestion.entity
4. com.shandong.policyagent.ingestion.repository
5. com.shandong.policyagent.ingestion.service
6. com.shandong.policyagent.ingestion.crawler
7. com.shandong.policyagent.ingestion.model

### 6.2 核心服务划分

#### UrlImportService

职责：

1. 接收前端提交的网站链接。
2. 创建网站采集任务。
3. 驱动抓取流程。
4. 查询任务和候选内容。
5. 处理确认入库和驳回。

#### SiteCrawlerAdapter

职责：

1. 面向特定网站实现抓取逻辑。
2. 负责栏目页候选链接发现。
3. 负责详情页正文和附件提取。

首期仅实现：

1. ShandongCommerceCrawlerAdapter

#### CandidateContentService

职责：

1. 正文清洗。
2. 候选内容去重。
3. 元数据抽取。
4. 质量评分。
5. 生成待入库内容。

#### KnowledgeImportBridgeService

职责：

1. 将候选内容映射为正式 KnowledgeDocument。
2. 调用现有 KnowledgeService 完成持久化和后续处理。
3. 创建来源映射记录。

---

## 7. 数据模型详细设计

### 7.1 设计原则

首期数据模型需满足以下目标：

1. 不侵入现有 knowledge_documents 主表语义。
2. 网站采集结果和正式知识文档分离存储。
3. 待入库内容保留完整的原始来源和处理状态。
4. 正式入库后保留来源映射关系。

### 7.2 表设计

#### 7.2.1 url_import_jobs

作用：记录一次网站导入任务。

建议字段：

```sql
CREATE TABLE url_import_jobs (
    id BIGSERIAL PRIMARY KEY,
    source_url VARCHAR(1000) NOT NULL,
    source_site VARCHAR(200) NOT NULL,
    target_folder_id BIGINT REFERENCES knowledge_folders(id) ON DELETE SET NULL,
    embedding_model VARCHAR(200),
    title_override VARCHAR(500),
    remark TEXT,
    status VARCHAR(50) NOT NULL,
    discovered_count INTEGER NOT NULL DEFAULT 0,
    candidate_count INTEGER NOT NULL DEFAULT 0,
    imported_count INTEGER NOT NULL DEFAULT 0,
    rejected_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by BIGINT REFERENCES users(id)
);

CREATE INDEX idx_url_import_jobs_status ON url_import_jobs(status);
CREATE INDEX idx_url_import_jobs_created_at ON url_import_jobs(created_at DESC);
```

状态建议：

1. PENDING
2. CRAWLING
3. PROCESSING
4. WAITING_CONFIRM
5. PARTIALLY_IMPORTED
6. COMPLETED
7. FAILED

#### 7.2.2 url_import_items

作用：记录导入任务下发现的每个候选页面或附件。

建议字段：

```sql
CREATE TABLE url_import_items (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL REFERENCES url_import_jobs(id) ON DELETE CASCADE,
    source_url VARCHAR(1000) NOT NULL,
    source_page VARCHAR(1000),
    item_type VARCHAR(50) NOT NULL,
    source_title VARCHAR(500),
    publish_date DATE,
    raw_object_path VARCHAR(1000),
    cleaned_text_object_path VARCHAR(1000),
    content_hash VARCHAR(128),
    quality_score INTEGER NOT NULL DEFAULT 0,
    parse_status VARCHAR(50) NOT NULL,
    review_status VARCHAR(50) NOT NULL,
    review_comment TEXT,
    knowledge_document_id BIGINT REFERENCES knowledge_documents(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_url_import_items_job_id ON url_import_items(job_id);
CREATE INDEX idx_url_import_items_review_status ON url_import_items(review_status);
CREATE INDEX idx_url_import_items_quality_score ON url_import_items(quality_score DESC);
CREATE UNIQUE INDEX uq_url_import_items_source_url ON url_import_items(source_url);
```

item_type 建议：

1. ARTICLE
2. ATTACHMENT

parse_status 建议：

1. PENDING
2. PARSED
3. FILTERED
4. FAILED

review_status 建议：

1. WAITING_CONFIRM
2. CONFIRMED
3. REJECTED
4. IMPORT_FAILED

#### 7.2.3 url_import_attachments

作用：记录候选页面关联的附件。

建议字段：

```sql
CREATE TABLE url_import_attachments (
    id BIGSERIAL PRIMARY KEY,
    item_id BIGINT NOT NULL REFERENCES url_import_items(id) ON DELETE CASCADE,
    attachment_url VARCHAR(1000) NOT NULL,
    file_name VARCHAR(500),
    file_type VARCHAR(100),
    storage_path VARCHAR(1000),
    parsed_text_path VARCHAR(1000),
    parse_status VARCHAR(50) NOT NULL,
    ocr_used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_url_import_attachments_item_id ON url_import_attachments(item_id);
```

#### 7.2.4 knowledge_document_sources

作用：正式知识文档与网站采集来源映射。

建议字段：

```sql
CREATE TABLE knowledge_document_sources (
    id BIGSERIAL PRIMARY KEY,
    knowledge_document_id BIGINT NOT NULL REFERENCES knowledge_documents(id) ON DELETE CASCADE,
    import_item_id BIGINT NOT NULL REFERENCES url_import_items(id) ON DELETE CASCADE,
    source_site VARCHAR(200) NOT NULL,
    source_url VARCHAR(1000) NOT NULL,
    source_page VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_kds_document_id ON knowledge_document_sources(knowledge_document_id);
CREATE INDEX idx_kds_import_item_id ON knowledge_document_sources(import_item_id);
```

### 7.3 现有 knowledge_documents 的扩展建议

首期可不强制改表，但建议在下一步实现时评估增加以下字段：

1. source_url
2. source_site
3. source_type
4. region
5. policy_level

如果暂不改表，则至少通过 knowledge_document_sources 映射表保留来源信息。

---

## 8. 后端接口详细设计

### 8.1 接口设计原则

1. 路径统一挂在 /api/admin/knowledge 下。
2. 新接口只覆盖网站导入任务和候选内容确认。
3. 正式知识文档的查看、预览、分段和重入库继续使用现有接口。

### 8.2 新增接口清单

#### 8.2.1 提交网站链接导入

接口：

```text
POST /api/admin/knowledge/url-imports
```

请求体：

```json
{
  "url": "http://commerce.shandong.gov.cn/col/col352659/index.html",
  "folderId": 12,
  "embeddingModel": "ollama:qwen3-embedding",
  "titleOverride": "山东商务厅以旧换新栏目导入",
  "remark": "首期试点"
}
```

响应体：

```json
{
  "id": 1001,
  "status": "CRAWLING",
  "sourceUrl": "http://commerce.shandong.gov.cn/col/col352659/index.html",
  "message": "网站导入任务已创建"
}
```

校验规则：

1. url 必填。
2. 仅允许 http 或 https。
3. folderId 可选，但若传入则必须存在。
4. embeddingModel 可为空，为空则复用当前知识库默认嵌入模型。

#### 8.2.2 查询网站导入任务列表

接口：

```text
GET /api/admin/knowledge/url-imports
```

查询参数：

1. status
2. page
3. size

响应体：

```json
{
  "content": [
    {
      "id": 1001,
      "sourceUrl": "http://commerce.shandong.gov.cn/col/col352659/index.html",
      "status": "WAITING_CONFIRM",
      "discoveredCount": 18,
      "candidateCount": 6,
      "importedCount": 0,
      "rejectedCount": 0,
      "createdAt": "2026-03-10T10:30:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

#### 8.2.3 查询待入库候选列表

接口：

```text
GET /api/admin/knowledge/url-imports/{jobId}
```

响应体建议包含：

1. 任务信息。
2. 候选内容列表。
3. 每条候选的预览摘要、质量评分、来源链接、发布时间、reviewStatus。

#### 8.2.4 确认单条候选入库

接口：

```text
POST /api/admin/knowledge/url-imports/{itemId}/confirm
```

请求体：

```json
{
  "folderId": 12,
  "title": "2025年山东省消费品以旧换新实施细则",
  "category": "政策文件",
  "tags": ["以旧换新", "家电", "省级政策"],
  "publishDate": "2025-06-01",
  "source": "山东省商务厅",
  "summary": "自动抓取并清洗后的政策正文"
}
```

响应体：

```json
{
  "itemId": 2001,
  "knowledgeDocumentId": 501,
  "status": "IMPORTED",
  "message": "候选内容已提交入库"
}
```

#### 8.2.5 驳回单条候选内容

接口：

```text
POST /api/admin/knowledge/url-imports/{itemId}/reject
```

请求体：

```json
{
  "reason": "活动新闻，非政策正文"
}
```

---

## 9. 服务流程详细设计

### 9.1 提交网站导入

处理流程：

1. Controller 接收请求。
2. UrlImportService 校验 URL。
3. 创建 url_import_jobs 记录，状态为 PENDING。
4. 更新状态为 CRAWLING。
5. 调用 ShandongCommerceCrawlerAdapter 抓取入口页。
6. 发现候选详情页和附件。
7. 对每个候选执行解析、清洗、评分、去重。
8. 合格候选写入 url_import_items，状态为 WAITING_CONFIRM。
9. 任务状态更新为 WAITING_CONFIRM 或 FAILED。

### 9.2 候选确认入库

处理流程：

1. 管理员选择待入库候选并点击确认。
2. Controller 调用 KnowledgeImportBridgeService。
3. 服务将候选文本转换为可入库文件或资源。
4. 通过现有 KnowledgeService 完成正式知识文档创建。
5. 调用现有 processDocumentAsync 完成切片和 embedding。
6. 写入 knowledge_document_sources 映射。
7. 更新 url_import_items.review_status 为 CONFIRMED。

### 9.3 候选驳回

处理流程：

1. 管理员输入驳回原因。
2. 系统更新 review_status 为 REJECTED。
3. 保留原始内容、清洗文本和处理日志。

---

## 10. 与现有 KnowledgeService 的衔接设计

### 10.1 衔接原则

首期不建议在现有 KnowledgeService 上直接增加“抓网页”的复杂逻辑，而是增加桥接服务。

原因：

1. KnowledgeService 当前职责是知识文档管理，不是网站采集编排。
2. 直接混入抓取逻辑会导致控制器和服务职责膨胀。
3. 候选内容和正式知识文档生命周期不同，应分层处理。

### 10.2 推荐桥接方式

推荐新增 KnowledgeImportBridgeService，负责将待入库候选转换为正式知识文档。

有两种实现思路：

#### 方案 A：将 cleanedText 生成临时 txt 资源后调用现有 uploadDocument

优点：

1. 最大化复用现有流程。
2. 变更范围小。

缺点：

1. 对“来源是网页而不是附件”这种情况语义不完全自然。

#### 方案 B：在 KnowledgeService 新增内部方法 createDocumentFromText

优点：

1. 语义更清晰。
2. 可以避免人为构造临时 MultipartFile。

缺点：

1. 需要对 KnowledgeService 做一定增强。

首期建议采用方案 A 或 A 向 B 过渡的方式，优先保证低风险落地。

---

## 11. 前端详细设计

### 11.1 改造原则

首期前端改造必须满足：

1. 保持现有知识库页面主体布局不变。
2. 不额外新增独立路由。
3. 复用现有通知、确认弹层、文件夹选择和文档列表模式。

### 11.2 页面状态设计

KnowledgeBaseTab 需新增以下状态：

1. showSourceDialog：是否展示新增来源弹层。
2. sourceType：来源类型，取值 upload 或 url。
3. urlImportForm：网站导入表单数据。
4. importJobs：网站导入任务列表。
5. candidateDocuments：待入库候选列表。
6. selectedCandidate：当前预览的候选内容。
7. importLoading：网站导入提交状态。

### 11.3 页面布局改造

建议在现有“上传文档”入口附近增加“添加来源”按钮。

点击后弹出统一来源面板，包含两个 tab：

1. 手动上传
2. 网站链接导入

网站链接导入 tab 包含：

1. 网站链接输入框。
2. 目标文件夹下拉。
3. 嵌入模型选择。
4. 标题覆盖输入框。
5. 备注输入框。
6. 提交按钮。

### 11.4 待入库列表展示

候选内容建议在知识库页顶部或单独分组区域展示，位于正式文档列表之前。

展示字段：

1. 标题。
2. 来源链接。
3. 发布时间。
4. 质量评分。
5. 解析状态。
6. 操作按钮。

操作按钮：

1. 预览。
2. 确认入库。
3. 驳回。
4. 重新抓取。

### 11.5 候选预览交互

建议复用现有弹层/抽屉风格，展示：

1. 标题。
2. 原文链接。
3. 发布时间。
4. 摘要。
5. 正文预览。
6. 附件列表。
7. 质量评分和命中规则。
8. 确认入库和驳回按钮。

---

## 12. 通知与状态设计

### 12.1 前端通知节点

应复用 useAdminConsole 中的 notify 机制，至少支持以下通知：

1. 网站导入任务已创建。
2. 网站导入任务失败。
3. 候选内容已生成。
4. 候选内容已确认入库。
5. 候选内容驳回成功。

### 12.2 状态显示策略

页面中的状态展示不依赖通知，而使用明确标签。

候选内容状态建议颜色：

1. WAITING_CONFIRM：黄色。
2. CONFIRMED 或 IMPORTED：绿色。
3. REJECTED：灰色或红色。
4. FAILED：红色。

---

## 13. 异常处理设计

### 13.1 网站不可访问

处理方式：

1. 任务直接失败。
2. 返回明确错误信息。
3. 不创建候选内容。

### 13.2 页面无可用正文

处理方式：

1. 标记该候选 parse_status 为 FAILED 或 FILTERED。
2. 保留原始 HTML 以便排查。

### 13.3 附件解析失败

处理方式：

1. 如果正文页本身已有足够正文，则候选仍可进入待入库。
2. 如果正文页依赖附件且附件失败，则候选标记失败。

### 13.4 确认入库失败

处理方式：

1. review_status 更新为 IMPORT_FAILED。
2. 保留错误信息。
3. 支持管理员重试。

---

## 14. 安全与合规设计

### 14.1 权限控制

所有新接口必须挂在管理员权限下，统一要求：

1. 已登录。
2. 角色为 ADMIN。

### 14.2 站点抓取约束

首期必须遵守以下抓取约束：

1. 仅抓取公开可访问页面。
2. 控制并发和抓取频率。
3. 失败后退避重试，不暴力请求。
4. 保留原文链接，不篡改来源。

### 14.3 内容安全

待入库候选内容仅后台可见，不暴露给普通用户。

---

## 15. 开发拆分建议

### 15.1 后端 P0

1. Flyway 新增 4 张表。
2. 新增 url import 相关实体、仓储、DTO。
3. 新增 ShandongCommerceCrawlerAdapter。
4. 新增 UrlImportService 和 CandidateContentService。
5. AdminKnowledgeController 增加 5 个新接口。
6. 新增 KnowledgeImportBridgeService。

### 15.2 前端 P0

1. adminKnowledgeApi 增加 url import 相关接口。
2. KnowledgeBaseTab 增加来源面板。
3. KnowledgeBaseTab 增加待入库列表。
4. 增加候选预览弹层。
5. 增加入库和驳回交互。

### 15.3 联调重点

1. 网站链接提交后状态是否正确刷新。
2. 候选内容是否能与正式知识文档区分展示。
3. 确认入库后是否能在现有 documents 列表中看到正式文档。
4. 原文链接和来源映射是否正确保留。

---

## 16. 首期验收口径

首期验收时应重点确认以下问题：

1. 管理员是否可以在现有知识库页通过网站链接发起导入。
2. 是否可以对指定商务厅栏目抓取到高质量政策正文或附件正文。
3. 是否可以过滤明显无关活动稿和低质量内容。
4. 是否可以在待入库区预览内容，并看到原文链接。
5. 确认入库后是否确实走现有知识库链路完成向量化。
6. 现有手动上传流程是否完全不受影响。

---

## 17. 后续演进方向

首期完成后，可继续演进：

1. 抽象通用 SiteCrawlerAdapter 注册机制。
2. 抽象站点配置表和栏目配置表。
3. 增加定时调度和自动发现。
4. 增加版本识别和自动发布规则。
5. 为政策匹配提供专用结构化知识视图。
