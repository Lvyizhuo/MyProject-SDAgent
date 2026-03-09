const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';

import { getAuthHeaders } from './api';

export const adminApi = {
    async login(username, password) {
        const response = await fetch(`${API_BASE}/admin/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({ message: '管理员登录失败' }));
            throw new Error(error.message || '管理员登录失败');
        }
        return response.json();
    },

    async changePassword(oldPassword, newPassword, confirmPassword) {
        const response = await fetch(`${API_BASE}/admin/auth/change-password`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                ...getAuthHeaders()
            },
            body: JSON.stringify({ oldPassword, newPassword, confirmPassword })
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({ message: '修改密码失败' }));
            throw new Error(error.message || '修改密码失败');
        }
        return response.json();
    },

    async getAgentConfig() {
        const response = await fetch(`${API_BASE}/admin/agent-config`, {
            headers: getAuthHeaders()
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({ message: '获取配置失败' }));
            throw new Error(error.message || '获取配置失败');
        }
        return response.json();
    },

    async updateAgentConfig(config) {
        const response = await fetch(`${API_BASE}/admin/agent-config`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                ...getAuthHeaders()
            },
            body: JSON.stringify(config)
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({ message: '更新配置失败' }));
            throw new Error(error.message || '更新配置失败');
        }
        return response.json();
    },

    async resetAgentConfig() {
        const response = await fetch(`${API_BASE}/admin/agent-config/reset`, {
            method: 'POST',
            headers: getAuthHeaders()
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({ message: '重置配置失败' }));
            throw new Error(error.message || '重置配置失败');
        }
        return response.json();
    },

    async testAgentConfig(message, sessionId) {
        const response = await fetch(`${API_BASE}/admin/agent-config/test`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                ...getAuthHeaders()
            },
            body: JSON.stringify({ message, sessionId })
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({ message: '测试对话失败' }));
            throw new Error(error.message || '测试对话失败');
        }
        return response.json();
    },

    // ========== 模型管理 API ==========

    async getModels(type) {
        const url = type ? `${API_BASE}/admin/models?type=${type}` : `${API_BASE}/admin/models`;
        const response = await fetch(url, {
            headers: getAuthHeaders()
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({ message: '获取模型列表失败' }));
            throw new Error(error.message || '获取模型列表失败');
        }
        return response.json();
    },

    async getModelById(id) {
        const response = await fetch(`${API_BASE}/admin/models/${id}`, {
            headers: getAuthHeaders()
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({ message: '获取模型详情失败' }));
            throw new Error(error.message || '获取模型详情失败');
        }
        return response.json();
    },

    async createModel(model) {
        const response = await fetch(`${API_BASE}/admin/models`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                ...getAuthHeaders()
            },
            body: JSON.stringify(model)
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({ message: '创建模型失败' }));
            throw new Error(error.message || '创建模型失败');
        }
        return response.json();
    },

    async updateModel(id, model) {
        const response = await fetch(`${API_BASE}/admin/models/${id}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                ...getAuthHeaders()
            },
            body: JSON.stringify(model)
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({ message: '更新模型失败' }));
            throw new Error(error.message || '更新模型失败');
        }
        return response.json();
    },

    async deleteModel(id) {
        const response = await fetch(`${API_BASE}/admin/models/${id}`, {
            method: 'DELETE',
            headers: getAuthHeaders()
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({ message: '删除模型失败' }));
            throw new Error(error.message || '删除模型失败');
        }
    },

    async setDefaultModel(id) {
        const response = await fetch(`${API_BASE}/admin/models/${id}/set-default`, {
            method: 'PUT',
            headers: getAuthHeaders()
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({ message: '设置默认模型失败' }));
            throw new Error(error.message || '设置默认模型失败');
        }
        return response.json();
    },

    async testModelConnection(id) {
        const response = await fetch(`${API_BASE}/admin/models/${id}/test`, {
            method: 'POST',
            headers: getAuthHeaders()
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({ message: '测试连接失败' }));
            throw new Error(error.message || '测试连接失败');
        }
        return response.json();
    },

    async getModelOptions() {
        const response = await fetch(`${API_BASE}/admin/models/options`, {
            headers: getAuthHeaders()
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({ message: '获取模型选项失败' }));
            throw new Error(error.message || '获取模型选项失败');
        }
        return response.json();
    }
};
