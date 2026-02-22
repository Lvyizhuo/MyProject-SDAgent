import React, { useMemo, useState } from 'react';
import {
    AlertCircle,
    Building2,
    CheckCircle2,
    ChevronRight,
    Loader2,
    MapPin,
    Target,
    Users
} from 'lucide-react';
import TopNavbar from '../components/TopNavbar';
import { INDUSTRIES, MOCK_POLICIES, REGIONS } from '../constants/mockData';
import './MatchPage.css';

const PolicyMatchingPage = () => {
    const [step, setStep] = useState(1);
    const [loading, setLoading] = useState(false);
    const [formData, setFormData] = useState({
        companyName: '',
        industry: '人工智能',
        region: '山东',
        scale: '小型企业'
    });

    const results = useMemo(() => {
        if (step !== 2) return [];
        return MOCK_POLICIES.slice(0, 3).map((policy, index) => ({
            ...policy,
            score: 93 - index * 7,
            suggestion: '建议优先准备申报主体证明、财务数据与项目说明材料，并关注地方申报时限。'
        }));
    }, [step]);

    const handleMatch = () => {
        if (!formData.companyName.trim()) return;
        setLoading(true);
        setTimeout(() => {
            setStep(2);
            setLoading(false);
        }, 800);
    };

    return (
        <div className="match-page">
            <TopNavbar />
            <main className="match-wrap">
                <header className="match-header fade-in-up">
                    <h1>政策精准匹配</h1>
                    <p>填写企业基本信息，AI 为您匹配最适合的政策建议。</p>
                </header>

                {step === 1 ? (
                    <section className="match-form-card fade-in-up">
                        <div className="match-form-grid">
                            <label>
                                <span><Building2 size={15} /> 企业名称</span>
                                <input
                                    value={formData.companyName}
                                    onChange={(e) => setFormData({ ...formData, companyName: e.target.value })}
                                    placeholder="请输入企业全称"
                                />
                            </label>

                            <label>
                                <span><Target size={15} /> 所属行业</span>
                                <select
                                    value={formData.industry}
                                    onChange={(e) => setFormData({ ...formData, industry: e.target.value })}
                                >
                                    {INDUSTRIES.filter((i) => i !== '全部').map((i) => <option key={i}>{i}</option>)}
                                </select>
                            </label>

                            <label>
                                <span><MapPin size={15} /> 注册地区</span>
                                <select
                                    value={formData.region}
                                    onChange={(e) => setFormData({ ...formData, region: e.target.value })}
                                >
                                    {REGIONS.filter((r) => r !== '全国').map((r) => <option key={r}>{r}</option>)}
                                </select>
                            </label>

                            <label>
                                <span><Users size={15} /> 企业规模</span>
                                <select
                                    value={formData.scale}
                                    onChange={(e) => setFormData({ ...formData, scale: e.target.value })}
                                >
                                    <option>微型企业</option>
                                    <option>小型企业</option>
                                    <option>中型企业</option>
                                    <option>大型企业</option>
                                </select>
                            </label>
                        </div>

                        <div className="match-action">
                            <button onClick={handleMatch} disabled={loading || !formData.companyName.trim()}>
                                {loading ? <Loader2 size={18} className="spin" /> : <Target size={18} />}
                                {loading ? 'AI 正在匹配...' : '开始智能匹配'}
                            </button>
                        </div>

                        <footer>
                            <AlertCircle size={14} />
                            您的信息仅用于政策匹配，我们将严格保护您的隐私安全。
                        </footer>
                    </section>
                ) : (
                    <section className="match-result-wrap fade-in-up">
                        <div className="match-result-head">
                            <div>
                                <h2>匹配结果</h2>
                                <p>为您找到 {results.length} 项高度契合的政策</p>
                            </div>
                            <button onClick={() => setStep(1)}>重新匹配</button>
                        </div>

                        <div className="match-result-list">
                            {results.map((item, index) => (
                                <article key={item.id} className="match-result-card fade-in-up" style={{ animationDelay: `${index * 80}ms` }}>
                                    <div className="main">
                                        <div className="tags">
                                            <span>{item.type}</span>
                                            <small>{item.agency}</small>
                                        </div>
                                        <h3>{item.title}</h3>
                                        <p>{item.summary}</p>
                                        <div className="suggestion">
                                            <h4>
                                                <CheckCircle2 size={15} />
                                                适配建议
                                            </h4>
                                            <p>{item.suggestion}</p>
                                        </div>
                                    </div>
                                    <aside>
                                        <strong>{item.score}%</strong>
                                        <span>匹配度</span>
                                        <button>
                                            详情
                                            <ChevronRight size={14} />
                                        </button>
                                    </aside>
                                </article>
                            ))}
                        </div>
                    </section>
                )}
            </main>
        </div>
    );
};

export default PolicyMatchingPage;
