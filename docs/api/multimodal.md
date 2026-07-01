# 多模态模块

> 语音识别（ASR）与视觉能力（图像分析、发票识别、设备识别）。

- **路径前缀**：`/api/multimodal`
- **认证**：无（公开接口）
- **控制器**：`MultiModalController`

## 接口一览

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| POST | `/api/multimodal/transcribe` | 语音转文字 |
| POST | `/api/multimodal/analyze-image` | 图像分析 |
| POST | `/api/multimodal/analyze-invoice` | 发票识别 |
| POST | `/api/multimodal/analyze-device` | 设备识别 |

---

## 通用响应结构

所有接口返回 `MultiModalResponse`：

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `success` | boolean | 是否成功 |
| `type` | String | 响应类型（`asr` / `vision` / `invoice` / `device`） |
| `content` | String | 响应内容（识别结果） |
| `conversationId` | String | 会话 ID |
| `errorMessage` | String | 错误信息（失败时） |

**成功示例：**

```json
{
  "success": true,
  "type": "asr",
  "content": "山东省家电以旧换新政策",
  "conversationId": null,
  "errorMessage": null
}
```

**失败示例：**

```json
{
  "success": false,
  "type": "vision",
  "content": null,
  "conversationId": null,
  "errorMessage": "请提供 imageUrls 或 base64Image"
}
```

---

## 1. 语音转文字

将音频转录为文本。支持通过 URL 或 Base64 提供音频。

### 请求

```
POST /api/multimodal/transcribe
Content-Type: application/json
```

**请求体**（`MultiModalRequest.AudioTranscription`）：

| 字段 | 类型 | 必填 | 默认 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `audioUrl` | String | 二选一 | - | 音频 URL |
| `base64Audio` | String | 二选一 | - | Base64 音频数据 |
| `audioFormat` | String | 否 | `mp3` | 音频格式 |

> `audioUrl` 与 `base64Audio` 至少提供一个，否则返回 400。

**请求示例：**

```json
{
  "base64Audio": "UklGRiQAAABXQVZF...",
  "audioFormat": "wav"
}
```

### 响应

**状态码**：`200 OK`（成功）或 `400`（参数缺失）或 `500`（识别失败）

**响应体**：`MultiModalResponse`（`type=asr`）

---

## 2. 图像分析

对图像进行通用分析，需提供分析问题。

### 请求

```
POST /api/multimodal/analyze-image
Content-Type: application/json
```

**请求体**（`MultiModalRequest.ImageAnalysis`）：

| 字段 | 类型 | 必填 | 默认 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `imageUrls` | List\<String\> | 二选一 | - | 图片 URL 列表 |
| `base64Image` | String | 二选一 | - | Base64 图片数据 |
| `imageFormat` | String | 否 | `jpeg` | 图片格式 |
| `question` | String | 是 | - | 分析问题（不能为空） |

**请求示例：**

```json
{
  "imageUrls": ["https://example.com/policy-doc.png"],
  "question": "这张图片里的补贴金额是多少？"
}
```

### 响应

**状态码**：`200 OK`（成功）或 `400`（参数缺失/校验失败）或 `500`（分析失败）

**响应体**：`MultiModalResponse`（`type=vision`）

---

## 3. 发票识别

识别发票图片，提取发票号码、日期、销售方/购买方、金额、税额、价税合计等信息。

### 请求

```
POST /api/multimodal/analyze-invoice
Content-Type: application/json
```

**请求体**（`MultiModalRequest.InvoiceAnalysis`）：

| 字段 | 类型 | 必填 | 默认 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `imageUrl` | String | 二选一 | - | 发票图片 URL |
| `base64Image` | String | 二选一 | - | Base64 发票图片数据 |
| `imageFormat` | String | 否 | `jpeg` | 图片格式 |

**提取字段**：发票号码、开票日期、销售方名称、购买方名称、商品名称/项目、金额（小写）、税额、价税合计。

**请求示例：**

```json
{
  "imageUrl": "https://example.com/invoice.jpg"
}
```

### 响应

**状态码**：`200 OK`（成功）或 `400`（参数缺失）或 `500`（识别失败）

**响应体**：`MultiModalResponse`（`type=invoice`），`content` 为结构化提取结果。

---

## 4. 设备识别

识别设备/电器图片，提取设备类型、品牌、型号、状态等信息。

### 请求

```
POST /api/multimodal/analyze-device
Content-Type: application/json
```

**请求体**（`MultiModalRequest.DeviceAnalysis`）：

| 字段 | 类型 | 必填 | 默认 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `imageUrl` | String | 二选一 | - | 设备图片 URL |
| `base64Image` | String | 二选一 | - | Base64 设备图片数据 |
| `imageFormat` | String | 否 | `jpeg` | 图片格式 |

**识别字段**：设备类型（空调/冰箱/洗衣机/电视/手机等）、品牌、型号、设备状态、其他可识别特征。

**请求示例：**

```json
{
  "base64Image": "/9j/4AAQSkZJRg...",
  "imageFormat": "jpeg"
}
```

### 响应

**状态码**：`200 OK`（成功）或 `400`（参数缺失）或 `500`（识别失败）

**响应体**：`MultiModalResponse`（`type=device`），`content` 为结构化识别结果。
