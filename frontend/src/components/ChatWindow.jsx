import React, { useState, useRef, useEffect } from 'react';
import MessageBubble from './MessageBubble';
import InputArea from './InputArea';
import { chatApi } from '../services/api';
import './ChatWindow.css';

const fileToBase64 = (file) => new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result.split(',')[1]);
    reader.onerror = reject;
    reader.readAsDataURL(file);
});

const WELCOME_MESSAGE = {
    id: 'welcome',
    role: 'assistant',
    content: '您好！我是山东省以旧换新政策咨询智能助手。您可以问我关于汽车、家电、数码产品等的补贴标准和申请流程。\n\n**我可以帮您：**\n- 查询各类产品补贴金额\n- 了解申请条件和流程\n- 计算您能获得的补贴\n- 解答政策相关疑问'
};

const STATUS_PHASES = [
    '正在思考您的问题',
    '正在调用工具获取信息',
    '正在整理最终回复'
];

const ChatWindow = ({ sessionId, initialMessages, onSessionUpdate }) => {
    const [messages, setMessages] = useState(
        initialMessages?.length > 0 ? initialMessages : [WELCOME_MESSAGE]
    );
    const [isGenerating, setIsGenerating] = useState(false);
    const [location, setLocation] = useState(null);
    const messagesEndRef = useRef(null);
    const statusTimerRef = useRef(null);
    const prevSessionIdRef = useRef(sessionId);
    const hasTransientMessage = messages.some(msg => msg.meta?.transient);

    const scrollToBottom = () => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    };

    const clearStatusTimer = () => {
        if (statusTimerRef.current) {
            clearInterval(statusTimerRef.current);
            statusTimerRef.current = null;
        }
    };

    const startStatusTimer = (statusMsgId) => {
        clearStatusTimer();
        let phaseIndex = 0;
        statusTimerRef.current = setInterval(() => {
            phaseIndex = (phaseIndex + 1) % STATUS_PHASES.length;
            setMessages(prev => prev.map(msg => (
                msg.id === statusMsgId
                    ? {
                        ...msg,
                        meta: {
                            ...msg.meta,
                            statusText: STATUS_PHASES[phaseIndex]
                        }
                    }
                    : msg
            )));
        }, 1800);
    };

    useEffect(() => {
        scrollToBottom();
    }, [messages]);

    useEffect(() => {
        if (prevSessionIdRef.current !== sessionId) {
            prevSessionIdRef.current = sessionId;
            setMessages(initialMessages?.length > 0 ? initialMessages : [WELCOME_MESSAGE]);
        }
    }, [sessionId, initialMessages]);

    useEffect(() => {
        // 仅在非生成状态且没有临时状态气泡时同步外部会话，避免父子状态相互覆盖
        if (isGenerating || hasTransientMessage) {
            return;
        }
        if (initialMessages) {
            setMessages(initialMessages.length > 0 ? initialMessages : [WELCOME_MESSAGE]);
        }
    }, [initialMessages, isGenerating, hasTransientMessage]);

    useEffect(() => {
        if (!navigator.geolocation) {
            return;
        }

        navigator.geolocation.getCurrentPosition(
            (position) => {
                setLocation({
                    latitude: position.coords.latitude,
                    longitude: position.coords.longitude,
                    accuracy: position.coords.accuracy
                });
            },
            (error) => {
                console.warn('获取定位失败:', error.message);
            },
            {
                enableHighAccuracy: true,
                timeout: 8000,
                maximumAge: 5 * 60 * 1000
            }
        );
    }, []);

    useEffect(() => {
        // 生成过程中不向父组件同步，避免状态轮询导致频繁覆盖当前对话显示
        if (hasTransientMessage) {
            return;
        }
        const persistedMessages = messages.filter(msg => !msg.meta?.transient);
        if (onSessionUpdate && persistedMessages.length > 1) {
            onSessionUpdate(sessionId, persistedMessages);
        }
    }, [messages, sessionId, onSessionUpdate, hasTransientMessage]);

    const handleSend = async (text, files = []) => {
        if (!text.trim() && files.length === 0) return;

        const imageFiles = files.filter(f => f.type === 'image');
        const userMsg = {
            id: `user-${Date.now()}`,
            role: 'user',
            content: text,
            images: imageFiles.length > 0 ? imageFiles.map(f => f.preview) : undefined
        };
        const statusMsgId = `status-${Date.now()}`;

        setMessages(prev => [
            ...prev,
            userMsg,
            {
                id: statusMsgId,
                role: 'assistant',
                content: '',
                meta: {
                    transient: true,
                    statusOnly: true,
                    isStreaming: true,
                    statusText: STATUS_PHASES[0]
                }
            }
        ]);

        setIsGenerating(true);
        startStatusTimer(statusMsgId);

        try {
            let imageBase64List = null;
            if (imageFiles.length > 0) {
                imageBase64List = await Promise.all(
                    imageFiles.map(f => fileToBase64(f.file))
                );
            }

            const response = await chatApi.createStreamRequest(text, sessionId, imageBase64List, location);

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}`);
            }

            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let aiContent = '';
            let buffer = '';
            const STREAM_TIMEOUT = 5000;

            const readWithTimeout = async () => Promise.race([
                reader.read(),
                new Promise((_, reject) =>
                    setTimeout(() => reject(new Error('STREAM_TIMEOUT')), STREAM_TIMEOUT)
                )
            ]);

            while (true) {
                let result;
                try {
                    result = await readWithTimeout();
                } catch (timeoutError) {
                    if (aiContent) {
                        break;
                    }
                    throw timeoutError;
                }

                const { done, value } = result;
                if (done) break;

                buffer += decoder.decode(value, { stream: true });
                const lines = buffer.split('\n');
                buffer = lines.pop() || '';

                for (const line of lines) {
                    const match = line.match(/^data:\s?(.*)$/);
                    if (match) {
                        aiContent += match[1];
                    }
                }
            }

            // 处理流结束时未被换行符 flush 的最后一段数据
            if (buffer) {
                const trailingMatch = buffer.match(/^data:\s?(.*)$/);
                if (trailingMatch) {
                    aiContent += trailingMatch[1];
                }
            }

            const finalContent = aiContent || '抱歉，本次未获取到有效回复，请稍后重试。';
            const assistantMsg = {
                id: `assistant-${Date.now()}`,
                role: 'assistant',
                content: finalContent
            };

            setMessages(prev => [
                ...prev.filter(msg => msg.id !== statusMsgId),
                assistantMsg
            ]);
        } catch (error) {
            console.error('Chat error:', error);
            setMessages(prev => [
                ...prev.filter(msg => msg.id !== statusMsgId),
                {
                    id: `assistant-error-${Date.now()}`,
                    role: 'assistant',
                    content: '抱歉，服务暂时不可用，请稍后重试。'
                }
            ]);
        } finally {
            clearStatusTimer();
            setIsGenerating(false);
        }
    };

    useEffect(() => () => clearStatusTimer(), []);

    return (
        <div className="chat-window">
            <div className="messages-list">
                {messages.map(msg => (
                    <MessageBubble
                        key={msg.id}
                        role={msg.role}
                        content={msg.content}
                        images={msg.images}
                        meta={msg.meta}
                    />
                ))}
                <div ref={messagesEndRef} />
            </div>
            <div className="input-wrapper">
                <InputArea onSend={handleSend} isGenerating={isGenerating} />
            </div>
        </div>
    );
};

export default ChatWindow;
