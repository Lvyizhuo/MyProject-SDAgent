import React, { useState, useRef, useEffect } from 'react';
import { Layers, FileText, MapPin, Calculator, Bot, Target } from 'lucide-react';
import MessageBubble from './MessageBubble';
import InputArea from './InputArea';
import { chatApi, publicConfigApi } from '../../services/api';
import '../ui/chat-ui.css';

const fileToBase64 = (file) => new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result.split(',')[1]);
    reader.onerror = reject;
    reader.readAsDataURL(file);
});

const DEFAULT_WELCOME_CONTENT = '您好！我是山东省以旧换新政策咨询智能助手。可以帮您查询各类产品补贴标准、了解申请条件和流程、计算您能获得的补贴金额。\n\n请问有什么可以帮您的？';

// 后端 SSE 推送的阶段 → 面向用户的状态文案（与后端真实阶段一一对应，不再是盲目的定时轮播）
const STAGE_TEXT = {
    analyzing: '正在理解您的问题',
    planning: '正在规划检索与工具调用',
    tool: '正在调用工具获取信息',
    retrieving: '正在检索知识库政策依据',
    generating: '正在组织回复'
};

const AUTO_SCROLL_THRESHOLD = 120;

const SUGGESTIONS = [
    { icon: FileText, title: '补贴标准查询', desc: '查询各类产品以旧换新补贴金额' },
    { icon: Calculator, title: '补贴金额计算', desc: '根据产品类型计算可获补贴金额' },
    { icon: MapPin, title: '附近门店查询', desc: '查找您附近的以旧换新参与门店' },
    { icon: Layers, title: '申请流程指导', desc: '了解补贴申请条件和办理流程' }
];

