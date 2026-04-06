<div align="right">
  English | <a href="./README.md">中文</a>
</div>

<div align="center">
  <img src="./image.png" alt="AI Policy Agent Banner" width="100%">
  <h1>AI Policy Agent · SDAgent</h1>
  <h3>🏛️ Shandong Provincial Smart Policy Consulting Assistant</h3>
  <p align="center">
    <strong>Policy Interpretation | Subsidy Calculation | Multimodal Recognition | Admin Console</strong>
  </p>
  <p><em>An end-to-end intelligent knowledge base Q&A system built on RAG technology for Shandong provincial policy scenarios.</em></p>

  <img src="https://img.shields.io/github/stars/Lvyizhuo/MyProject-SDAgent?style=flat&logo=github" alt="GitHub stars"/>
  <img src="https://img.shields.io/github/forks/Lvyizhuo/MyProject-SDAgent?style=flat&logo=github" alt="GitHub forks"/>
  <img src="https://img.shields.io/badge/language-English%2FChinese-brightgreen?style=flat" alt="Language"/>
  <a href="https://github.com/Lvyizhuo/MyProject-SDAgent/issues">
    <img src="https://img.shields.io/badge/PRs-welcome-blue?style=flat&logo=github" alt="PRs Welcome">
  </a>
  <a href="https://github.com/Lvyizhuo/MyProject-SDAgent/blob/master/LICENSE">
    <img src="https://img.shields.io/badge/license-Apache%202.0-orange?style=flat" alt="License">
  </a>

  <div align="center" style="margin-top: 15px;">
    <img src="https://img.shields.io/badge/Java-21-ED8B00?style=flat&logo=openjdk&logoColor=white" />
    <img src="https://img.shields.io/badge/Spring_Boot-3.4.1-6DB33F?style=flat&logo=spring&logoColor=white" />
    <img src="https://img.shields.io/badge/React-19-61DAFB?style=flat&logo=react&logoColor=black" />
    <img src="https://img.shields.io/badge/PostgreSQL-pgvector-336791?style=flat&logo=postgresql&logoColor=white" />
    <img src="https://img.shields.io/badge/Spring_AI-1.0.3-6DB33F?style=flat&logo=spring" />
  </div>
</div>

---

## 📖 Introduction

**AI Policy Agent** is an LLM-powered consulting system designed for Shandong Province's "Trade-in" (以旧换新) policies. It supports intelligent Q&A, automated subsidy estimation, and multimodal recognition. The system includes a comprehensive Admin Console for agent orchestration, knowledge base management, and model service routing.

## 🏢 Business Scenarios

Designed for government departments, public service platforms, and industry associations. It focuses on high-frequency consulting needs regarding Shandong's trade-in policies, creating a closed-loop service of **"Query + Calculation + Recognition"** to reduce manual workload and improve policy accessibility.

## ✨ Key Features

- **Policy Q&A**: Structured, traceable answers based on RAG (Retrieval-Augmented Generation).
- **Subsidy Calculation**: Automatic calculation of subsidy amounts based on region, category, and price.
- **Multimodal Recognition**: Supports Voice-to-Text (ASR), Image/Device recognition, and Invoice OCR.
- **Admin Console**: Unified management of agent prompts, knowledge bases, and model providers.
- **Governance & Security**: Features authentication, tool-call intent validation, fallback strategies, and audit logging.

## 🛠️ Tech Stack

| Layer | Technologies |
|------|--------|
| **Frontend** | React 19 + Vite 7 |
| **Backend** | Spring Boot 3.4.1 + Spring AI 1.0.3 |
| **LLM** | Alibaba DashScope (Default: `qwen-plus`, supports OpenAI-compatible custom models) |
| **Vector DB** | PostgreSQL 16 + pgvector |
| **Cache/Session** | Redis 7 |
| **Storage** | MinIO (S3 Compatible) |
| **Security** | Spring Security + JWT |

## 📂 Project Structure

```text
├── backend/
│   ├── src/main/java/com/shandong/policyagent/
│   │   ├── advisor/      # Security, Memory, Logging, Re-read Validation
│   │   ├── agent/        # ToolIntentClassifier / AgentPlanParser
│   │   ├── config/       # ChatClient / Security / Embedding / Minio Configs
│   │   ├── rag/          # Slicing, Retrieval, Vector Ingestion, Dynamic Routing
│   │   ├── multimodal/   # ASR and Vision (OCR)
│   │   └── tool/         # calculateSubsidy / parseFile / webSearch
│   └── docker-compose.yml # PostgreSQL + Redis + MinIO
├── frontend/
│   └── src/
│       ├── pages/        # Main UI + Admin Console
│       └── services/     # API Integration (Chat & Admin)
```


## 🚀 Quick Start

### Prerequisites

  - Java 21+
  - Node.js 18+
  - Docker & Docker Compose

### 1\. Environment Variables

```bash
export DASHSCOPE_API_KEY=your_key
export TAVILY_API_KEY=your_key  # Optional for web search
export APP_JWT_SECRET=your_secret
```

### 2\. Infrastructure

```bash
cd backend
docker compose up -d
```

### 3\. Backend & Frontend

```bash
# Backend
./mvnw spring-boot:run

# Frontend
cd frontend
npm install && npm run dev
```

## 🖥️ Admin Console

The Admin Console provides 4 core modules:

1.  **Agent Config**: Manage System Prompts, Skills, and bind specific LLM/Vision/ASR models.
2.  **Knowledge Base**: Folder management, document/URL ingestion, chunking previews, and archival.
3.  **Tools**: Information architecture for integrated tool governance.
4.  **Model Management**: Maintain LLM, Visual, Audio, Embedding, and Rerank models with connectivity tests.

## 🌐 Deployment (Docker Compose)

Refer to the `deploy/` directory for one-click deployment.

  - Recommended path: `/www/wwwroot/MyProject-SDAgent`
  - Setup `.env` from `.env.example`.
  - Run: `docker compose --env-file .env up -d --build`.

## 📌 API Overview

| Method | Path | Description |
|------|------|------|
| POST | `/api/chat/stream` | Streaming Q\&A (SSE) |
| POST | `/api/admin/models/test` | Model Connectivity Test |
| POST | `/api/admin/knowledge/documents` | Upload & Ingest Knowledge |
| POST | `/api/multimodal/analyze-invoice` | OCR Invoice Analysis |

## ⭐ Star History


<p align="center">
  <a href="https://star-history.com/#Lvyizhuo/MyProject-SDAgent&Date">
    <img src="https://api.star-history.com/svg?repos=Lvyizhuo/MyProject-SDAgent&type=Date" alt="Star History Chart" />
  </a>
</p>

> 如果这个项目对你有帮助，欢迎点个 Star⭐；也欢迎提交 Issue 或 PR，一起把山东省政策智能体做得更实用。