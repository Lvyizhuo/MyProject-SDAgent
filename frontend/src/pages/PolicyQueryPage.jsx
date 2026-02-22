import React, { useMemo, useState } from 'react';
import {
    Building2,
    Calendar,
    ChevronRight,
    FileText,
    Filter,
    Search,
    X,
    Zap
} from 'lucide-react';
import TopNavbar from '../components/TopNavbar';
import { INDUSTRIES, MOCK_POLICIES, POLICY_TYPES, REGIONS } from '../constants/mockData';
import './QueryPage.css';

const PolicyQueryPage = () => {
    const [searchTerm, setSearchTerm] = useState('');
    const [selectedIndustry, setSelectedIndustry] = useState('全部');
    const [selectedRegion, setSelectedRegion] = useState('全国');
    const [selectedType, setSelectedType] = useState('全部');
    const [selectedPolicy, setSelectedPolicy] = useState(null);
    const [interpreting, setInterpreting] = useState(false);
    const [interpretation, setInterpretation] = useState('');

    const filteredPolicies = useMemo(() => MOCK_POLICIES.filter((policy) => {
        const matchesSearch = !searchTerm
            || policy.title.includes(searchTerm)
            || policy.summary.includes(searchTerm);
        const matchesIndustry = selectedIndustry === '全部' || policy.industry === selectedIndustry;
        const matchesRegion = selectedRegion === '全国' || policy.region === selectedRegion;
        const matchesType = selectedType === '全部' || policy.type === selectedType;
        return matchesSearch && matchesIndustry && matchesRegion && matchesType;
    }), [searchTerm, selectedIndustry, selectedRegion, selectedType]);

    const handleInterpret = () => {
        if (!selectedPolicy) return;
        setInterpreting(true);
        setTimeout(() => {
            setInterpretation(`【AI解读】\n该政策核心关注“${selectedPolicy.type}”方向，重点在于降低企业合规成本、提高申报效率。\n\n建议：\n1. 先对照适用主体与时间范围；\n2. 准备企业基础材料与财务证明；\n3. 关注地方细则中的加分条款与截止时间。`);
            setInterpreting(false);
        }, 900);
    };

    return (
        <div className="query-page">
            <TopNavbar />
            <main className="query-wrap">
                <div className="query-layout">
                    <aside className="query-aside fade-in-up">
                        <h2>
                            <Filter size={18} />
                            政策筛选
                        </h2>

                        <div className="aside-section">
                            <h3>行业领域</h3>
                            <div className="chip-list">
                                {INDUSTRIES.map((item) => (
                                    <button
                                        key={item}
                                        className={selectedIndustry === item ? 'active' : ''}
                                        onClick={() => setSelectedIndustry(item)}
                                    >
                                        {item}
                                    </button>
                                ))}
                            </div>
                        </div>

                        <div className="aside-section">
                            <h3>地区范围</h3>
                            <select value={selectedRegion} onChange={(e) => setSelectedRegion(e.target.value)}>
                                {REGIONS.map((item) => <option key={item}>{item}</option>)}
                            </select>
                        </div>

                        <div className="aside-section">
                            <h3>政策类型</h3>
                            <div className="radio-list">
                                {POLICY_TYPES.map((item) => (
                                    <label key={item}>
                                        <input
                                            type="radio"
                                            checked={selectedType === item}
                                            onChange={() => setSelectedType(item)}
                                        />
                                        <span>{item}</span>
                                    </label>
                                ))}
                            </div>
                        </div>
                    </aside>

                    <section className="query-main">
                        <div className="query-search fade-in-up">
                            <Search size={20} />
                            <input
                                value={searchTerm}
                                onChange={(e) => setSearchTerm(e.target.value)}
                                placeholder="搜索政策标题、文号或关键词..."
                            />
                        </div>

                        <div className="query-list">
                            {filteredPolicies.length > 0 ? filteredPolicies.map((policy, index) => (
                                <article
                                    key={policy.id}
                                    className="query-card fade-in-up"
                                    style={{ animationDelay: `${index * 65}ms` }}
                                    onClick={() => {
                                        setSelectedPolicy(policy);
                                        setInterpretation('');
                                    }}
                                >
                                    <div className="query-card-meta">
                                        <div>
                                            <span>{policy.type}</span>
                                            <span>{policy.region}</span>
                                        </div>
                                        <time>
                                            <Calendar size={12} />
                                            {policy.date}
                                        </time>
                                    </div>
                                    <h3>{policy.title}</h3>
                                    <p>{policy.summary}</p>
                                    <div className="query-card-footer">
                                        <span>
                                            <Building2 size={14} />
                                            {policy.agency}
                                        </span>
                                        <b>
                                            查看详情
                                            <ChevronRight size={15} />
                                        </b>
                                    </div>
                                </article>
                            )) : (
                                <div className="query-empty">
                                    <FileText size={44} />
                                    <p>暂无符合条件的政策</p>
                                </div>
                            )}
                        </div>
                    </section>
                </div>
            </main>

            {selectedPolicy && (
                <div className="query-modal" onClick={() => setSelectedPolicy(null)}>
                    <div className="query-modal-card" onClick={(e) => e.stopPropagation()}>
                        <header>
                            <div>
                                <h3>{selectedPolicy.title}</h3>
                                <p>{selectedPolicy.agency} · {selectedPolicy.date}</p>
                            </div>
                            <button onClick={() => setSelectedPolicy(null)}>
                                <X size={18} />
                            </button>
                        </header>

                        <div className="query-modal-body">
                            <div className="query-modal-actions">
                                <button onClick={handleInterpret} disabled={interpreting}>
                                    <Zap size={16} />
                                    {interpreting ? 'AI解读中...' : 'AI智能解读'}
                                </button>
                            </div>

                            {interpretation && (
                                <article className="ai-interpretation">
                                    <h4>AI 解读报告</h4>
                                    <pre>{interpretation}</pre>
                                </article>
                            )}

                            <article className="query-policy-content">
                                <h4>政策原文</h4>
                                <pre>{selectedPolicy.content}</pre>
                            </article>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default PolicyQueryPage;
