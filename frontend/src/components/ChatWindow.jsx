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

const ChatWindow = ({ sessionId, initialMessages, onSessionUpdate }) => {
    const [messages, setMessages] = useState(
        initialMessages?.length > 0 ? initialMessages : [WELCOME_MESSAGE]
    );
    const [isGenerating, setIsGenerating] = useState(false);
    const messagesEndRef = useRef(null);

    const scrollToBottom = () => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    };

    useEffect(() => {
        scrollToBottom();
    }, [messages]);

    useEffect(() => {
        if (onSessionUpdate && messages.length > 1) {
            onSessionUpdate(sessionId, messages);
        }
    }, [messages, sessionId, onSessionUpdate]);

    const handleSend = async (text, files = []) => {
        if (!text.trim() && files.length === 0) return;

        const imageFiles = files.filter(f => f.type === 'image');

        const userMsg = { 
            id: Date.now(), 
            role: 'user', 
            content: text,
            images: imageFiles.length > 0 ? imageFiles.map(f => f.preview) : undefined
        };
        setMessages(prev => [...prev, userMsg]);
        setIsGenerating(true);

        try {
            let imageBase64List = null;
            if (imageFiles.length > 0) {
                imageBase64List = await Promise.all(
                    imageFiles.map(f => fileToBase64(f.file))
                );
            }

            const response = await chatApi.createStreamRequest(text, sessionId, imageBase64List);

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}`);
            }

            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let aiContent = '';
            const aiMsgId = Date.now() + 1;
            
            setMessages(prev => [...prev, { id: aiMsgId, role: 'assistant', content: '' }]);

            let buffer = '';
            let lastContentTime = Date.now();
            const STREAM_TIMEOUT = 5000; // 5秒无新内容则认为流结束
            
            const readWithTimeout = async () => {
                return Promise.race([
                    reader.read(),
                    new Promise((_, reject) => 
                        setTimeout(() => reject(new Error('STREAM_TIMEOUT')), STREAM_TIMEOUT)
                    )
                ]);
            };
            
            try {
                while (true) {
                    let result;
                    try {
                        result = await readWithTimeout();
                    } catch (timeoutError) {
                        // 超时且已有内容，认为流正常结束
                        if (aiContent) {
                            console.log('Stream timeout with content, treating as complete');
                            break;
                        }
                        throw timeoutError;
                    }
                    
                    const { done, value } = result;
                    if (done) break;
                    
                    lastContentTime = Date.now();
                    buffer += decoder.decode(value, { stream: true });
                    
                    const lines = buffer.split('\n');
                    buffer = lines.pop() || '';
                    
                    for (const line of lines) {
                        if (line.startsWith('data:')) {
                            const content = line.slice(5);
                            aiContent += content;
                        }
                    }
                    
                    setMessages(prev => prev.map(msg => 
                        msg.id === aiMsgId ? { ...msg, content: aiContent } : msg
                    ));
                }
            } catch (streamError) {
                if (aiContent && streamError.message !== 'STREAM_TIMEOUT') {
                    console.log('Stream error with content:', streamError.message);
                    setMessages(prev => prev.map(msg => 
                        msg.id === aiMsgId ? { ...msg, content: aiContent } : msg
                    ));
                } else if (!aiContent) {
                    throw streamError;
                }
            }
        } catch (error) {
            console.error('Chat error:', error);
            setMessages(prev => [...prev, { 
                id: Date.now() + 1, 
                role: 'assistant', 
                content: '抱歉，服务暂时不可用，请稍后重试。' 
            }]);
        } finally {
            setIsGenerating(false);
        }
    };

    return (
        <div className="chat-window">
            <div className="messages-list">
                {messages.map(msg => (
                    <MessageBubble key={msg.id} role={msg.role} content={msg.content} images={msg.images} />
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
