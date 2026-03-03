import React, { useState, useRef, useEffect } from 'react';
import { Bot } from 'lucide-react';
import MessageBubble from './MessageBubble';
import InputArea from './InputArea';
import { chatApi, publicConfigApi } from '../services/api';
import './ChatWindow.css';

const fileToBase64 = (file) => new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result.split(',')[1]);
    reader.onerror = reject;
    reader.readAsDataURL(file);
});

const DEFAULT_WELCOME_CONTENT = '您好！我是山东省以旧换新政策咨询智能助手。您可以问我关于汽车、家电、数码产品等的补贴标准和申请流程。\n\n**我可以帮您：**\n- 查询各类产品补贴金额\n- 了解申请条件和流程\n- 计算您能获得的补贴\n- 解答政策相关疑问';

const STATUS_PHASES = [
    '正在思考您的问题',
    '正在调用工具获取信息',
    '正在整理最终回复'
];
const AUTO_SCROLL_THRESHOLD = 120;

const consumeSseEvents = (chunk, onData) => {
    const events = chunk.split('\n\n');
    const rest = events.pop() || '';

    for (const event of events) {
        const lines = event.split('\n');
        const dataLines = lines
            .filter(line => line.startsWith('data:'))
            .map(line => line.replace(/^data:\s?/, ''));
        if (dataLines.length > 0) {
            onData(dataLines.join('\n'));
        }
    }
    return rest;
};

const ChatWindow = ({ sessionId, initialMessages, onSessionUpdate }) => {
    const [greetingContent, setGreetingContent] = useState(DEFAULT_WELCOME_CONTENT);
    const [messages, setMessages] = useState(() => {
        if (initialMessages?.length > 0) {
            return initialMessages;
        }
        return [
            {
                id: 'welcome',
                role: 'assistant',
                content: DEFAULT_WELCOME_CONTENT
            }
        ];
    });
    const [isGenerating, setIsGenerating] = useState(false);
    const [location, setLocation] = useState(null);
    const [activeReferenceKey, setActiveReferenceKey] = useState(null);
    const [expandedReferenceMap, setExpandedReferenceMap] = useState({});
    const messagesEndRef = useRef(null);
    const messagesListRef = useRef(null);
    const statusTimerRef = useRef(null);
    const prevSessionIdRef = useRef(sessionId);
    const shouldAutoScrollRef = useRef(true);
    const lastPersistedSignatureRef = useRef('');
    const hasTransientMessage = messages.some(msg => msg.meta?.transient);

    // 从后端获取动态开场白
    useEffect(() => {
        const fetchGreeting = async () => {
            try {
                const config = await publicConfigApi.getAgentConfig();
                if (config.greetingMessage) {
                    setGreetingContent(config.greetingMessage);
                    // 更新欢迎消息（如果当前只有欢迎消息）
                    setMessages(prev => {
                        if (prev.length === 1 && prev[0].id === 'welcome') {
                            return [
                                {
                                    id: 'welcome',
                                    role: 'assistant',
                                    content: config.greetingMessage
                                }
                            ];
                        }
                        return prev;
                    });
                }
            } catch (err) {
                console.warn('获取开场白失败，使用默认值:', err.message);
            }
        };
        fetchGreeting();
    }, []);

    const scrollToBottom = (behavior = 'auto') => {
        if (!shouldAutoScrollRef.current) return;
        const list = messagesListRef.current;
        if (list) {
            list.scrollTo({ top: list.scrollHeight, behavior });
            return;
        }
        messagesEndRef.current?.scrollIntoView({ behavior });
    };

    const handleMessagesScroll = () => {
        const list = messagesListRef.current;
        if (!list) return;
        const distanceToBottom = list.scrollHeight - list.scrollTop - list.clientHeight;
        shouldAutoScrollRef.current = distanceToBottom <= AUTO_SCROLL_THRESHOLD;
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
        scrollToBottom(isGenerating ? 'auto' : 'smooth');
    }, [messages, isGenerating]);

    useEffect(() => {
        if (prevSessionIdRef.current !== sessionId) {
            prevSessionIdRef.current = sessionId;
            shouldAutoScrollRef.current = true;
            lastPersistedSignatureRef.current = '';
            setActiveReferenceKey(null);
            setExpandedReferenceMap({});
            setMessages(initialMessages?.length > 0 ? initialMessages : [
                {
                    id: 'welcome',
                    role: 'assistant',
                    content: greetingContent
                }
            ]);
        }
    }, [sessionId, initialMessages, greetingContent]);

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
        const signature = persistedMessages
            .map(msg => `${msg.id}|${msg.role}|${msg.content}`)
            .join('||');
        if (signature === lastPersistedSignatureRef.current) {
            return;
        }
        lastPersistedSignatureRef.current = signature;
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
        shouldAutoScrollRef.current = true;

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
                buffer = consumeSseEvents(buffer, (data) => {
                    aiContent += data;
                });
            }

            // 处理结束时残留事件
            if (buffer) {
                consumeSseEvents(`${buffer}\n\n`, (data) => {
                    aiContent += data;
                });
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

    const handleToggleReference = (cardKey) => {
        setExpandedReferenceMap(prev => ({
            ...prev,
            [cardKey]: !prev[cardKey]
        }));
        setActiveReferenceKey(cardKey);
    };

    const handleReferenceClick = (messageId, refId) => {
        const cardKey = `${messageId}-${refId}`;
        setActiveReferenceKey(cardKey);
        setExpandedReferenceMap(prev => ({
            ...prev,
            [cardKey]: true
        }));
        requestAnimationFrame(() => {
            const element = document.getElementById(`ref-${cardKey}`);
            if (!element) return;
            element.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        });
    };

    return (
        <div className="chat-window">
            <header className="chat-panel-header">
                <div className="chat-panel-bot-icon">
                    <Bot size={17} />
                </div>
                <div className="chat-panel-meta">
                    <h2>AI政策助手</h2>
                    <p>
                        <span className="online-dot" />
                        在线服务中
                    </p>
                </div>
            </header>
            <div className="messages-list" ref={messagesListRef} onScroll={handleMessagesScroll}>
                {messages.map(msg => (
                    <MessageBubble
                        key={msg.id}
                        id={msg.id}
                        role={msg.role}
                        content={msg.content}
                        images={msg.images}
                        meta={msg.meta}
                        references={msg.references || msg.meta?.references || []}
                        activeRefId={activeReferenceKey}
                        expandedRefIds={expandedReferenceMap}
                        onRefClick={(refId) => handleReferenceClick(msg.id, refId)}
                        onToggleRef={handleToggleReference}
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
