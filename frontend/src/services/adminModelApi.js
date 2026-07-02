const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';
const MODEL_BASE = `${API_BASE}/admin/models-card`;

const getAuthHeaders = () => {
    const token = localStorage.getItem('token');
    return token ? { 'Authorization': `Bearer ${token}` } : {};
};

const handleResponse = async (response, defaultMessage) => {
    if (response.ok) {
        if (response.status === 204) return null;
        return response.json();
    }
    let message = defaultMessage;
    try {
        const body = await response.json();
        if (body?.message) message = body.message;
    } catch {
        // ignore
    }
    throw new Error(message);
};

/**
 * 卡片视图专用的 Model API。
 * 与 adminApi.listModels 不同：返回 DTO 包含卡片所需的展示字段
 * （icon / description / status / latencyMs / usedByAgentCount / isDefault / modelTypeLabel 等），
 * 不返回 apiKey 等敏感字段。
 */
const adminModelApi = {
    async listModels(params = {}) {
        const search = new URLSearchParams();
        if (params.modelType) search.append('modelType', params.modelType);
        const url = search.toString() ? `${MODEL_BASE}?${search}` : MODEL_BASE;
        const response = await fetch(url, { headers: getAuthHeaders() });
        return handleResponse(response, '获取模型列表失败');
    },

    async getModel(id) {
        const response = await fetch(`${MODEL_BASE}/${id}`, { headers: getAuthHeaders() });
        return handleResponse(response, '获取模型详情失败');
    },

    async createModel(payload) {
        const response = await fetch(MODEL_BASE, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
            body: JSON.stringify(payload)
        });
        return handleResponse(response, '创建模型失败');
    },

    async updateModel(id, payload) {
        const response = await fetch(`${MODEL_BASE}/${id}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
            body: JSON.stringify(payload)
        });
        return handleResponse(response, '更新模型失败');
    },

    async deleteModel(id) {
        const response = await fetch(`${MODEL_BASE}/${id}`, {
            method: 'DELETE',
            headers: getAuthHeaders()
        });
        return handleResponse(response, '删除模型失败');
    },

    async setDefault(id) {
        const response = await fetch(`${MODEL_BASE}/${id}/default`, {
            method: 'POST',
            headers: getAuthHeaders()
        });
        return handleResponse(response, '设置默认模型失败');
    },

    async testConnection(id) {
        const response = await fetch(`${MODEL_BASE}/${id}/test`, {
            method: 'POST',
            headers: getAuthHeaders()
        });
        return handleResponse(response, '测试模型连接失败');
    }
};

export { adminModelApi };
export default adminModelApi;
