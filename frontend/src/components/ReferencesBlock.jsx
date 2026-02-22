import React from 'react';
import './ReferencesBlock.css';

const formatDate = (value) => {
    if (!value) return '未标注';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;
    return date.toLocaleDateString('zh-CN');
};

const getDomain = (url) => {
    if (!url) return '未标注';
    try {
        return new URL(url).hostname.replace(/^www\./, '');
    } catch {
        return url;
    }
};

const highlightText = (text, keywords = []) => {
    if (!text) return '暂无原文片段';
    let output = text;
    keywords
        .filter(Boolean)
        .slice(0, 4)
        .forEach((keyword) => {
            const escaped = keyword.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
            const regex = new RegExp(`(${escaped})`, 'gi');
            output = output.replace(regex, '<mark>$1</mark>');
        });
    return output;
};

const getRegion = (reference) => {
    if (!reference) return '未标注';
    return reference.region || reference.city || reference.cityCode || reference.scope || '未标注';
};

const ReferencesBlock = ({
    messageId,
    references,
    expandedRefIds,
    activeRefId,
    onToggleRef
}) => {
    if (!references || references.length === 0) {
        return null;
    }

    return (
        <section className="references-block" aria-label="参考依据">
            <h4 className="references-title">参考依据</h4>
            <div className="reference-list">
                {references.map((reference, index) => {
                    const refId = String(index + 1);
                    const cardKey = `${messageId}-${refId}`;
                    const isExpanded = Boolean(expandedRefIds[cardKey]);
                    const isActive = activeRefId === cardKey;
                    const title = reference.title || reference.name || `参考来源 ${refId}`;
                    const url = reference.url || reference.link || '';
                    const date = reference.publishedAt || reference.date || reference.timestamp || '';
                    const snippet = reference.snippet || reference.quote || reference.content || '';
                    const keywords = reference.keywords || [];

                    return (
                        <article
                            key={cardKey}
                            id={`ref-${cardKey}`}
                            className={`reference-card ${isActive ? 'active' : ''}`}
                        >
                            <div className="reference-head">
                                <span className="reference-id">[{refId}]</span>
                                <h5 className="reference-name" title={title}>{title}</h5>
                            </div>
                            <div className="reference-meta">
                                <span className="meta-item">{getDomain(url)}</span>
                                <span className="meta-item">{formatDate(date)}</span>
                                <span className="meta-item">{getRegion(reference)}</span>
                                <button
                                    type="button"
                                    className="toggle-ref-btn"
                                    onClick={() => onToggleRef(cardKey)}
                                    aria-expanded={isExpanded}
                                >
                                    {isExpanded ? '收起' : '展开'}
                                </button>
                            </div>
                            {isExpanded && (
                                <div className="reference-detail">
                                    <p
                                        className="reference-snippet"
                                        dangerouslySetInnerHTML={{ __html: highlightText(snippet, keywords) }}
                                    />
                                    {url && (
                                        <a href={url} target="_blank" rel="noreferrer" className="reference-link">
                                            打开原文
                                        </a>
                                    )}
                                </div>
                            )}
                        </article>
                    );
                })}
            </div>
        </section>
    );
};

export default ReferencesBlock;
