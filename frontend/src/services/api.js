const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';

const getAuthHeaders = () => {
    const token = localStorage.getItem('token');
    return token ? { 'Authorization': `Bearer ${token}` } : {};
};

export const authApi = {
    async register(username, password) {
        const response = await fetch(`${API_BASE}/auth/register`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({ message: '注册失败' }));
            throw new Error(error.message || '注册失败');
        }
        return response.json();
    },

    async login(username, password) {
        const response = await fetch(`${API_BASE}/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({ message: '登录失败' }));
            throw new Error(error.message || '登录失败');
        }
        return response.json();
    },

    async getCurrentUser() {
        const response = await fetch(`${API_BASE}/auth/me`, {
            headers: getAuthHeaders()
        });
        if (!response.ok) {
            // 401 = token 无效/过期；其他非 2xx 可能是后端暂时不可用
            const err = new Error('获取用户信息失败');
            err.status = response.status;
            throw err;
        }
        return response.json();
    }
};

export const conversationApi = {
    async getSessions() {
        const response = await fetch(`${API_BASE}/conversations`, {
            headers: getAuthHeaders()
        });
        if (!response.ok) {
            throw new Error('获取会话列表失败');
        }
        return response.json();
    },

    async getSession(sessionId) {
        const response = await fetch(`${API_BASE}/conversations/${sessionId}`, {
            headers: getAuthHeaders()
        });
        if (!response.ok) {
            throw new Error('获取会话失败');
        }
        return response.json();
    },

    async deleteSession(sessionId) {
        const response = await fetch(`${API_BASE}/conversations/${sessionId}`, {
            method: 'DELETE',
            headers: getAuthHeaders()
        });
        if (!response.ok) {
            throw new Error('删除会话失败');
        }
    }
};

export const publicConfigApi = {
    async getAgentConfig() {
        const response = await fetch(`${API_BASE}/public/config/agent`);
        if (!response.ok) {
            throw new Error('获取配置失败');
        }
        return response.json();
    }
};

export const chatApi = {
    async sendMessage(message, conversationId, imageBase64List = null, location = null) {
        const body = { message, conversationId };
        if (imageBase64List && imageBase64List.length > 0) {
            body.imageBase64List = imageBase64List;
            body.imageFormat = 'jpeg';
        }
        if (location?.latitude != null && location?.longitude != null) {
            body.latitude = location.latitude;
            body.longitude = location.longitude;
            if (location.accuracy != null) {
                body.locationAccuracy = location.accuracy;
            }
        }
        const response = await fetch(`${API_BASE}/chat`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                ...getAuthHeaders()
            },
            body: JSON.stringify(body)
        });
        if (!response.ok) {
            throw new Error('发送消息失败');
        }
        return response.json();
    },

    /**
     * 流式发送消息（SSE）。
     *
     * 通过 fetch + ReadableStream 解析 `text/event-stream`，逐事件回调：
     *   - onStage(stage)      阶段进度：analyzing / planning / tool / retrieving / generating
     *   - onDelta(content)    增量文本
     *   - onDone(result)      最终结果 { id, conversationId, content, references }
     *   - onError(message)    失败信息
     *
     * @param {AbortSignal} [options.signal] 外部取消信号
     * @param {number} [options.timeoutMs]    超时上限（默认 180s，与后端 SseEmitter 超时一致）
     * @returns {Promise<{id, conversationId, content, references}>} 流结束后的完整结果
     */
    async sendMessageStream(message, conversationId, { onStage, onDelta, onDone, onError, signal, timeoutMs = 180000, imageBase64List = null, location = null } = {}) {
        const body = { message, conversationId };
        if (imageBase64List && imageBase64List.length > 0) {
            body.imageBase64List = imageBase64List;
            body.imageFormat = 'jpeg';
        }
        if (location?.latitude != null && location?.longitude != null) {
            body.latitude = location.latitude;
            body.longitude = location.longitude;
            if (location?.accuracy != null) {
                body.locationAccuracy = location.accuracy;
            }
        }

        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(new Error('请求超时')), timeoutMs);
        const abortFromCaller = () => controller.abort(signal?.reason);
        if (signal) {
            if (signal.aborted) {
                abortFromCaller();
            } else {
                signal.addEventListener('abort', abortFromCaller, { once: true });
            }
        }

        try {
            const response = await fetch(`${API_BASE}/chat/stream`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'text/event-stream',
                    ...getAuthHeaders()
                },
                body: JSON.stringify(body),
                signal: controller.signal
            });

            if (!response.ok || !response.body) {
                throw new Error(`发送消息失败（${response.status}）`);
            }

            const reader = response.body.getReader();
            const decoder = new TextDecoder('utf-8');
            let buffer = '';
            let result = null;

            // SSE 帧以空行分隔；data 行可能有多行，需要拼接
            const handleFrame = (frame) => {
                const trimmed = frame.trim();
                if (!trimmed) return;
                let eventName = 'message';
                const dataLines = [];
                for (const line of trimmed.split('\n')) {
                    if (line.startsWith('event:')) {
                        eventName = line.slice(6).trim();
                    } else if (line.startsWith('data:')) {
                        dataLines.push(line.slice(5).trim());
                    }
                }
                if (!dataLines.length) return;
                let payload;
                try {
                    payload = JSON.parse(dataLines.join('\n'));
                } catch {
                    payload = { content: dataLines.join('\n') };
                }
                if (eventName === 'delta') {
                    onDelta?.(payload.content ?? '');
                } else if (eventName === 'stage') {
                    onStage?.(payload.stage);
                } else if (eventName === 'done') {
                    result = payload;
                    onDone?.(payload);
                } else if (eventName === 'error') {
                    onError?.(payload.message || '服务异常');
                }
            };

            while (true) {
                const { value, done } = await reader.read();
                if (done) break;
                buffer += decoder.decode(value, { stream: true });
                let boundary;
                while ((boundary = buffer.indexOf('\n\n')) >= 0) {
                    const frame = buffer.slice(0, boundary);
                    buffer = buffer.slice(boundary + 2);
                    handleFrame(frame);
                }
            }
            if (buffer.trim()) {
                handleFrame(buffer);
            }

            if (!result) {
                throw new Error('流式响应未返回最终结果');
            }
            return result;
        } catch (error) {
            if (error?.name === 'AbortError') {
                onError?.(signal?.reason?.message || '请求已取消');
            } else {
                onError?.(error?.message || '发送消息失败');
            }
            throw error;
        } finally {
            clearTimeout(timer);
            if (signal) {
                signal.removeEventListener('abort', abortFromCaller);
            }
        }
    }
};

export { getAuthHeaders };
