const API_BASE = '/api';

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
            throw new Error('获取用户信息失败');
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

export const chatApi = {
    async sendMessage(message, conversationId) {
        const response = await fetch(`${API_BASE}/chat`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                ...getAuthHeaders()
            },
            body: JSON.stringify({ message, conversationId })
        });
        if (!response.ok) {
            throw new Error('发送消息失败');
        }
        return response.json();
    },

    createStreamRequest(message, conversationId) {
        return fetch(`${API_BASE}/chat/stream`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                ...getAuthHeaders()
            },
            body: JSON.stringify({ message, conversationId })
        });
    }
};

export { getAuthHeaders };
