import React, { useState, useCallback, useEffect } from 'react';
import { PanelLeftClose, PanelLeft } from 'lucide-react';
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
    const [starredSessions, setStarredSessions] = useState(new Set());
    const [shareToast, setShareToast] = useState(false);

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

    const handleShare = useCallback(() => {
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
    }, []);

    const handleToggleStar = useCallback((sessionId) => {
        setStarredSessions((prev) => {
            const next = new Set(prev);
            if (next.has(sessionId)) {
                next.delete(sessionId);
            } else {
                next.add(sessionId);
            }
            return next;
        });
    }, []);

    const toggleSidebar = () => {
        if (window.innerWidth <= 900) {
            setSidebarOpen(!sidebarOpen);
        } else {
            setSidebarCollapsed(!sidebarCollapsed);
        }
    };

    const currentSession = sessions.find((s) => s.id === currentSessionId);

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
                onToggleStar={handleToggleStar}
                onShare={handleShare}
            />

            <main className="ui-main">
                <div className="ui-floating-controls">
                    <button
                        className="ui-floating-btn"
                        onClick={toggleSidebar}
                        title={sidebarCollapsed ? '展开侧栏' : '收起侧栏'}
                    >
                        {sidebarCollapsed ? <PanelLeft size={17} /> : <PanelLeftClose size={17} />}
                    </button>
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
