import React from 'react';
import { Bot, LoaderCircle, MapPinned, User } from 'lucide-react';
import { marked } from 'marked';
import ReferencesBlock from './ReferencesBlock';
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
        const inlineAddressMatch = line.match(/^\d+\.\s*(.+?)\s+-\s+(.+)$/);
        if (inlineAddressMatch) {
            const title = inlineAddressMatch[1].replace(/\*\*/g, '').trim();
            const address = inlineAddressMatch[2].trim();
            if (title && address) {
                const link = `https://uri.amap.com/search?keyword=${encodeURIComponent(`${title} ${address}`)}`;
                cards.push({
                    id: `${title}-${address}`,
                    title,
                    address,
                    link
                });
                pendingName = '';
                return;
            }
        }

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

const injectReferenceMarker = (content, references) => {
    if (!content || !Array.isArray(references) || references.length === 0) {
        return content;
    }
    return content.replace(/\[(\d+)](?!\()/g, (match, rawId) => {
        const index = Number(rawId) - 1;
        if (Number.isNaN(index) || index < 0 || index >= references.length) {
            return match;
        }
        return `<button type="button" class="ref-chip" data-ref-id="${rawId}" aria-label="查看第${rawId}条参考依据">[${rawId}]</button>`;
    });
};

const MessageBubble = ({
    id,
    role,
    content,
    images,
    meta,
    references,
    activeRefId,
    expandedRefIds,
    onRefClick,
    onToggleRef
}) => {
    const isAi = role === 'assistant';
    const isStatusOnly = Boolean(meta?.statusOnly);
    const isStreaming = Boolean(meta?.isStreaming);
    const statusText = meta?.statusText || 'Thinking';

    if (isStatusOnly) {
        return (
            <div className="message-row message-ai">
                <div className="message-avatar">
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
    const referencesForMessage = references || meta?.references || [];
    const markdownWithRefs = isAi
        ? injectReferenceMarker(normalizedContent, referencesForMessage)
        : normalizedContent;
    const htmlContent = marked.parse(markdownWithRefs);
    const mapCards = isAi ? extractMapCards(normalizedContent) : [];

    const handleContentClick = (event) => {
        const target = event.target.closest('.ref-chip');
        if (!target || !onRefClick) {
            return;
        }
        const refId = target.getAttribute('data-ref-id');
        if (refId) {
            onRefClick(refId);
        }
    };

    return (
        <div className={`message-row ${isAi ? 'message-ai' : 'message-user'}`}>
            <div className="message-avatar">
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
                <div
                    className="content markdown-body"
                    dangerouslySetInnerHTML={{ __html: htmlContent }}
                    onClick={handleContentClick}
                />
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
                {isAi && referencesForMessage.length > 0 && (
                    <ReferencesBlock
                        messageId={id}
                        references={referencesForMessage}
                        expandedRefIds={expandedRefIds}
                        activeRefId={activeRefId}
                        onToggleRef={onToggleRef}
                    />
                )}
            </div>
        </div>
    );
};

export default MessageBubble;
