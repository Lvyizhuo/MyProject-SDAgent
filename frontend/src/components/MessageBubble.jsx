import React from 'react';
import { Bot, User } from 'lucide-react';
import { marked } from 'marked';
import './MessageBubble.css';

const MessageBubble = ({ role, content }) => {
    const isAi = role === 'assistant';
    const htmlContent = marked.parse(content || '');

    return (
        <div className={`message-row ${isAi ? 'message-ai' : 'message-user'}`}>
            <div className="avatar">
                {isAi ? <Bot size={20} /> : <User size={20} />}
            </div>
            <div className="bubble">
                <div className="content markdown-body" dangerouslySetInnerHTML={{ __html: htmlContent }} />
            </div>
        </div>
    );
};
export default MessageBubble;
