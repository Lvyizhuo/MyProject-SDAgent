const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';
const MODEL_BASE = `${API_BASE}/admin/models`;

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
 * 管理员控制台「模型」tab 用的 API。
 * 复用 docs/api/admin-models.md 描述的标准 ModelProvider 接口（/api/admin/models）。
 * 返回字段与 ModelProviderResponse 一致：name / type / provider / apiUrl / apiKey（脱敏）/ modelName / builtinCode /
 *  dimensions / temperature / maxTokens / topP / isDefault / isEnabled / builtIn / createdAt / updatedAt
 */
const adminModelApi = {
    async listModels(params = {}) {
        const search = new URLSearchParams();
        if (params.type) search.append('type', params.type);
        const url = search.toString() ? `${MODEL_BASE}?${search}` : MODEL_BASE;
        const response = await fetch(url, { headers: getAuthHeaders() });
        return handleResponse(response, '获取模型列表失败');
    },

    async getModel(id) {
        const response = await fetch(`${MODEL_BASE}/${id}`, { headers: getAuthHeaders() });
        return handleResponse(response, '获取模型详情失败');
    },

    async getModelOptions() {
        const response = await fetch(`${MODEL_BASE}/options`, { headers: getAuthHeaders() });
        return handleResponse(response, '获取模型下拉选项失败');
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
        const response = await fetch(`${MODEL_BASE}/${id}/set-default`, {
            method: 'PUT',
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
