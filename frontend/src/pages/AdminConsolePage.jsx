import React, { useState, useEffect } from 'react';
import { adminApi } from '../services/adminApi';
import AdminNavbar from '../components/admin/AdminNavbar';
import ConfigPanel from '../components/admin/ConfigPanel';
import PreviewPanel from '../components/admin/PreviewPanel';
import KnowledgeBaseTab from '../components/admin/KnowledgeBaseTab';
import ToolsTab from '../components/admin/ToolsTab';
import './AdminConsolePage.css';

const AdminConsolePage = () => {
    const [activeTab, setActiveTab] = useState('agent');
    const [config, setConfig] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        if (activeTab === 'agent') {
            loadConfig();
        }
    }, [activeTab]);

    const loadConfig = async () => {
        setLoading(true);
        try {
            const data = await adminApi.getAgentConfig();
            setConfig(data);
            setError('');
        } catch (err) {
            setError(err.message || '获取配置失败');
        } finally {
            setLoading(false);
        }
    };

    const handleSave = async (newConfig) => {
        try {
            const savedConfig = await adminApi.updateAgentConfig(newConfig);
            setConfig(savedConfig);
            return { success: true };
        } catch (err) {
            return { success: false, message: err.message };
        }
    };

    const handleReset = async () => {
        try {
            const resetConfig = await adminApi.resetAgentConfig();
            setConfig(resetConfig);
            return { success: true };
        } catch (err) {
            return { success: false, message: err.message };
        }
    };

    const renderContent = () => {
        switch (activeTab) {
            case 'agent':
                if (loading) {
                    return (
                        <div className="admin-tab-loading">
                            <div className="loading-spinner">加载中...</div>
                        </div>
                    );
                }
                if (error && !config) {
                    return (
                        <div className="admin-tab-error">
                            <div className="error-message">
                                <h3>出错了</h3>
                                <p>{error}</p>
                                <button onClick={loadConfig}>重试</button>
                            </div>
                        </div>
                    );
                }
                return (
                    <div className="admin-layout">
                        <div className="admin-left-panel">
                            <ConfigPanel
                                config={config}
                                onSave={handleSave}
                                onReset={handleReset}
                            />
                        </div>
                        <div className="admin-right-panel">
                            <PreviewPanel config={config} />
                        </div>
                    </div>
                );
            case 'knowledge':
                return (
                    <div className="admin-layout single-panel">
                        <div className="admin-full-panel">
                            <KnowledgeBaseTab />
                        </div>
                    </div>
                );
            case 'tools':
                return (
                    <div className="admin-layout single-panel">
                        <div className="admin-full-panel">
                            <ToolsTab />
                        </div>
                    </div>
                );
            default:
                return null;
        }
    };

    return (
        <div className="admin-console-page">
            <AdminNavbar activeTab={activeTab} onTabChange={setActiveTab} />
            <main className="admin-main-content">
                {renderContent()}
            </main>
        </div>
    );
};

export default AdminConsolePage;
