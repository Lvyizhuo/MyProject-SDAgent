import React from 'react';
import { BookOpen } from 'lucide-react';
import './KnowledgeBaseTab.css';

const KnowledgeBaseTab = () => {
    return (
        <div className="knowledge-base-tab">
            <div className="placeholder-icon">
                <BookOpen size={36} />
            </div>
            <h2>知识库管理</h2>
            <p>文档上传、向量索引管理、知识库配置等功能即将上线，敬请期待。</p>
            <span className="coming-soon-badge">即将推出</span>
        </div>
    );
};

export default KnowledgeBaseTab;
