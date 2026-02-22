import React from 'react';
import { MessageSquare, Plus, X, Trash2 } from 'lucide-react';
import './Sidebar.css';

const Sidebar = ({ 
    isOpen, 
    onClose, 
    sessions, 
    currentSessionId, 
    onSelectSession, 
    onNewSession,
    onDeleteSession 
}) => {
    const formatDate = (timestamp) => {
        const date = new Date(timestamp);
        const now = new Date();
        const diffDays = Math.floor((now - date) / (1000 * 60 * 60 * 24));
        
        if (diffDays === 0) return '今天';
        if (diffDays === 1) return '昨天';
        if (diffDays < 7) return `${diffDays}天前`;
        return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' });
    };

    return (
        <>
            <div className={`sidebar-overlay ${isOpen ? 'visible' : ''}`} onClick={onClose} />
            <aside className={`sidebar ${isOpen ? 'open' : ''}`}>
                <div className="sidebar-header">
                    <h2>历史会话</h2>
                    <button className="sidebar-close-btn" onClick={onClose}>
                        <X size={20} />
                    </button>
                </div>

                <button className="new-chat-btn" onClick={onNewSession}>
                    <Plus size={18} />
                    <span>新建会话</span>
                </button>

                <div className="sessions-list">
                    {sessions.length === 0 ? (
                        <div className="no-sessions">
                            <MessageSquare size={32} />
                            <p>暂无历史会话</p>
                        </div>
                    ) : (
                        sessions.map(session => (
                            <div
                                key={session.id}
                                className={`session-item ${session.id === currentSessionId ? 'active' : ''}`}
                                onClick={() => onSelectSession(session.id)}
                            >
                                <div className="session-icon">
                                    <MessageSquare size={16} />
                                </div>
                                <div className="session-info">
                                    <span className="session-title">{session.title || '新对话'}</span>
                                    <span className="session-date">{formatDate(session.timestamp)}</span>
                                </div>
                                <button 
                                    className="delete-session-btn"
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        onDeleteSession(session.id);
                                    }}
                                >
                                    <Trash2 size={14} />
                                </button>
                            </div>
                        ))
                    )}
                </div>
            </aside>
        </>
    );
};

export default Sidebar;
