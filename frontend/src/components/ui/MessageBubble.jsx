import React, { useState } from 'react';
import { Bot, User, Copy, RefreshCw, ThumbsUp, ThumbsDown, ChevronRight, MapPinned, LoaderCircle, FileText } from 'lucide-react';
import { marked } from 'marked';
import '../ui/chat-ui.css';

marked.setOptions({ gfm: true, breaks: true });

const KEYWORDS = [
    '补贴', '优惠', '门店', '流程', '路径规划', '导航', '国补', '以旧换新', '审核', '发票'
];

const normalizeContent = (raw) => {
    if (!raw) return '';
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
            if (prevTwo === '**' && nextTwo === '**') return match;
            return `**${match}**`;
        });
    });
    return content.trim();
};

const extractMapCards = (content) => {
    if (!content) return [];
    const lines = content.split('\n').map((line) => line.trim()).filter(Boolean);
    const cards = [];
    let pendingName = '';

    lines.forEach((line) => {
        const inlineAddressMatch = line.match(/^\d+\.\s*(.+?)\s+-\s+(.+)$/);
        if (inlineAddressMatch) {
            const title = inlineAddressMatch[1].replace(/\*\*/g, '').trim();
            const address = inlineAddressMatch[2].trim();
            if (title && address) {
                const link = `https://uri.amap.com/search?keyword=${encodeURIComponent(`${title} ${address}`)}`;
                cards.push({ id: `${title}-${address}`, title, address, link });
                pendingName = '';
                return;
            }
        }
        const listMatch = line.match(/^\d+\.\s*(.+)$/);
        if (listMatch) pendingName = listMatch[1].replace(/^-+\s*/, '').trim();
        const addressMatch = line.match(/地址[：:]\s*(.+)$/);
        if (addressMatch) {
            const address = addressMatch[1].trim();
            const title = pendingName || address;
            const link = `https://uri.amap.com/search?keyword=${encodeURIComponent(`${title} ${address}`)}`;
            cards.push({ id: `${title}-${address}`, title, address, link });
            pendingName = '';
        }
    });
    return cards.slice(0, 4);
};

