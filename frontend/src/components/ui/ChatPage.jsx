import React, { useState, useCallback, useEffect, useRef } from 'react';
import { History, MoreVertical, Share2, Trash2, Star, PanelLeftClose, PanelLeft } from 'lucide-react';
import Sidebar from './Sidebar';
import ChatWindow from './ChatWindow';
import { conversationApi } from '../../services/api';
import '../ui/chat-ui.css';

const ChatPage = () => {
    const [sidebarOpen, setSidebarOpen] = useState(false);
    const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
    const [sessions, setSessions] = useState([]);
    const [currentSessionId, setCurrentSessionId] = useState(() => {
        const id = crypto.randomUUID?.() || `${Date.now()}-${Math.random()}`;
        return id;
    });
    const [loading, setLoading] = useState(true);
    const [moreMenuOpen, setMoreMenuOpen] = useState(false);
    const [starredSessions, setStarredSessions] = useState(new Set());
    const [shareToast, setShareToast] = useState(false);
    const moreMenuRef = useRef(null);

    useEffect(() => {
        const loadSessions = async () => {
            try {
                const data = await conversationApi.getSessions();
                const formattedSessions = data.map((session) => ({
                    id: session.id,
                    title: session.title || '新对话',
                    messages: session.messages || [],
                    timestamp: session.updatedAt || session.createdAt
                }));
                setSessions(formattedSessions);
                if (formattedSessions.length > 0) {
                    setCurrentSessionId(formattedSessions[0].id);
                }
            } catch (err) {
                console.error('Failed to load sessions:', err);
            } finally {
                setLoading(false);
            }
        };
        loadSessions();
    }, []);

    useEffect(() => {
        if (!moreMenuOpen) return;
        const handleClickOutside = (event) => {
            if (moreMenuRef.current && !moreMenuRef.current.contains(event.target)) {
                setMoreMenuOpen(false);
            }
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, [moreMenuOpen]);

    const handleNewSession = useCallback(() => {
        const newId = crypto.randomUUID?.() || `${Date.now()}-${Math.random()}`;
        setCurrentSessionId(newId);
        if (sidebarCollapsed) setSidebarCollapsed(false);
    }, [sidebarCollapsed]);

    const handleSelectSession = useCallback((sessionId) => {
        setCurrentSessionId(sessionId);
    }, []);

    const handleDeleteSession = useCallback(async (sessionId) => {
        try {
            await conversationApi.deleteSession(sessionId);
            setSessions((prev) => prev.filter((s) => s.id !== sessionId));
            setStarredSessions((prev) => {
                const next = new Set(prev);
                next.delete(sessionId);
                return next;
            });
            if (sessionId === currentSessionId) {
                handleNewSession();
            }
        } catch (err) {
            console.error('Failed to delete session:', err);
        }
    }, [currentSessionId, handleNewSession]);

    const handleSessionUpdate = useCallback((sessionId, messages) => {
        if (messages.length <= 1) return;
        setSessions((prev) => {
            const firstUserMsg = messages.find((m) => m.role === 'user');
            const title = firstUserMsg
                ? firstUserMsg.content.slice(0, 30) + (firstUserMsg.content.length > 30 ? '...' : '')
                : '新对话';
            const existingIndex = prev.findIndex((s) => s.id === sessionId);
            let updated;
            if (existingIndex >= 0) {
                updated = [...prev];
                updated[existingIndex] = { ...updated[existingIndex], title, messages, timestamp: Date.now() };
            } else {
                updated = [{ id: sessionId, title, messages, timestamp: Date.now() }, ...prev];
            }
            updated.sort((a, b) => b.timestamp - a.timestamp);
            return updated;
        });
    }, []);

    const handleShare = () => {
        const url = window.location.href;
        navigator.clipboard?.writeText(url).then(() => {
            setShareToast(true);
            setTimeout(() => setShareToast(false), 2000);
        }).catch(() => {
            const textarea = document.createElement('textarea');
            textarea.value = url;
            document.body.appendChild(textarea);
            textarea.select();
            document.execCommand('copy');
            document.body.removeChild(textarea);
            setShareToast(true);
            setTimeout(() => setShareToast(false), 2000);
        });
        setMoreMenuOpen(false);
    };

    const handleDeleteCurrent = () => {
        if (currentSessionId && sessions.find((s) => s.id === currentSessionId)) {
            handleDeleteSession(currentSessionId);
        }
        setMoreMenuOpen(false);
    };

    const handleToggleStar = () => {
        setStarredSessions((prev) => {
            const next = new Set(prev);
            if (next.has(currentSessionId)) {
                next.delete(currentSessionId);
            } else {
                next.add(currentSessionId);
            }
            return next;
        });
        setMoreMenuOpen(false);
    };

    const toggleSidebar = () => {
        if (window.innerWidth <= 900) {
            setSidebarOpen(!sidebarOpen);
        } else {
            setSidebarCollapsed(!sidebarCollapsed);
        }
    };

    const currentSession = sessions.find((s) => s.id === currentSessionId);
    const isStarred = starredSessions.has(currentSessionId);

    if (loading) {
        return (
            <div className="ui-app">
                <div className="ui-loading">
                    <div className="ui-loading-spinner" />
                    <span className="ui-loading-text">加载中...</span>
                </div>
            </div>
        );
    }

    return (
        <div className="ui-app">
            <Sidebar
                isOpen={sidebarOpen}
                onClose={() => setSidebarOpen(false)}
                collapsed={sidebarCollapsed}
                sessions={sessions}
                currentSessionId={currentSessionId}
                onSelectSession={handleSelectSession}
                onNewSession={handleNewSession}
                onDeleteSession={handleDeleteSession}
                starredSessions={starredSessions}
            />

            <main className="ui-main">
                <div className="ui-floating-controls" ref={moreMenuRef}>
                    <button
                        className="ui-floating-btn"
                        onClick={toggleSidebar}
                        title={sidebarCollapsed ? '展开侧栏' : '收起侧栏'}
                    >
                        {sidebarCollapsed ? <PanelLeft size={17} /> : <PanelLeftClose size={17} />}
                    </button>
                    <button
                        className="ui-floating-btn"
                        onClick={() => setMoreMenuOpen(!moreMenuOpen)}
                        title="更多操作"
                    >
                        <MoreVertical size={17} />
                    </button>
                    {moreMenuOpen && (
                        <div className="ui-dropdown-menu">
                            <button className="ui-dropdown-item" onClick={handleShare}>
                                <Share2 size={16} />
                                分享
                            </button>
                            <button className="ui-dropdown-item" onClick={handleToggleStar}>
                                <Star size={16} className={isStarred ? 'starred' : ''} />
                                {isStarred ? '取消收藏' : '收藏'}
                            </button>
                            <div className="ui-dropdown-divider" />
                            <button className="ui-dropdown-item danger" onClick={handleDeleteCurrent}>
                                <Trash2 size={16} />
                                删除对话
                            </button>
                        </div>
                    )}
                </div>

                <ChatWindow
                    key={currentSessionId}
                    sessionId={currentSessionId}
                    initialMessages={currentSession?.messages}
                    onSessionUpdate={handleSessionUpdate}
                />
            </main>

            {shareToast && (
                <div className="ui-toast">链接已复制到剪贴板</div>
            )}
        </div>
    );
};

export default ChatPage;
