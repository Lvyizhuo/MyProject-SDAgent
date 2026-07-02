import React, { useEffect, useState } from 'react';
import {
    Bot,
    BookOpen,
    Wrench,
    Cpu,
    BarChart3,
    MessageSquare,
    ChevronLeft,
    ChevronRight
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import './AdminSidebar.css';

const NAV_ITEMS = [
    { id: 'agent', label: '智能体', icon: Bot, group: 'workspace' },
    { id: 'knowledge', label: '知识库', icon: BookOpen, group: 'workspace', badge: '3,562' },
    { id: 'tools', label: '工具', icon: Wrench, group: 'workspace', badge: '3' },
    { id: 'models', label: '模型', icon: Cpu, group: 'workspace', badge: '4' }
];

const ANALYTICS_ITEMS = [
    { id: 'analytics', label: '对话分析', icon: BarChart3, group: 'analytics' },
    { id: 'feedback', label: '用户反馈', icon: MessageSquare, group: 'analytics' }
];

const GROUP_LABELS = {
    workspace: '工作区',
    analytics: '分析'
};

const readCollapsedFromStorage = () => {
    try {
        return window.localStorage.getItem('admin-sidebar-collapsed') === '1';
    } catch {
        return false;
    }
};

const AdminSidebar = ({
    activeTab,
    onTabChange,
    documentCount = 0,
    modelCount = 0,
    toolCount = 0,
    onCollapseChange
}) => {
    const navigate = useNavigate();
    const { user, logout } = useAuth();
    const [collapsed, setCollapsed] = useState(readCollapsedFromStorage);

    useEffect(() => {
        if (typeof onCollapseChange === 'function') {
            onCollapseChange(collapsed);
        }
    }, [collapsed, onCollapseChange]);

    const toggleCollapsed = () => {
        setCollapsed((prev) => {
            const next = !prev;
            try {
                window.localStorage.setItem('admin-sidebar-collapsed', next ? '1' : '0');
            } catch {
                // 忽略存储异常
            }
            return next;
        });
    };

    const handleLogout = () => {
        logout();
        navigate('/home');
    };

    // 合并并补充动态徽章
    const enriched = (items) =>
        items.map((item) => {
            if (item.id === 'knowledge' && documentCount) {
                return { ...item, badge: documentCount.toLocaleString('zh-CN') };
            }
            if (item.id === 'models' && modelCount) {
                return { ...item, badge: String(modelCount) };
            }
            if (item.id === 'tools' && toolCount) {
                return { ...item, badge: String(toolCount) };
            }
            return item;
        });

    const renderItem = (item) => {
        const Icon = item.icon;
        const isActive = activeTab === item.id;
        return (
            <button
                key={item.id}
                className={`admin-nav-item ${isActive ? 'active' : ''}`}
                onClick={() => onTabChange(item.id)}
                title={collapsed ? item.label : undefined}
            >
                <Icon className="admin-nav-icon" size={16} />
                <span className="admin-nav-label">{item.label}</span>
                {item.badge != null && (
                    <span className="admin-nav-num">{item.badge}</span>
                )}
            </button>
        );
    };

    const groups = [
        { key: 'workspace', items: enriched(NAV_ITEMS) },
        { key: 'analytics', items: ANALYTICS_ITEMS }
    ];

    return (
        <>
            <aside className={`admin-sidebar ${collapsed ? 'collapsed' : ''}`}>
                <div className="admin-sidebar-brand">
                    <button className="brand" onClick={() => navigate('/home')} title="返回首页">
                        <span className="brand-mark">
                            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                                <circle cx="12" cy="12" r="10" />
                                <circle cx="12" cy="12" r="6" />
                                <circle cx="12" cy="12" r="2" />
                            </svg>
                        </span>
                        {!collapsed && <span className="brand-text">AI 政策通</span>}
                    </button>
                    <button
                        className="admin-sidebar-toggle"
                        onClick={toggleCollapsed}
                        title="收起"
                        aria-label="收起侧栏"
                    >
                        <ChevronLeft size={14} />
                    </button>
                </div>

                <nav className="admin-sidebar-nav" aria-label="管理导航">
                    {groups.map((group) => (
                        <div key={group.key} className="admin-sidebar-group">
                            {!collapsed && (
                                <div className="admin-sidebar-section">{GROUP_LABELS[group.key]}</div>
                            )}
                            {group.items.map(renderItem)}
                        </div>
                    ))}
                </nav>

                <div className="admin-sidebar-bottom">
                    <button className="admin-user-card" onClick={handleLogout} title={collapsed ? (user?.username || '退出登录') : undefined}>
                        <span className="admin-user-avatar">
                            {(user?.username || 'A').slice(0, 1).toUpperCase()}
                        </span>
                        {!collapsed && (
                            <span className="admin-user-meta">
                                <span className="admin-user-name">{user?.username || 'admin'}</span>
                                <span className="admin-user-role">超级管理员</span>
                            </span>
                        )}
                    </button>
                </div>
            </aside>

            {/* 收起后：贴侧栏右边缘的浮动展开按钮，永远可点 */}
            {collapsed && (
                <button
                    className="admin-sidebar-float-expand"
                    onClick={toggleCollapsed}
                    title="展开侧栏"
                    aria-label="展开侧栏"
                >
                    <ChevronRight size={14} />
                </button>
            )}
        </>
    );
};

export default AdminSidebar;
