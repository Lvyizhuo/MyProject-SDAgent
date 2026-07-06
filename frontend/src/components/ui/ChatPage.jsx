import React, { useState, useCallback, useEffect } from 'react';
import { History } from 'lucide-react';
import Sidebar from './Sidebar';
import ChatWindow from './ChatWindow';
import { conversationApi } from '../../services/api';
import '../ui/chat-ui.css';

const ChatPage = () => {
    const [sidebarOpen, setSidebarOpen] = useState(false);
    const [sessions, setSessions] = useState([]);
    const [currentSessionId, setCurrentSessionId] = useState(() => {
        const id = crypto.randomUUID?.() || `${Date.now()}-${Math.random()}`;
        return id;
    });
    const [loading, setLoading] = useState(true);

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
        setSidebarOpen(false);
    }, []);

    const handleSelectSession = useCallback((sessionId) => {
        setCurrentSessionId(sessionId);
        setSidebarOpen(false);
    }, []);

    const handleDeleteSession = useCallback(async (sessionId) => {
        try {
            await conversationApi.deleteSession(sessionId);
            setSessions((prev) => prev.filter((s) => s.id !== sessionId));
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
                updated[existingIndex] = {
                    ...updated[existingIndex],
                    title,
                    messages,
                    timestamp: Date.now()
                };
            } else {
                updated = [{
                    id: sessionId,
                    title,
                    messages,
                    timestamp: Date.now()
                }, ...prev];
            }

            updated.sort((a, b) => b.timestamp - a.timestamp);
            return updated;
        });
    }, []);

    const currentSession = sessions.find((s) => s.id === currentSessionId);

    const toggleSidebar = () => {
        setSidebarOpen(!sidebarOpen);
    };

    if (loading) {
        return (
            <div className="ui-app" style={{ alignItems: 'center', justifyContent: 'center' }}>
                <div style={{ textAlign: 'center', color: 'var(--ui-text-muted)' }}>
                    <div style={{
                        width: 32,
                        height: 32,
                        border: '2px solid var(--ui-border)',
                        borderTopColor: 'var(--ui-primary)',
                        borderRadius: '50%',
                        animation: 'spin 0.8s linear infinite',
                        margin: '0 auto 12px'
                    }} />
                    <span style={{ fontSize: '0.875rem' }}>加载中...</span>
                </div>
            </div>
        );
    }

    return (
        <div className="ui-app">
            <Sidebar
                isOpen={sidebarOpen}
                onClose={() => setSidebarOpen(false)}
                sessions={sessions}
                currentSessionId={currentSessionId}
                onSelectSession={handleSelectSession}
                onNewSession={handleNewSession}
                onDeleteSession={handleDeleteSession}
            />

            <button
                className="ui-sidebar-fab"
                onClick={toggleSidebar}
                title="历史会话"
            >
                <History size={18} />
            </button>

            <main className="ui-main">
                <ChatWindow
                    key={currentSessionId}
                    sessionId={currentSessionId}
                    initialMessages={currentSession?.messages}
                    onSessionUpdate={handleSessionUpdate}
                />
            </main>
        </div>
    );
};

export default ChatPage;
