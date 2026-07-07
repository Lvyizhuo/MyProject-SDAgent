import React, { useState, useEffect, useRef } from 'react';
import { Plus, Search, MoreVertical, Star, Share2, Trash2 } from 'lucide-react';
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

const groupSessions = (sessions, starredSet) => {
    const groups = {};
    const starred = [];
    const others = [];

    sessions.forEach((session) => {
        if (starredSet.has(session.id)) {
            starred.push(session);
        } else {
            others.push(session);
        }
    });

    if (starred.length > 0) {
        groups['收藏'] = starred;
    }

    others.forEach((session) => {
        const label = formatDate(session.timestamp);
        if (!groups[label]) groups[label] = [];
        groups[label].push(session);
    });

    return groups;
};

const Sidebar = ({
    isOpen,
    onClose,
    collapsed,
    sessions,
    currentSessionId,
    onSelectSession,
    onNewSession,
    onDeleteSession,
    starredSessions,
    onToggleStar,
    onShare
}) => {
    const [searchQuery, setSearchQuery] = useState('');
    const [menuOpenSessionId, setMenuOpenSessionId] = useState(null);
    const menuRef = useRef(null);
    const starredSet = starredSessions || new Set();

    const filteredSessions = searchQuery
        ? sessions.filter((s) => (s.title || '新对话').includes(searchQuery))
        : sessions;

    const grouped = groupSessions(filteredSessions, starredSet);

    useEffect(() => {
        if (menuOpenSessionId === null) return;

        const handleClick = (e) => {
            if (menuRef.current && !menuRef.current.contains(e.target)) {
                setMenuOpenSessionId(null);
            }
        };

        const timer = setTimeout(() => {
            document.addEventListener('mousedown', handleClick);
        }, 0);

        return () => {
            clearTimeout(timer);
            document.removeEventListener('mousedown', handleClick);
        };
    }, [menuOpenSessionId]);

    return (
        <>
            <div
                className={`ui-sidebar-overlay ${isOpen ? 'visible' : ''}`}
                onClick={onClose}
            />
            <aside className={`ui-sidebar ${collapsed ? 'collapsed' : ''} ${isOpen ? 'open' : ''}`}>
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
                        <div className="ui-sidebar-empty">
                            {searchQuery ? '未找到匹配的对话' : '暂无历史会话'}
                        </div>
                    ) : (
                        Object.entries(grouped).map(([label, items]) => (
                            <div className="ui-conv-group" key={label}>
                                <div className="ui-conv-group-label">
                                    {label === '收藏' && <Star size={11} className="star-icon" />}
                                    {label}
                                </div>
                                {items.map((session) => {
                                    const isStarred = starredSet.has(session.id);
                                    return (
                                        <div
                                            key={session.id}
                                            className={`ui-conv-item ${session.id === currentSessionId ? 'active' : ''}`}
                                            onClick={() => onSelectSession(session.id)}
                                        >
                                            {isStarred && <Star size={12} className="ui-conv-star" fill="currentColor" />}
                                            <span className="ui-conv-item-text">
                                                {session.title || '新对话'}
                                            </span>
                                            <div
                                                className="ui-conv-item-actions"
                                                ref={menuOpenSessionId === session.id ? menuRef : null}
                                            >
                                                <button
                                                    className="ui-conv-item-menu"
                                                    onClick={(e) => {
                                                        e.stopPropagation();
                                                        setMenuOpenSessionId(
                                                            menuOpenSessionId === session.id ? null : session.id
                                                        );
                                                    }}
                                                    title="更多操作"
                                                >
                                                    <MoreVertical size={14} />
                                                </button>
                                                {menuOpenSessionId === session.id && (
                                                    <div
                                                        className="ui-conv-dropdown"
                                                        onClick={(e) => e.stopPropagation()}
                                                    >
                                                        <button
                                                            className="ui-conv-dropdown-item"
                                                            onClick={(e) => {
                                                                e.stopPropagation();
                                                                onShare?.(session.id);
                                                                setMenuOpenSessionId(null);
                                                            }}
                                                        >
                                                            <Share2 size={14} />
                                                            分享
                                                        </button>
                                                        <button
                                                            className="ui-conv-dropdown-item"
                                                            onClick={(e) => {
                                                                e.stopPropagation();
                                                                onToggleStar?.(session.id);
                                                                setMenuOpenSessionId(null);
                                                            }}
                                                        >
                                                            <Star size={14} />
                                                            收藏
                                                        </button>
                                                        <div className="ui-conv-dropdown-divider" />
                                                        <button
                                                            className="ui-conv-dropdown-item danger"
                                                            onClick={(e) => {
                                                                e.stopPropagation();
                                                                onDeleteSession(session.id);
                                                                setMenuOpenSessionId(null);
                                                            }}
                                                        >
                                                            <Trash2 size={14} />
                                                            删除
                                                        </button>
                                                    </div>
                                                )}
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        ))
                    )}
                </div>
            </aside>
        </>
    );
};

export default Sidebar;
