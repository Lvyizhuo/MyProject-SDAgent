import React from 'react';
import { Bot, User } from 'lucide-react';
import { marked } from 'marked';
import './MessageBubble.css';

const MessageBubble = ({ role, content, images }) => {
    const isAi = role === 'assistant';
    const htmlContent = marked.parse(content || '');

    return (
        <div className={`message-row ${isAi ? 'message-ai' : 'message-user'}`}>
            <div className="avatar">
                {isAi ? <Bot size={20} /> : <User size={20} />}
            </div>
            <div className="bubble">
                {images && images.length > 0 && (
                    <div className="message-images">
                        {images.map((src, idx) => (
                            <img key={idx} src={src} alt={`上传图片 ${idx + 1}`} className="message-image-thumb" />
                        ))}
                    </div>
                )}
                <div className="content markdown-body" dangerouslySetInnerHTML={{ __html: htmlContent }} />
            </div>
        </div>
    );
};
export default MessageBubble;