const injectReferenceMarker = (content, references) => {
    if (!content || !Array.isArray(references) || references.length === 0) return content;
    return content.replace(/\[(\d+)](?!\()/g, (match, rawId) => {
        const index = Number(rawId) - 1;
        if (Number.isNaN(index) || index < 0 || index >= references.length) return match;
        return `<button type="button" class="ui-ref-chip" data-ref-id="${rawId}">[${rawId}]</button>`;
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
    const [thinkingExpanded, setThinkingExpanded] = useState(true);
    const isAi = role === 'assistant';
    const isStatusOnly = Boolean(meta?.statusOnly);
    const statusText = meta?.statusText || '正在思考';

    if (isStatusOnly) {
        return (
            <div className="ui-msg-ai">
                <div className="ui-msg-ai-avatar">
                    <Bot size={16} />
                </div>
                <div className="ui-msg-ai-body">
                    <div className="ui-msg-ai-name">AI政策助手</div>
                    <div className="ui-status-indicator">
                        <div className="ui-status-dots">
                            <span></span><span></span><span></span>
                        </div>
                        {statusText}
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
        const target = event.target.closest('.ui-ref-chip');
        if (!target || !onRefClick) return;
        const refId = target.getAttribute('data-ref-id');
        if (refId) onRefClick(refId);
    };

    const handleCopy = () => {
        navigator.clipboard?.writeText(content || '');
    };

    return (
        <div className={isAi ? 'ui-msg-ai' : 'ui-msg-user'}>
            {isAi && (
                <div className="ui-msg-ai-avatar">
                    <Bot size={16} />
                </div>
            )}
            {isAi ? (
                <div className="ui-msg-ai-body">
                    <div className="ui-msg-ai-name">AI政策助手</div>

                    {/* 思考过程块 */}
                    {meta?.thinking && (
                        <div className={`ui-thinking-block ${thinkingExpanded ? 'expanded' : ''}`}>
                            <div
                                className="ui-thinking-header"
                                onClick={() => setThinkingExpanded(!thinkingExpanded)}
                            >
                                <ChevronRight size={14} className="chevron" />
                                {thinkingExpanded ? '思考过程' : '已完成思考'}
                            </div>
                            <div className="ui-thinking-content">
                                {meta.thinking}
                            </div>
                        </div>
                    )}

                    {images && images.length > 0 && (
                        <div className="ui-msg-images">
                            {images.map((src, idx) => (
                                <img key={idx} src={src} alt={`上传图片 ${idx + 1}`} className="ui-msg-image-thumb" />
                            ))}
                        </div>
                    )}

                    <div
                        className="ui-msg-content"
                        dangerouslySetInnerHTML={{ __html: htmlContent }}
                        onClick={handleContentClick}
                    />

                    {mapCards.length > 0 && (
                        <div className="ui-map-card-grid">
                            {mapCards.map((card) => (
                                <a
                                    key={card.id}
                                    className="ui-map-card"
                                    href={card.link}
                                    target="_blank"
                                    rel="noreferrer"
                                >
                                    <div className="ui-map-thumb">
                                        <MapPinned size={16} />
                                        <span>地图预览</span>
                                    </div>
                                    <div className="ui-map-meta">
                                        <div className="ui-map-title">{card.title}</div>
                                        <div className="ui-map-address">{card.address}</div>
                                    </div>
                                </a>
                            ))}
                        </div>
                    )}

                    {/* 参考来源 */}
                    {referencesForMessage.length > 0 && (
                        <div className="ui-references-block">
                            <div className="ui-references-title">
                                <FileText size={13} />
                                参考来源
                            </div>
                            {referencesForMessage.map((ref, index) => {
                                const refId = String(index + 1);
                                const cardKey = `${id}-${refId}`;
                                const isExpanded = Boolean(expandedRefIds?.[cardKey]);
                                const title = ref.title || ref.name || `参考来源 ${refId}`;
                                const url = ref.url || ref.link || '';

                                return (
                                    <div
                                        key={cardKey}
                                        id={`ref-${cardKey}`}
                                        className="ui-ref-source"
                                        onClick={() => onToggleRef?.(cardKey)}
                                        style={{
                                            borderColor: isExpanded ? 'var(--ui-primary-tint)' : 'var(--ui-border-soft)',
                                            background: isExpanded ? 'var(--ui-primary-soft)' : 'var(--ui-bg-surface)'
                                        }}
                                    >
                                        <div className="ui-ref-source-num">{refId}</div>
                                        <div className="ui-ref-source-text">{title}</div>
                                        {url && (
                                            <a
                                                href={url}
                                                target="_blank"
                                                rel="noreferrer"
                                                onClick={(e) => e.stopPropagation()}
                                                style={{ fontSize: '0.7rem', color: 'var(--ui-primary)', flexShrink: 0 }}
                                            >
                                                原文
                                            </a>
                                        )}
                                    </div>
                                );
                            })}
                        </div>
                    )}

                    <div className="ui-msg-actions">
                        <button className="ui-msg-action-btn" onClick={handleCopy} title="复制">
                            <Copy size={14} />
                        </button>
                        <button className="ui-msg-action-btn" title="重新生成">
                            <RefreshCw size={14} />
                        </button>
                        <button className="ui-msg-action-btn" title="赞">
                            <ThumbsUp size={14} />
                        </button>
                        <button className="ui-msg-action-btn" title="踩">
                            <ThumbsDown size={14} />
                        </button>
                    </div>
                </div>
            ) : (
                <>
                    <div className="ui-msg-user-bubble">
                        {images && images.length > 0 && (
                            <div className="ui-msg-images">
                                {images.map((src, idx) => (
                                    <img key={idx} src={src} alt={`上传图片 ${idx + 1}`} className="ui-msg-image-thumb" />
                                ))}
                            </div>
                        )}
                        {content}
                    </div>
                    <div className="ui-msg-user-avatar">
                        <User size={16} />
                    </div>
                </>
            )}
        </div>
    );
};

export default MessageBubble;
