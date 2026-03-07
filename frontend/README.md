# 山东省智能政策咨询助手 - Frontend

前端基于 React 19 + Vite 7，提供用户政策咨询页面与管理员控制台页面。

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
- `src/components/admin/` 管理员控制台子模块
- `src/services/api.js` 用户侧 API 请求封装
- `src/services/adminApi.js` 管理员配置 API
- `src/services/adminKnowledgeApi.js` 管理员知识库 API

## 鉴权说明

- 用户登录与管理员登录均使用 JWT，并写入 `localStorage`。
- 管理员接口使用 `/api/admin/**`，普通用户 token 无法访问。
- `ProtectedRoute` 负责前端路由级访问控制。

## 依赖摘要

- `react` / `react-dom` 19.2
- `react-router-dom` 7.13
- `lucide-react`
- `marked`
- `uuid`
