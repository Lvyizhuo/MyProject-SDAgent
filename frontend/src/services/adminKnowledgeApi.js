const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';
const ADMIN_KNOWLEDGE_BASE = `${API_BASE}/admin/knowledge`;

const getAuthHeaders = () => {
    const token = localStorage.getItem('token');
    return token ? { 'Authorization': `Bearer ${token}` } : {};
};

const adminKnowledgeApi = {
    async getFolderTree() {
        const response = await fetch(`${ADMIN_KNOWLEDGE_BASE}/folders`, {
            headers: getAuthHeaders()
        });
        if (!response.ok) {
            throw new Error('获取文件夹树失败');
        }
        return response.json();
    },

    async createFolder(data) {
        const response = await fetch(`${ADMIN_KNOWLEDGE_BASE}/folders`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                ...getAuthHeaders()
            },
            body: JSON.stringify(data)
        });
        if (!response.ok) {
            throw new Error('创建文件夹失败');
        }
        return response.json();
    },

    async updateFolder(id, data) {
        const response = await fetch(`${ADMIN_KNOWLEDGE_BASE}/folders/${id}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                ...getAuthHeaders()
            },
            body: JSON.stringify(data)
        });
        if (!response.ok) {
            throw new Error('更新文件夹失败');
        }
        return response.json();
    },

    async deleteFolder(id) {
        const response = await fetch(`${ADMIN_KNOWLEDGE_BASE}/folders/${id}`, {
            method: 'DELETE',
            headers: getAuthHeaders()
        });
        if (!response.ok) {
            throw new Error('删除文件夹失败');
        }
    },

    async listDocuments(params = {}) {
        const searchParams = new URLSearchParams();
        Object.entries(params).forEach(([key, value]) => {
            if (value != null) {
                searchParams.append(key, value);
            }
        });
        const response = await fetch(`${ADMIN_KNOWLEDGE_BASE}/documents?${searchParams}`, {
            headers: getAuthHeaders()
        });
        if (!response.ok) {
            throw new Error('获取文档列表失败');
        }
        return response.json();
    },

    async getDocument(id) {
        const response = await fetch(`${ADMIN_KNOWLEDGE_BASE}/documents/${id}`, {
            headers: getAuthHeaders()
        });
        if (!response.ok) {
            throw new Error('获取文档详情失败');
        }
        return response.json();
    },

    async getDocumentChunks(id, params = {}) {
        const searchParams = new URLSearchParams();
        Object.entries(params).forEach(([key, value]) => {
            if (value != null) {
                searchParams.append(key, value);
            }
        });

        const response = await fetch(`${ADMIN_KNOWLEDGE_BASE}/documents/${id}/chunks?${searchParams}`, {
            headers: getAuthHeaders()
        });
        if (!response.ok) {
            throw new Error('获取分段结果失败');
        }
        return response.json();
    },

    async uploadDocument(formData, onProgress) {
        return new Promise((resolve, reject) => {
            const xhr = new XMLHttpRequest();

            xhr.upload.addEventListener('progress', (e) => {
                if (e.lengthComputable && onProgress) {
                    onProgress(Math.round((e.loaded / e.total) * 100));
                }
            });

            xhr.addEventListener('load', () => {
                if (xhr.status >= 200 && xhr.status < 300) {
                    resolve(JSON.parse(xhr.responseText));
                } else {
                    let message = '上传文档失败';
                    try {
                        const errorBody = JSON.parse(xhr.responseText);
                        if (errorBody?.message) {
                            message = errorBody.message;
                        }
                    } catch {
                        if (xhr.responseText) {
                            message = xhr.responseText;
                        }
                    }
                    reject(new Error(message));
                }
            });

            xhr.addEventListener('error', () => {
                reject(new Error('上传文档失败'));
            });

            xhr.addEventListener('timeout', () => {
                reject(new Error('上传超时，请重试'));
            });

            xhr.open('POST', `${ADMIN_KNOWLEDGE_BASE}/documents`);
            xhr.timeout = 60000;
            const token = localStorage.getItem('token');
            if (token) {
                xhr.setRequestHeader('Authorization', `Bearer ${token}`);
            }
            xhr.send(formData);
        });
    },

    async extractDocumentMetadata(file) {
        const formData = new FormData();
        formData.append('file', file);

        const response = await fetch(`${ADMIN_KNOWLEDGE_BASE}/documents/extract-metadata`, {
            method: 'POST',
            headers: getAuthHeaders(),
            body: formData
        });
        if (!response.ok) {
            throw new Error('智能提取失败');
        }
        return response.json();
    },

    async downloadDocument(id) {
        const response = await fetch(`${ADMIN_KNOWLEDGE_BASE}/documents/${id}/download`, {
            headers: getAuthHeaders()
        });
        if (!response.ok) {
            throw new Error('下载文档失败');
        }
        return response.blob();
    },

    async getDocumentPreview(id) {
        const response = await fetch(`${ADMIN_KNOWLEDGE_BASE}/documents/${id}/preview`, {
            headers: getAuthHeaders()
        });
        if (!response.ok) {
            throw new Error('获取文档预览失败');
        }
        return response.json();
    },

    async deleteDocument(id) {
        const response = await fetch(`${ADMIN_KNOWLEDGE_BASE}/documents/${id}`, {
            method: 'DELETE',
            headers: getAuthHeaders()
        });
        if (!response.ok) {
            throw new Error('删除文档失败');
        }
    },

    async reingestDocument(id) {
        const response = await fetch(`${ADMIN_KNOWLEDGE_BASE}/documents/${id}/reingest`, {
            method: 'POST',
            headers: getAuthHeaders()
        });
        if (!response.ok) {
            throw new Error('重新处理文档失败');
        }
        return response.json();
    },

    async batchDeleteDocuments(ids) {
        const response = await fetch(`${ADMIN_KNOWLEDGE_BASE}/documents/batch-delete`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                ...getAuthHeaders()
            },
            body: JSON.stringify({ ids })
        });
        if (!response.ok) {
            throw new Error('批量删除文档失败');
        }
    },

    async getConfig() {
        const response = await fetch(`${ADMIN_KNOWLEDGE_BASE}/config`, {
            headers: getAuthHeaders()
        });
        if (!response.ok) {
            throw new Error('获取配置失败');
        }
        return response.json();
    },

    async updateConfig(data) {
        const response = await fetch(`${ADMIN_KNOWLEDGE_BASE}/config`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                ...getAuthHeaders()
            },
            body: JSON.stringify(data)
        });
        if (!response.ok) {
            throw new Error('更新配置失败');
        }
        return response.json();
    },

    async getEmbeddingModels() {
        const response = await fetch(`${ADMIN_KNOWLEDGE_BASE}/embedding-models`, {
            headers: getAuthHeaders()
        });
        if (!response.ok) {
            throw new Error('获取嵌入模型列表失败');
        }
        return response.json();
    }
};

export default adminKnowledgeApi;
