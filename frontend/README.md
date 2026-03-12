# 山东省智能政策咨询助手 - Frontend

前端基于 React 19 + Vite 7，提供用户政策咨询页面与管理员控制台页面。管理员端当前已接入智能体配置、知识库管理、工具规划视图和模型服务管理。

## 启动命令

```bash
cd frontend
npm install
npm run dev
```

其他命令：

```bash
npm run build
npm run lint
npm run preview
```

## 路由

- `/home` 首页
- `/policies` 政策查询
- `/matching` 政策匹配
- `/chat` 智能问答（登录后）
- `/user` 用户中心
- `/login` 登录
- `/register` 注册
- `/admin-console` 管理员控制台（管理员登录后）

## 主要目录

- `src/pages/` 页面组件（含 `AdminConsolePage.jsx`）
- `src/components/` 通用组件
- `src/components/admin/` 管理员控制台子模块（含 `ConfigPanel`、`KnowledgeBaseTab`、`ToolsTab`、`ModelsTab`、统一反馈层）
- `src/services/api.js` 用户侧 API 请求封装
- `src/services/adminApi.js` 管理员配置 API
- `src/services/adminKnowledgeApi.js` 管理员知识库 API

## 管理员控制台

- `AdminConsolePage` 提供 `agent / knowledge / tools / models` 四个一级页面。
- `ConfigPanel` 支持从模型管理中选择 LLM、视觉、语音、嵌入模型，也支持在未绑定 LLM 时继续手动填写 API 地址和模型名。
- `KnowledgeBaseTab` 支持文件夹管理、文档上传/预览/下载、查看切片、重新入库、批量移动/删除，以及“网站导入（URL Import）”的审核确认入库流程。
- `ModelsTab` 以卡片形式管理四类模型，支持新增、编辑、删除、设为默认和连接测试。
- `PreviewPanel` 中的测试对话会展示“当前生效模型”，便于确认运行时到底走的是手动配置还是模型管理绑定。
- `AdminConsoleProvider` 为管理员子页面提供统一 toast、确认弹层和输入弹层。

## 鉴权说明

- 用户登录与管理员登录均使用 JWT，并写入 `localStorage`。
- 管理员接口使用 `/api/admin/**`，普通用户 token 无法访问。
- `ProtectedRoute` 负责前端路由级访问控制。

## 管理员接口封装

- `src/services/adminApi.js`：管理员登录、智能体配置、模型管理接口
- `src/services/adminKnowledgeApi.js`：知识库目录、上传、切片、重入库、批量操作、网站导入（URL Import）、预览与下载等接口
- 首页和聊天页可通过后端公开接口 `/api/public/config/agent` 读取公开开场白

## 依赖摘要

- `react` / `react-dom` 19.2
- `react-router-dom` 7.13
- `lucide-react`
- `marked`
- `uuid`
