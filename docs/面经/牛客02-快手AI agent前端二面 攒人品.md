# AI 前端应用岗 - 面经

> 基于山东省智能政策咨询助手项目，结合前端 AI 应用高频面试题

---

## 目录
1. [React Fiber 架构与 AI 高频更新场景](#1-react-fiber-架构与-ai-高频更新场景)
2. [流式数据与 Markdown 渲染](#2-流式数据与-markdown-渲染)
3. [RAG 流程与前端工作](#3-rag-流程与前端工作)
4. [Prompt 构建与封装](#4-prompt-构建与封装)
5. [AI 应用前端性能优化](#5-ai-应用前端性能优化)
6. [WebWorker 在 AI 应用中的应用](#6-webworker-在-ai-应用中的应用)
7. [XSS 攻击防范](#7-xss-攻击防范)
8. [手写 EventBus](#8-手写-eventbus)

---

## 1. React Fiber 架构与 AI 高频更新场景

**面试官问**：React 的 Fiber 架构解决了什么问题？在 AI 聊天这种高频更新场景下有什么优势？

**参考答案**（结合项目）：

### React 16 之前的问题
React 16 之前用的是 Stack Reconciler（栈协调器），递归更新 Virtual DOM，一旦开始就无法中断，长时间占用主线程。如果更新耗时超过 16.6ms（60fps），就会掉帧、卡顿。

### Fiber 架构的核心改进
Fiber 架构把递归更新改成了**链表结构 + 时间切片**：
- **链表结构**：每个 Fiber 节点有 child、sibling、return 指针，可以随时暂停和恢复
- **时间切片**：requestIdleCallback / scheduler，每帧只工作一小段时间（~5ms），把控制权还给浏览器
- **优先级调度**：不同更新有不同优先级，高优先级（用户输入）可以打断低优先级

### 在 AI 聊天场景下的优势（结合我的项目）
在 `ChatWindow.jsx` 中，AI 流式输出时：
- **高频更新**：每个 delta 都要更新状态，可能每秒几十次
- **Fiber 的优势**：
  1. **不阻塞用户输入**：用户可以在 AI 回答时继续打字、发送新消息
  2. **平滑渲染**：时间切片保证动画、滚动流畅
  3. **可中断**：新消息到来时，可以中断当前的流式渲染

### 我的项目中的实践
```jsx
// ChatWindow.jsx 中的优化
const [messages, setMessages] = useState(...);
// 流式更新时，React 19 的 Fiber 架构能高效处理高频状态更新
// 配合 useRef、requestAnimationFrame 做滚动优化
```

---

## 2. 流式数据与 Markdown 渲染

**面试官问**：处理 AI 流式数据时，前端如何优雅地处理 Markdown 渲染和代码高亮？

**参考答案**（结合项目代码）：

### 我的项目中的实现（MessageBubble.jsx + ChatWindow.jsx）

#### 1. SSE 流式数据解析（ChatWindow.jsx）
```jsx
// SSE 事件消费
const consumeSseEvents = (chunk, onData) => {
    const events = chunk.split('\n\n');
    const rest = events.pop() || '';
    for (const event of events) {
        const lines = event.split('\n');
        const dataLines = lines
            .filter(line => line.startsWith('data:'))
            .map(line => line.replace(/^data:\s?/, ''));
        if (dataLines.length > 0) {
            onData(dataLines.join('\n'));
        }
    }
    return rest; // 保存不完整的 chunk
};

// 解析 payload
const parseStreamPayload = (payload) => {
    try {
        const parsed = JSON.parse(payload);
        if (parsed?.type) return parsed;
    } catch { /* 兼容纯文本 */ }
    return { type: 'delta', content: payload };
};
```

#### 2. Markdown 渲染（MessageBubble.jsx）
```jsx
import { marked } from 'marked';

// marked 配置
marked.setOptions({
    gfm: true,    // GitHub Flavored Markdown
    breaks: true   // 换行转 <br>
});

// 流式内容预处理
const normalizeContent = (raw) => {
    let content = raw.replace(/\r\n/g, '\n');
    // 补全新行，避免流式渲染时格式错乱
    content = content
        .replace(/([。！？])\s*(#{1,6}\s)/g, '$1\n\n$2')
        .replace(/([。！？])\s*(\d+\.\s)/g, '$1\n$2');
    return content.trim();
};

// 渲染
const htmlContent = marked.parse(markdownWithRefs);
// dangerouslySetInnerHTML 渲染（配合 XSS 防护）
<div dangerouslySetInnerHTML={{ __html: htmlContent }} />
```

#### 3. 优化技巧
- **buffer 处理**：SSE 数据可能分片，用 buffer 保存不完整的事件
- **增量渲染**：不要每次都重新 parse 全部内容，只追加 delta
- **关键词高亮**：`normalizeContent` 中把政策相关关键词加粗
- **防抖**：避免高频更新导致的性能问题

---

## 3. RAG 流程与前端工作

**面试官问**：谈谈你对 RAG 流程的理解，前端在其中可以做哪些工作？

**参考答案**（结合项目）：

### RAG 完整流程
```
用户问题
  ↓
查询改写（可选）
  ↓
向量化 → 向量检索（Top-K）→ 重排序（Rerank）→ Top-N
  ↓
Prompt 组装：系统提示词 + RAG 结果 + 用户问题
  ↓
LLM 生成
  ↓
返回回答 + 引用来源
```

### 前端在 RAG 中的工作（我的项目实践）

#### 1. 查询预处理（前端可以做）
```jsx
// ChatWindow.jsx - 用户输入可以做简单处理
const handleSend = async (text, files = []) => {
    // 1. 去除多余空格、换行
    const cleanedText = text.trim();
    // 2. 图片 base64 编码（多模态）
    const imageBase64List = await Promise.all(
        imageFiles.map(f => fileToBase64(f.file))
    );
    // 3. 附上位置信息（用于地图服务）
    // 4. 发送给后端
};
```

#### 2. 引用来源展示（MessageBubble.jsx）
```jsx
// 注入引用标记
const injectReferenceMarker = (content, references) => {
    return content.replace(/\[(\d+)](?!\()/g, (match, rawId) => {
        const index = Number(rawId) - 1;
        if (Number.isNaN(index) || index < 0 || index >= references.length) {
            return match;
        }
        return `<button type="button" class="ref-chip" data-ref-id="${rawId}">
            [${rawId}]
        </button>`;
    });
};

// ReferencesBlock 组件展示详细来源
<ReferencesBlock
    messageId={id}
    references={referencesForMessage}
    expandedRefIds={expandedRefIds}
    onToggleRef={handleToggleRef}
/>
```

#### 3. 负反馈/正面反馈（可选，我的项目没做但可以加）
- 用户点击"赞/踩"，告诉 RAG 这个结果好不好
- 前端可以做"切换引用"、"不满意重查"功能

#### 4. 前端 Prompt 组装（公开配置）
```jsx
// publicConfigApi - 获取智能体配置（开场白等）
const fetchGreeting = async () => {
    const config = await publicConfigApi.getAgentConfig();
    if (config.greetingMessage) {
        setGreetingContent(config.greetingMessage);
    }
};
```

---

## 4. Prompt 构建与封装

**面试官问**：如何优化 Prompt 的构建过程？前端如何封装 Prompt 模板？

**参考答案**（结合项目）：

### 前端 Prompt 封装（虽然主要在后端，但前端也可以做）

#### 1. 配置化 Prompt 模板
```jsx
// services/promptTemplates.js（可以建一个这样的模块）
const PROMPT_TEMPLATES = {
    policyQuery: ({ question, context, facts }) => `
【政策咨询】
用户问题：${question}

【参考依据】
${context}

【已知事实】
${facts}

请基于以上信息回答...
`,
    subsidyCalc: ({ price, category }) => `
请计算补贴金额：
- 商品类型：${category}
- 购买价格：${price}元
`
};

export const buildPrompt = (type, variables) => {
    const template = PROMPT_TEMPLATES[type];
    if (!template) throw new Error(`Unknown template: ${type}`);
    return template(variables);
};
```

#### 2. 我的项目中的做法（前端做轻量组装）
ChatWindow.jsx 中，把位置、图片识别结果等拼接到请求中：
```jsx
// 位置、图片这些作为上下文传给后端，后端再拼到 Prompt
const response = await chatApi.createStreamRequest(
    text,           // 用户问题
    sessionId,      // 会话 ID
    imageBase64List,// 图片（后端调用 VLM 识别后拼 Prompt）
    location        // 位置（后端拼到 Prompt）
);
```

#### 3. Prompt 优化技巧
- **变量注入**：用 ${xxx} 占位，不要字符串拼接
- **转义处理**：用户输入中的特殊字符要转义，避免 Prompt 注入
- **前端缓存**：相同配置的 Prompt 可以缓存（虽然不多）
- **少样本示例**：前端可以维护示例库，让用户选择或自动注入

---

## 5. AI 应用前端性能优化

**面试官问**：你们项目里是如何做前端性能优化的？请结合 AI 应用的特点聊聊。

**参考答案**（结合项目代码）：

### AI 应用的特点与优化方向
| 特点 | 问题 | 优化方案 |
|------|------|----------|
| 流式高频更新 | 状态更新频繁，可能卡顿 | React 19 Fiber、防抖、虚拟列表 |
| Markdown 渲染 | 多次 parse 开销大 | 增量渲染、缓存 |
| 历史会话多 | DOM 节点多，内存占用高 | 虚拟列表、懒加载 |
| 图片上传 | base64 传输慢 | 压缩、预览、分片上传 |

### 我的项目中的具体优化

#### 1. 流式更新优化（ChatWindow.jsx）
```jsx
// 1. 自动滚动判断：用户往上翻时不自动滚动
const handleMessagesScroll = () => {
    const list = messagesListRef.current;
    const distanceToBottom = list.scrollHeight - list.scrollTop - list.clientHeight;
    shouldAutoScrollRef.current = distanceToBottom <= AUTO_SCROLL_THRESHOLD;
};

// 2. 会话更新防抖：生成过程中不向父组件同步
useEffect(() => {
    if (hasTransientMessage) return; // transient 表示正在生成
    // 比较 signature，避免不必要的更新
    const signature = persistedMessages.map(msg => `${msg.id}|${msg.role}|${msg.content}`).join('||');
    if (signature === lastPersistedSignatureRef.current) return;
    onSessionUpdate(sessionId, persistedMessages);
}, [messages, sessionId, onSessionUpdate, hasTransientMessage]);

// 3. useRef 代替 useState 存不需要渲染的状态
const shouldAutoScrollRef = useRef(true);
const lastPersistedSignatureRef = useRef('');
```

#### 2. Markdown 渲染优化（MessageBubble.jsx）
```jsx
// 1. 流式内容 normalize 只做一次
const normalizedContent = useMemo(() => normalizeContent(content || ''), [content]);

// 2. 关键词高亮用正则，避免重复渲染
// 3. dangerouslySetInnerHTML 直接用，不要用 React 组件树模拟 Markdown
<div dangerouslySetInnerHTML={{ __html: htmlContent }} />
```

#### 3. 图片处理优化
```jsx
// ChatWindow.jsx - 图片预览 + 压缩
const imageFiles = files.filter(f => f.type === 'image');
// 前端预览用 URL.createObjectURL，不等到 base64
images: imageFiles.length > 0 ? imageFiles.map(f => f.preview) : undefined

// fileToBase64 可以加压缩逻辑
const fileToBase64 = (file) => new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
        // 可以在这里用 canvas 压缩图片
        resolve(reader.result.split(',')[1]);
    };
    reader.onerror = reject;
    reader.readAsDataURL(file);
});
```

#### 4. 其他通用优化
- **React 19**：自动批处理、useMemo/useCallback
- **代码分割**：管理员控制台路由懒加载
- **CSS 优化**：ChatWindow.css 按需加载，避免大样式文件
- **虚拟列表**：如果历史会话非常多，可以用 react-window

---

## 6. WebWorker 在 AI 应用中的应用

**面试官问**：介绍一下 WebWorker 的应用场景，在 AI 前端应用中它能解决什么问题？

**参考答案**：

### WebWorker 是什么
- 浏览器的后台线程，不阻塞主线程
- 不能操作 DOM，但可以做计算
- 通过 postMessage 和主线程通信

### 应用场景
1. **复杂计算**：大文件解析、加密解密
2. **数据处理**：大数据排序、过滤、统计
3. **AI 推理**：轻量级模型在前端推理（如 Transformers.js）
4. **流式解析**：SSE 数据解析、Markdown 解析

### AI 前端应用中能解决的问题（结合我的项目）

#### 1. Markdown 解析放 Worker（我的项目可以优化的点）
```jsx
// worker.js
import { marked } from 'marked';
self.onmessage = (e) => {
    const { content, references } = e.data;
    const html = marked.parse(content);
    self.postMessage({ html });
};

// 主线程
const worker = new Worker('./markdownWorker.js');
worker.postMessage({ content, references });
worker.onmessage = (e) => {
    setHtmlContent(e.data.html);
};
```

#### 2. 向量检索放 Worker（前端 RAG 可以做）
- 如果前端有本地向量库，可以用 Worker 做相似度计算
- 不阻塞聊天输入

#### 3. 图片压缩放 Worker
```jsx
// 我的项目中的 fileToBase64 可以放到 Worker
// 避免大图片压缩阻塞主线程
```

#### 4. 我的项目中目前没用到 Worker，但可以加的地方
- Markdown 解析
- 图片压缩/预处理
- 历史会话搜索

---

## 7. XSS 攻击防范

**面试官问**：如果 AI 返回的内容包含恶意脚本，前端如何防范 XSS 攻击？

**参考答案**（结合项目）：

### XSS 防范手段

#### 1. 我的项目中的做法（MessageBubble.jsx）
```jsx
// 1. 使用成熟的 Markdown 库：marked 默认会做一些转义
import { marked } from 'marked';

// 2. 注入引用标记时，属性用 data-，避免直接执行
const injectReferenceMarker = (content, references) => {
    return content.replace(/\[(\d+)](?!\()/g, (match, rawId) => {
        // 用 data-ref-id，而不是 onclick
        return `<button type="button"
            class="ref-chip"
            data-ref-id="${rawId}"
            aria-label="查看第${rawId}条参考依据">
            [${rawId}]
        </button>`;
    });
};

// 3. 点击事件通过事件委托，不是内联 script
const handleContentClick = (event) => {
    const target = event.target.closest('.ref-chip');
    if (!target || !onRefClick) return;
    const refId = target.getAttribute('data-ref-id');
    if (refId) onRefClick(refId);
};
```

#### 2. 其他防范手段
```jsx
// 4. DOMPurify 净化 HTML（推荐加上）
import DOMPurify from 'dompurify';
const htmlContent = DOMPurify.sanitize(marked.parse(markdownWithRefs));

// 5. Content Security Policy (CSP)
// 后端设置响应头：
// Content-Security-Policy: default-src 'self'; script-src 'none';
// 禁止内联 script，禁止 eval

// 6. 不使用 innerHTML，用 textContent（但 Markdown 必须用 innerHTML）
// 所以这个场景下只能用前几种方案
```

#### 3. AI 内容特殊防范
- **Prompt 层面**：后端系统提示词要求 AI 只返回 Markdown，不返回 script
- **输出过滤**：后端可以过滤 `<script>`、`javascript:` 等
- **Sandbox iframe**：如果是复杂内容，可以放 iframe 里渲染

---

## 8. 手写 EventBus

**面试官问**：手写一个简单的 EventBus（发布订阅模式）。

**参考答案**：

### 版本 1：基础版
```javascript
class EventBus {
    constructor() {
        this.events = {}; // { eventName: [callback1, callback2, ...] }
    }

    // 订阅
    on(eventName, callback) {
        if (!this.events[eventName]) {
            this.events[eventName] = [];
        }
        this.events[eventName].push(callback);
        // 返回取消订阅函数
        return () => this.off(eventName, callback);
    }

    // 取消订阅
    off(eventName, callback) {
        if (!this.events[eventName]) return;
        this.events[eventName] = this.events[eventName].filter(cb => cb !== callback);
    }

    // 发布
    emit(eventName, ...args) {
        if (!this.events[eventName]) return;
        this.events[eventName].forEach(callback => {
            try {
                callback(...args);
            } catch (e) {
                console.error('EventBus callback error:', e);
            }
        });
    }

    // 只订阅一次
    once(eventName, callback) {
        const onceCallback = (...args) => {
            callback(...args);
            this.off(eventName, onceCallback);
        };
        return this.on(eventName, onceCallback);
    }
}

// 使用示例
const bus = new EventBus();
const unsub = bus.on('message', (data) => console.log('收到消息:', data));
bus.emit('message', { text: 'Hello' });
unsub(); // 取消订阅
```

### 版本 2：TypeScript + 单例（进阶）
```typescript
type EventCallback = (...args: any[]) => void;

class EventBus {
    private static instance: EventBus;
    private events: Map<string, Set<EventCallback>> = new Map();

    static getInstance(): EventBus {
        if (!EventBus.instance) {
            EventBus.instance = new EventBus();
        }
        return EventBus.instance;
    }

    on<T extends any[]>(eventName: string, callback: (...args: T) => void): () => void {
        if (!this.events.has(eventName)) {
            this.events.set(eventName, new Set());
        }
        this.events.get(eventName)!.add(callback);
        return () => this.off(eventName, callback);
    }

    off(eventName: string, callback: EventCallback): void {
        this.events.get(eventName)?.delete(callback);
    }

    emit<T extends any[]>(eventName: string, ...args: T): void {
        this.events.get(eventName)?.forEach(callback => {
            try {
                callback(...args);
            } catch (e) {
                console.error('EventBus error:', e);
            }
        });
    }
}

// React 中使用的 Hook
function useEventBus<T>(eventName: string, callback: (...args: T[]) => void) {
    useEffect(() => {
        const bus = EventBus.getInstance();
        const unsub = bus.on(eventName, callback);
        return unsub;
    }, [eventName, callback]);
}
```

### 在 AI 聊天项目中的应用
```jsx
// 组件间通信：消息流式更新、侧边栏状态同步
const bus = new EventBus();

// 消息组件订阅
bus.on('stream:delta', (content) => { /* 更新消息 */ });

// 发送组件发布
bus.emit('stream:delta', '新内容');
```

---

## 总结：AI 前端应用高频面试要点

1. **React 19 + Fiber**：时间切片、优先级调度，AI 高频更新不卡
2. **SSE 流式**：buffer 处理、增量渲染、Markdown 库选择
3. **RAG 前端工作**：引用展示、查询预处理、反馈收集
4. **性能优化**：useRef、防抖、虚拟列表、图片压缩
5. **WebWorker**：Markdown 解析、图片处理、前端推理
6. **XSS**：DOMPurify、CSP、事件委托
7. **设计模式**：发布订阅（EventBus）、单例
