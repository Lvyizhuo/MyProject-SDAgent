import React, { useState, useCallback, useEffect } from 'react';
import { History } from 'lucide-react';
import { v4 as uuidv4 } from 'uuid';
import ChatWindow from '../components/ChatWindow';
import Sidebar from '../components/Sidebar';
import { conversationApi } from '../services/api';
import '../App.css';

const ChatPage = () => {
    const [sidebarOpen, setSidebarOpen] = useState(false);
    const [sessions, setSessions] = useState([]);
    const [currentSessionId, setCurrentSessionId] = useState(() => uuidv4());
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const loadSessions = async () => {
            try {
                const data = await conversationApi.getSessions();
                const formattedSessions = data.map(session => ({
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
        const newId = uuidv4();
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
            setSessions(prev => prev.filter(s => s.id !== sessionId));
            if (sessionId === currentSessionId) {
                handleNewSession();
            }
        } catch (err) {
            console.error('Failed to delete session:', err);
        }
    }, [currentSessionId, handleNewSession]);

    const handleSessionUpdate = useCallback((sessionId, messages) => {
        if (messages.length <= 1) return;

        setSessions(prev => {
            const firstUserMsg = messages.find(m => m.role === 'user');
            const title = firstUserMsg
                ? firstUserMsg.content.slice(0, 30) + (firstUserMsg.content.length > 30 ? '...' : '')
                : '新对话';

            const existingIndex = prev.findIndex(s => s.id === sessionId);
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

    const currentSession = sessions.find(s => s.id === currentSessionId);

    if (loading) {
        return (
            <div className="loading-screen">
                <div className="loading-spinner"></div>
            </div>
        );
    }

    return (
        <div className="chat-shell">
            <div className="app-container">
                <Sidebar
                    isOpen={sidebarOpen}
                    onClose={() => setSidebarOpen(false)}
                    sessions={sessions}
                    currentSessionId={currentSessionId}
                    onSelectSession={handleSelectSession}
                    onNewSession={handleNewSession}
                    onDeleteSession={handleDeleteSession}
                />

                <header className="app-header">
                    <div className="header-content">
                        <button
                            className="header-menu-btn"
                            onClick={() => setSidebarOpen(true)}
                            title="历史会话"
                            aria-label="查看历史会话"
                        >
                            <History size={22} />
                        </button>
                        <div className="header-assistant-meta">
                            <div className="header-text">
                                <h1>AI政策助手</h1>
                                <p>
                                    <span className="header-status-dot" />
                                    在线服务中
                                </p>
                            </div>
                        </div>
                    </div>
                </header>

                <main className="app-main">
                    <ChatWindow
                        key={currentSessionId}
                        sessionId={currentSessionId}
                        initialMessages={currentSession?.messages}
                        onSessionUpdate={handleSessionUpdate}
                    />
                </main>
            </div>
        </div>
    );
};

export default ChatPage;
