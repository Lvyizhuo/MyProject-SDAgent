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
            const error = await response.json().catch(() => ({ message: '创建文件夹失败' }));
            throw new Error(error.message || '创建文件夹失败');
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
            const error = await response.json().catch(() => ({ message: '更新文件夹失败' }));
            throw new Error(error.message || '更新文件夹失败');
        }
        return response.json();
    },

    async deleteFolder(id) {
        const response = await fetch(`${ADMIN_KNOWLEDGE_BASE}/folders/${id}`, {
            method: 'DELETE',
            headers: getAuthHeaders()
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({ message: '删除文件夹失败' }));
            throw new Error(error.message || '删除文件夹失败');
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

    async listDocumentSelection(params = {}) {
        const searchParams = new URLSearchParams();
        Object.entries(params).forEach(([key, value]) => {
            if (value != null) {
                searchParams.append(key, value);
            }
        });
        const response = await fetch(`${ADMIN_KNOWLEDGE_BASE}/documents/selection?${searchParams}`, {
            headers: getAuthHeaders()
        });
        if (!response.ok) {
            throw new Error('获取文档选择范围失败');
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
            const uploadTimeoutMs = 10 * 60 * 1000;

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
                reject(new Error('上传等待超时。文档可能仍在后台处理中，请稍后刷新列表查看状态。'));
            });

            xhr.open('POST', `${ADMIN_KNOWLEDGE_BASE}/documents`);
            xhr.timeout = uploadTimeoutMs;
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

    async exportArchive() {
        const response = await fetch(`${ADMIN_KNOWLEDGE_BASE}/archive/export`, {
            headers: getAuthHeaders()
        });
        if (!response.ok) {
            throw new Error('导出知识库失败');
        }
        return response.blob();
    },

    async importArchive(file) {
        const formData = new FormData();
        formData.append('file', file);

        const response = await fetch(`${ADMIN_KNOWLEDGE_BASE}/archive/import`, {
            method: 'POST',
            headers: getAuthHeaders(),
            body: formData
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({}));
            throw new Error(error.message || '导入知识库失败');
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
            const error = await response.json().catch(() => ({}));
            throw new Error(error.message || '批量删除文档失败');
        }
    },

    async batchMoveDocuments(ids, targetFolderId) {
        const response = await fetch(`${ADMIN_KNOWLEDGE_BASE}/documents/batch-move`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                ...getAuthHeaders()
            },
            body: JSON.stringify({ ids, targetFolderId })
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({}));
            throw new Error(error.message || '批量移动文档失败');
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
    },

    async createUrlImport(data) {
        const response = await fetch(`${ADMIN_KNOWLEDGE_BASE}/url-imports`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                ...getAuthHeaders()
            },
            body: JSON.stringify(data)
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({}));
            throw new Error(error.message || '创建网站导入任务失败');
        }
        return response.json();
    },

    async listUrlImports() {
        const response = await fetch(`${ADMIN_KNOWLEDGE_BASE}/url-imports`, {
            headers: getAuthHeaders()
        });
        if (!response.ok) {
            throw new Error('获取网站导入任务失败');
        }
        return response.json();
    },

    async getUrlImportItem(id) {
        const response = await fetch(`${ADMIN_KNOWLEDGE_BASE}/url-imports/${id}`, {
            headers: getAuthHeaders()
        });
        if (!response.ok) {
            throw new Error('获取待入库内容详情失败');
        }
        return response.json();
    },

    async confirmUrlImport(id, data) {
        const response = await fetch(`${ADMIN_KNOWLEDGE_BASE}/url-imports/${id}/confirm`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                ...getAuthHeaders()
            },
            body: JSON.stringify(data)
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({}));
            throw new Error(error.message || '确认入库失败');
        }
        return response.json();
    },

    async batchConfirmUrlImports(data) {
        const response = await fetch(`${ADMIN_KNOWLEDGE_BASE}/url-imports/batch-confirm`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                ...getAuthHeaders()
            },
            body: JSON.stringify(data)
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({}));
            throw new Error(error.message || '批量确认入库失败');
        }
        return response.json();
    },

    async rejectUrlImport(id, data) {
        const response = await fetch(`${ADMIN_KNOWLEDGE_BASE}/url-imports/${id}/reject`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                ...getAuthHeaders()
            },
            body: JSON.stringify(data)
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({}));
            throw new Error(error.message || '驳回待入库内容失败');
        }
    },

    async cancelUrlImport(id) {
        const response = await fetch(`${ADMIN_KNOWLEDGE_BASE}/url-imports/${id}/cancel`, {
            method: 'POST',
            headers: getAuthHeaders()
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({}));
            throw new Error(error.message || '取消网站导入任务失败');
        }
    },

    async deleteUrlImport(id) {
        const response = await fetch(`${ADMIN_KNOWLEDGE_BASE}/url-imports/${id}`, {
            method: 'DELETE',
            headers: getAuthHeaders()
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({}));
            throw new Error(error.message || '删除网站导入任务失败');
        }
    },

    async deleteUrlImportItem(id) {
        const response = await fetch(`${ADMIN_KNOWLEDGE_BASE}/url-import-items/${id}`, {
            method: 'DELETE',
            headers: getAuthHeaders()
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({}));
            throw new Error(error.message || '删除待入库内容失败');
        }
    }
};

export default adminKnowledgeApi;
