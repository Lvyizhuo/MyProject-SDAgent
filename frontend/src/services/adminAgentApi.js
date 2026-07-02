const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';
const AGENT_BASE = `${API_BASE}/admin/agents`;

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
        // 非 JSON 错误体
    }
    throw new Error(message);
};

const adminAgentApi = {
    async listAgents() {
        const response = await fetch(AGENT_BASE, {
            headers: getAuthHeaders()
        });
        return handleResponse(response, '获取智能体列表失败');
    },

    async getAgent(id) {
        const response = await fetch(`${AGENT_BASE}/${id}`, {
            headers: getAuthHeaders()
        });
        return handleResponse(response, '获取智能体详情失败');
    },

    async createAgent(payload) {
        const response = await fetch(AGENT_BASE, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                ...getAuthHeaders()
            },
            body: JSON.stringify(payload)
        });
        return handleResponse(response, '创建智能体失败');
    },

    async updateAgent(id, payload) {
        const response = await fetch(`${AGENT_BASE}/${id}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                ...getAuthHeaders()
            },
            body: JSON.stringify(payload)
        });
        return handleResponse(response, '更新智能体失败');
    },

    async deleteAgent(id) {
        const response = await fetch(`${AGENT_BASE}/${id}`, {
            method: 'DELETE',
            headers: getAuthHeaders()
        });
        return handleResponse(response, '删除智能体失败');
    },

    async setDefault(id) {
        const response = await fetch(`${AGENT_BASE}/${id}/default`, {
            method: 'POST',
            headers: getAuthHeaders()
        });
        return handleResponse(response, '设置默认智能体失败');
    },

    async duplicateAgent(id) {
        const response = await fetch(`${AGENT_BASE}/${id}/duplicate`, {
            method: 'POST',
            headers: getAuthHeaders()
        });
        return handleResponse(response, '复制智能体失败');
    }
};

export { adminAgentApi };
export default adminAgentApi;