const ChatWindow = ({ sessionId, initialMessages, onSessionUpdate }) => {
    const [greetingContent, setGreetingContent] = useState(DEFAULT_WELCOME_CONTENT);
    const [messages, setMessages] = useState(() => {
        if (initialMessages?.length > 0) return initialMessages;
        return [{ id: 'welcome', role: 'assistant', content: DEFAULT_WELCOME_CONTENT }];
    });
    const [isGenerating, setIsGenerating] = useState(false);
    const [location, setLocation] = useState(null);
    const [activeReferenceKey, setActiveReferenceKey] = useState(null);
    const [expandedReferenceMap, setExpandedReferenceMap] = useState({});
    const messagesEndRef = useRef(null);
    const messagesListRef = useRef(null);
    const statusTimerRef = useRef(null);
    const abortControllerRef = useRef(null);
    const prevSessionIdRef = useRef(sessionId);
    const shouldAutoScrollRef = useRef(true);
    const lastPersistedSignatureRef = useRef('');
    const hasTransientMessage = messages.some((msg) => msg.meta?.transient);

    const hasUserMessages = messages.some((msg) => msg.role === 'user');
    const showWelcome = !hasUserMessages && messages.length <= 1;

    useEffect(() => {
        const fetchGreeting = async () => {
            try {
                const config = await publicConfigApi.getAgentConfig();
                if (config.greetingMessage) {
                    setGreetingContent(config.greetingMessage);
                    setMessages((prev) => {
                        if (prev.length === 1 && prev[0].id === 'welcome') {
                            return [{ id: 'welcome', role: 'assistant', content: config.greetingMessage }];
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
                { id: 'welcome', role: 'assistant', content: greetingContent }
            ]);
        }
    }, [sessionId, initialMessages, greetingContent]);

    useEffect(() => {
        if (!navigator.geolocation) return;
        navigator.geolocation.getCurrentPosition(
            (position) => {
                setLocation({
                    latitude: position.coords.latitude,
                    longitude: position.coords.longitude,
                    accuracy: position.coords.accuracy
                });
            },
            (error) => { console.warn('获取定位失败:', error.message); },
            { enableHighAccuracy: true, timeout: 8000, maximumAge: 5 * 60 * 1000 }
        );
    }, []);

    useEffect(() => {
        if (hasTransientMessage) return;
        const persistedMessages = messages.filter((msg) => !msg.meta?.transient);
        const signature = persistedMessages
            .map((msg) => `${msg.id}|${msg.role}|${msg.content}`)
            .join('||');
        if (signature === lastPersistedSignatureRef.current) return;
        lastPersistedSignatureRef.current = signature;
        if (onSessionUpdate && persistedMessages.length > 1) {
            onSessionUpdate(sessionId, persistedMessages);
        }
    }, [messages, sessionId, onSessionUpdate, hasTransientMessage]);

    const handleSend = async (text, files = []) => {
        if (!text.trim() && files.length === 0) return;

        const imageFiles = files.filter((f) => f.type === 'image');
        const userMsg = {
            id: `user-${Date.now()}`,
            role: 'user',
            content: text,
            images: imageFiles.length > 0 ? imageFiles.map((f) => f.preview) : undefined
        };
        const statusMsgId = `status-${Date.now()}`;

        setMessages((prev) => [
            ...prev,
            userMsg,
            {
                id: statusMsgId,
                role: 'assistant',
                content: '',
                meta: { transient: true, statusOnly: true, statusText: STAGE_TEXT.analyzing }
            }
        ]);
        shouldAutoScrollRef.current = true;

        setIsGenerating(true);

        // 每轮一个 AbortController：用户点「停止生成」时中断流式请求，省下后端算力
        const abortController = new AbortController();
        abortControllerRef.current = abortController;

        try {
            let imageBase64List = null;
            if (imageFiles.length > 0) {
                imageBase64List = await Promise.all(imageFiles.map((f) => fileToBase64(f.file)));
            }

            // 流式：后端逐块推送 delta，前端实时追加到同一条 assistant 消息
            const streamedContent = { text: '' };
            const updateAssistant = (patch) => {
                setMessages((prev) => prev.map((msg) => (
                    msg.id === statusMsgId ? { ...msg, ...patch } : msg
                )));
            };

            const response = await chatApi.sendMessageStream(text, sessionId, {
                imageBase64List,
                location,
                signal: abortController.signal,
                onStage: (stage) => {
                    const label = STAGE_TEXT[stage];
                    if (!label) return;
                    setMessages((prev) => prev.map((msg) => (
                        msg.id === statusMsgId
                            ? { ...msg, meta: { ...msg.meta, statusText: label } }
                            : msg
                    )));
                },
                onDelta: (chunk) => {
                    if (!chunk) return;
                    streamedContent.text += chunk;
                    updateAssistant({
                        content: streamedContent.text,
                        meta: { transient: true, statusOnly: false, streaming: true }
                    });
                },
                onError: (message) => {
                    console.error('Chat stream error:', message);
                }
            });

            const finalContent = response?.content || streamedContent.text || '抱歉，本次未获取到有效回复，请稍后重试。';
            const assistantMsg = {
                id: response?.id || `assistant-${Date.now()}`,
                role: 'assistant',
                content: finalContent,
                references: response?.references || []
            };

            setMessages((prev) => [
                ...prev.filter((msg) => msg.id !== statusMsgId),
                assistantMsg
            ]);
        } catch (error) {
            console.error('Chat error:', error);
            const aborted = error?.name === 'AbortError' || abortController.signal.aborted;
            // 保留已流式收到的部分内容，避免用户完全看不到回复
            setMessages((prev) => {
                const partial = prev.find((msg) => msg.id === statusMsgId)?.content;
                return [
                    ...prev.filter((msg) => msg.id !== statusMsgId),
                    {
                        id: `assistant-error-${Date.now()}`,
                        role: 'assistant',
                        content: partial && partial.trim()
                            ? `${partial}\n\n（${aborted ? '已停止生成' : '本次回复中断，可点击重新发送'}）`
                            : (aborted ? '已停止生成。' : '抱歉，服务暂时不可用，请稍后重试。')
                    }
                ];
            });
        } finally {
            abortControllerRef.current = null;
            clearStatusTimer();
            setIsGenerating(false);
        }
    };

    const handleStop = () => {
        if (abortControllerRef.current) {
            abortControllerRef.current.abort(new Error('用户停止生成'));
        }
    };

    useEffect(() => () => clearStatusTimer(), []);

    const handleToggleReference = (cardKey) => {
        setExpandedReferenceMap((prev) => ({ ...prev, [cardKey]: !prev[cardKey] }));
        setActiveReferenceKey(cardKey);
    };

    const handleReferenceClick = (messageId, refId) => {
        const cardKey = `${messageId}-${refId}`;
        setActiveReferenceKey(cardKey);
        setExpandedReferenceMap((prev) => ({ ...prev, [cardKey]: true }));
        requestAnimationFrame(() => {
            const element = document.getElementById(`ref-${cardKey}`);
            if (element) element.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        });
    };

    const handleSuggestionClick = (suggestion) => {
        const queryMap = {
            '补贴标准查询': '汽车以旧换新补贴标准是什么？',
            '补贴金额计算': '帮我计算一下我能获得多少补贴',
            '附近门店查询': '我附近有哪些以旧换新参与门店？',
            '申请流程指导': '以旧换新补贴申请流程是什么？'
        };
        const query = queryMap[suggestion.title] || suggestion.title;
        handleSend(query);
    };

    return (
        <div className="ui-content" onScroll={handleMessagesScroll}>
            {showWelcome ? (
                <>
                    <div className="ui-welcome">
                        <div className="ui-welcome-logo">
                            <Target size={28} />
                        </div>
                        <h1 className="ui-welcome-title">您好，我是山东政策咨询助手</h1>
                        <p className="ui-welcome-subtitle">
                            专注山东省以旧换新政策咨询，涵盖汽车、家电、数码产品等领域。补贴标准、申请流程、门店查询，随时问我。
                        </p>

                        <div className="ui-input-zone" style={{ marginTop: 0 }}>
                            <InputArea onSend={handleSend} isGenerating={isGenerating} />
                        </div>

                        <div className="ui-welcome-suggestions">
                            {SUGGESTIONS.map((item) => {
                                const Icon = item.icon;
                                return (
                                    <button
                                        key={item.title}
                                        className="ui-suggestion-card"
                                        onClick={() => handleSuggestionClick(item)}
                                    >
                                        <div className="ui-suggestion-card-icon">
                                            <Icon size={15} />
                                        </div>
                                        <div className="ui-suggestion-card-title">{item.title}</div>
                                        <div className="ui-suggestion-card-desc">{item.desc}</div>
                                    </button>
                                );
                            })}
                        </div>
                    </div>
                </>
            ) : (
                <div className="ui-chat-view">
                    <div className="ui-messages" ref={messagesListRef} onScroll={handleMessagesScroll}>
                        {messages.map((msg) => (
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
                    <InputArea onSend={handleSend} isGenerating={isGenerating} />
                    {isGenerating && (
                        <div className="ui-stop-zone">
                            <button type="button" className="ui-stop-button" onClick={handleStop}>
                                停止生成
                            </button>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
};

export default ChatWindow;
