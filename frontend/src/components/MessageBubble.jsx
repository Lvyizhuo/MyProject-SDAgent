import React from 'react';
import { Bot, LoaderCircle, MapPinned, User } from 'lucide-react';
import { marked } from 'marked';
import './MessageBubble.css';

marked.setOptions({
    gfm: true,
    breaks: true
});

const KEYWORDS = [
    '补贴', '优惠', '门店', '流程', '路径规划', '导航', '国补', '以旧换新', '审核', '发票'
];

const normalizeContent = (raw) => {
    if (!raw) {
        return '';
    }

    let content = raw.replace(/\r\n/g, '\n');
    content = content
        .replace(/([。！？])\s*(#{1,6}\s)/g, '$1\n\n$2')
        .replace(/([。！？])\s*(\d+\.\s)/g, '$1\n$2')
        .replace(/(###\s[^\n]+)/g, '\n$1\n')
        .replace(/(\n)(-\s)/g, '\n$2');

    KEYWORDS.forEach((keyword) => {
        const re = new RegExp(keyword, 'g');
        content = content.replace(re, (match, offset, fullText) => {
            const prevTwo = fullText.slice(Math.max(0, offset - 2), offset);
            const nextTwo = fullText.slice(offset + match.length, offset + match.length + 2);
            if (prevTwo === '**' && nextTwo === '**') {
                return match;
            }
            return `**${match}**`;
        });
    });

    return content.trim();
};

const extractMapCards = (content) => {
    if (!content) {
        return [];
    }
    const lines = content.split('\n').map(line => line.trim()).filter(Boolean);
    const cards = [];
    let pendingName = '';

    lines.forEach((line) => {
        const listMatch = line.match(/^\d+\.\s*(.+)$/);
        if (listMatch) {
            pendingName = listMatch[1].replace(/^-+\s*/, '').trim();
        }

        const addressMatch = line.match(/地址[：:]\s*(.+)$/);
        if (addressMatch) {
            const address = addressMatch[1].trim();
            const title = pendingName || address;
            const link = `https://uri.amap.com/search?keyword=${encodeURIComponent(`${title} ${address}`)}`;
            cards.push({
                id: `${title}-${address}`,
                title,
                address,
                link
            });
            pendingName = '';
        }
    });

    return cards.slice(0, 4);
};

const MessageBubble = ({ role, content, images, meta }) => {
    const isAi = role === 'assistant';
    const isStatusOnly = Boolean(meta?.statusOnly);
    const isStreaming = Boolean(meta?.isStreaming);
    const statusText = meta?.statusText || 'Thinking';

    if (isStatusOnly) {
        return (
            <div className="message-row message-ai">
                <div className="avatar">
                    <Bot size={20} />
                </div>
                <div className="bubble streaming status-bubble">
                    <div className="status-line" aria-live="polite" aria-atomic="true">
                        <LoaderCircle size={16} className="spin" />
                        <span className="status-text">{statusText}</span>
                    </div>
                </div>
            </div>
        );
    }

    const normalizedContent = normalizeContent(content || '');
    const htmlContent = marked.parse(normalizedContent);
    const mapCards = isAi ? extractMapCards(normalizedContent) : [];

    return (
        <div className={`message-row ${isAi ? 'message-ai' : 'message-user'}`}>
            <div className="avatar">
                {isAi ? <Bot size={20} /> : <User size={20} />}
            </div>
            <div className={`bubble ${isStreaming ? 'streaming' : ''}`}>
                {images && images.length > 0 && (
                    <div className="message-images">
                        {images.map((src, idx) => (
                            <img key={idx} src={src} alt={`上传图片 ${idx + 1}`} className="message-image-thumb" />
                        ))}
                    </div>
                )}
                <div className="content markdown-body" dangerouslySetInnerHTML={{ __html: htmlContent }} />
                {mapCards.length > 0 && (
                    <div className="map-card-grid">
                        {mapCards.map((card) => (
                            <a
                                key={card.id}
                                className="map-card"
                                href={card.link}
                                target="_blank"
                                rel="noreferrer"
                            >
                                <div className="map-thumb">
                                    <MapPinned size={18} />
                                    <span>地图预览</span>
                                </div>
                                <div className="map-meta">
                                    <div className="map-title">{card.title}</div>
                                    <div className="map-address">{card.address}</div>
                                </div>
                            </a>
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
};
export default MessageBubble;
