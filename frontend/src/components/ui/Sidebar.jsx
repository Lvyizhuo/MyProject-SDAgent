import React, { useState } from 'react';
import { Plus, Search, MoreVertical, Settings, Layers, X } from 'lucide-react';
import '../ui/chat-ui.css';

const formatDate = (timestamp) => {
    if (!timestamp) return '未知';
    const date = new Date(timestamp);
    const now = new Date();
    const diffDays = Math.floor((now - date) / (1000 * 60 * 60 * 24));

    if (diffDays === 0) return '今天';
    if (diffDays === 1) return '昨天';
    if (diffDays < 7) return '7天内';
    return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' });
};

const groupSessions = (sessions) => {
    const groups = {};
    sessions.forEach((session) => {
        const label = formatDate(session.timestamp);
        if (!groups[label]) {
            groups[label] = [];
        }
        groups[label].push(session);
    });
    return groups;
};

const Sidebar = ({
    isOpen,
    onClose,
    sessions,
    currentSessionId,
    onSelectSession,
    onNewSession,
    onDeleteSession
}) => {
    const [searchQuery, setSearchQuery] = useState('');
    const [collapsed, setCollapsed] = useState(false);

    const filteredSessions = searchQuery
        ? sessions.filter((s) => (s.title || '新对话').includes(searchQuery))
        : sessions;

    const grouped = groupSessions(filteredSessions);

    const handleToggle = () => {
        if (window.innerWidth <= 900) {
            onClose();
        } else {
            setCollapsed(!collapsed);
        }
    };

    return (
        <>
            <div
                className={`ui-sidebar-overlay ${isOpen ? 'visible' : ''}`}
                onClick={onClose}
            />
            <aside className={`ui-sidebar ${collapsed ? 'collapsed' : ''} ${isOpen ? 'open' : ''}`}>
                <div className="ui-sidebar-header">
                    <div className="ui-sidebar-logo">
                        <div className="logo-mark">
                            <Layers size={16} />
                        </div>
                        AI政策助手
                    </div>
                    <button className="ui-sidebar-toggle" onClick={handleToggle} title="收起侧栏">
                        <X size={18} />
                    </button>
                </div>

                <div className="ui-sidebar-body">
                    <button className="ui-new-chat-btn" onClick={onNewSession}>
                        <Plus size={16} />
                        开启新对话
                    </button>

                    <div className="ui-sidebar-search">
                        <Search size={14} />
                        <input
                            type="text"
                            placeholder="搜索对话历史"
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                        />
                    </div>

                    {filteredSessions.length === 0 ? (
                        <div style={{ padding: '40px 16px', textAlign: 'center', color: 'var(--ui-text-muted)', fontSize: '0.8125rem' }}>
                            {searchQuery ? '未找到匹配的对话' : '暂无历史会话'}
                        </div>
                    ) : (
                        Object.entries(grouped).map(([label, items]) => (
                            <div className="ui-conv-group" key={label}>
                                <div className="ui-conv-group-label">{label}</div>
                                {items.map((session) => (
                                    <div
                                        key={session.id}
                                        className={`ui-conv-item ${session.id === currentSessionId ? 'active' : ''}`}
                                        onClick={() => onSelectSession(session.id)}
                                    >
                                        <span className="ui-conv-item-text">
                                            {session.title || '新对话'}
                                        </span>
                                        <button
                                            className="ui-conv-item-menu"
                                            onClick={(e) => {
                                                e.stopPropagation();
                                                onDeleteSession(session.id);
                                            }}
                                        >
                                            <MoreVertical size={14} />
                                        </button>
                                    </div>
                                ))}
                            </div>
                        ))
                    )}
                </div>

                <div className="ui-sidebar-footer">
                    <div className="ui-user-card">
                        <div className="ui-user-avatar">政</div>
                        <div className="ui-user-info">
                            <div className="ui-user-name">政策用户</div>
                            <div className="ui-user-plan">免费使用</div>
                        </div>
                        <button className="ui-conv-item-menu" style={{ opacity: 1 }}>
                            <Settings size={16} />
                        </button>
                    </div>
                </div>
            </aside>

            {collapsed && (
                <button
                    className="ui-sidebar-expand"
                    onClick={() => setCollapsed(false)}
                    title="展开侧栏"
                    style={{ display: 'flex' }}
                >
                    <Layers size={18} />
                </button>
            )}
        </>
    );
};

export default Sidebar;
