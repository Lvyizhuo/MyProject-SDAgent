const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';
const TOOL_BASE = `${API_BASE}/admin/tools`;

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

const adminToolApi = {
    async listTools() {
        const response = await fetch(TOOL_BASE, {
            headers: getAuthHeaders()
        });
        return handleResponse(response, '获取工具列表失败');
    },

    async getTool(id) {
        const response = await fetch(`${TOOL_BASE}/${id}`, {
            headers: getAuthHeaders()
        });
        return handleResponse(response, '获取工具详情失败');
    },

    async createTool(payload) {
        const response = await fetch(TOOL_BASE, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                ...getAuthHeaders()
            },
            body: JSON.stringify(payload)
        });
        return handleResponse(response, '创建工具失败');
    },

    async updateTool(id, payload) {
        const response = await fetch(`${TOOL_BASE}/${id}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                ...getAuthHeaders()
            },
            body: JSON.stringify(payload)
        });
        return handleResponse(response, '更新工具失败');
    },

    async deleteTool(id) {
        const response = await fetch(`${TOOL_BASE}/${id}`, {
            method: 'DELETE',
            headers: getAuthHeaders()
        });
        return handleResponse(response, '删除工具失败');
    },

    async setEnabled(id, enabled) {
        const response = await fetch(`${TOOL_BASE}/${id}/enabled`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                ...getAuthHeaders()
            },
            body: JSON.stringify({ enabled })
        });
        return handleResponse(response, '切换启用状态失败');
    },

    async testTool(id) {
        const response = await fetch(`${TOOL_BASE}/${id}/test`, {
            method: 'POST',
            headers: getAuthHeaders()
        });
        return handleResponse(response, '测试工具失败');
    }
};

export { adminToolApi };
export default adminToolApi;
