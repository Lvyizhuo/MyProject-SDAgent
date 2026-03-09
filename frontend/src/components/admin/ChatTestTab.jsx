import React, { useState, useRef, useEffect } from 'react';
import { adminApi } from '../../services/adminApi';
import { Trash2 } from 'lucide-react';
import { v4 as uuidv4 } from 'uuid';
import MessageBubble from '../MessageBubble';
import InputArea from '../InputArea';
import '../ChatWindow.css'; // Reuse ChatWindow styles
import './ChatTestTab.css';

const ChatTestTab = ({ config }) => {
    const [messages, setMessages] = useState([]);
    const [loading, setLoading] = useState(false);
    const [sessionId, setSessionId] = useState('');
    const messagesEndRef = useRef(null);

    const greetingMessage = config?.greetingMessage;
    const effectiveModelName = config?.effectiveModelName || config?.modelName || '未配置';
    const effectiveProvider = config?.effectiveModelProvider || config?.modelProvider || '未配置';
    const effectiveSource = config?.effectiveConfigSource === 'MODEL_PROVIDER' ? '模型管理' : '手动配置';
    const configVersion = [
        config?.updatedAt,
        config?.llmModelId,
        config?.effectiveModelName,
        config?.effectiveModelProvider,
        config?.greetingMessage
    ].filter(Boolean).join('|');

    useEffect(() => {
        initSession();
    }, [configVersion]);

    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages]);

    const initSession = () => {
        setSessionId(`admin-test-${uuidv4()}`);
        setMessages([
            {
                id: `ai-${Date.now()}`,
                role: 'assistant',
                content: greetingMessage || '您好！我是管理员测试模式。请发送消息以测试当前配置。'
            }
        ]);
    };

    const handleSend = async (text, files = []) => {
        if (!text.trim() && files.length === 0) return;
        if (loading) return;

        const userText = text.trim();
        
        setMessages(prev => [...prev, { 
            id: `user-${Date.now()}`,
            role: 'user', 
            content: userText 
        }]);
        setLoading(true);

        try {
            const response = await adminApi.testAgentConfig(userText, sessionId);

            // Add a small delay for better UX
            setTimeout(() => {
                setMessages(prev => [...prev, {
                    id: `ai-${Date.now()}`,
                    role: 'assistant',
                    content: response.content || '未返回内容'
                }]);
                setLoading(false);
            }, 300);
        } catch (err) {
            setMessages(prev => [...prev, {
                id: `ai-err-${Date.now()}`,
                role: 'assistant',
                content: `**发生错误**：${err.message}`
            }]);
            setLoading(false);
        }
    };

    return (
        <div className="chat-test-tab">
            <div className="chat-test-header">
                <div className="chat-test-header-meta">
                    <span className="session-info">测试会话: {sessionId.substring(0, 18)}...</span>
                    <span className="active-model-info">
                        当前生效: {effectiveProvider} / {effectiveModelName} ({effectiveSource})
                    </span>
                </div>
                <button className="btn-clear" onClick={initSession} title="清空会话">
                    <Trash2 size={16} />
                    清空
                </button>
            </div>
            
            <div className="chat-window mock-chat-window">
                <div className="messages-list mock-messages-list">
                    {messages.map((msg) => (
                        <div key={msg.id} className="test-message-wrapper">
                            <MessageBubble
                                id={msg.id}
                                role={msg.role}
                                content={msg.content}
                                meta={loading && msg.role === 'user' && msg.id === messages[messages.length - 1].id ? undefined : undefined} // Not using streaming status here, just loader below
                            />
                        </div>
                    ))}
                    {loading && (
                        <MessageBubble 
                            id="loading-status"
                            role="assistant"
                            content=""
                            meta={{ statusOnly: true, statusText: '正在测试配置...' }}
                        />
                    )}
                    <div ref={messagesEndRef} />
                </div>

                <div className="input-wrapper">
                    <InputArea onSend={handleSend} isGenerating={loading} disabled={loading} />
                </div>
            </div>
        </div>
    );
};

export default ChatTestTab;
