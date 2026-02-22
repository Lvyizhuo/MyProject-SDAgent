import React from 'react';
import {
    ChevronRight,
    FileText,
    MessageSquare,
    Search,
    ShieldCheck,
    Target,
    TrendingUp,
    Zap
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import TopNavbar from '../components/TopNavbar';
import { MOCK_POLICIES } from '../constants/mockData';
import './HomePage.css';

const FEATURES = [
    {
        title: '政策查询',
        desc: '海量政策库，多维筛选。',
        route: '/policies',
        icon: FileText,
        tone: 'blue'
    },
    {
        title: '精准匹配',
        desc: '企业画像，一键匹配。',
        route: '/matching',
        icon: Target,
        tone: 'green'
    },
    {
        title: '智能问答',
        desc: '7x24小时，即问即答。',
        route: '/chat',
        icon: MessageSquare,
        tone: 'indigo'
    },
    {
        title: '智能解读',
        desc: 'AI重点提炼，快速理解政策。',
        route: '/policies',
        icon: Zap,
        tone: 'amber'
    }
];

const HomePage = () => {
    const navigate = useNavigate();

    return (
        <div className="home-page">
            <TopNavbar />
            <main className="home-wrap">
                <section className="hero-block">
                    <div className="hero-glow" />
                    <div className="hero-content fade-in-up">
                        <h1>智能政策咨询，触手可及</h1>
                        <p>
                            基于大模型技术的专业政策服务平台，为您提供政策查询、智能解读、精准匹配及
                            AI 问答一体化支持。
                        </p>
                        <div className="hero-search">
                            <div className="search-input-wrap">
                                <Search size={20} />
                                <input placeholder="输入政策关键词或您的问题，例如：以旧换新补贴政策..." />
                            </div>
                            <button onClick={() => navigate('/chat')}>智能咨询</button>
                        </div>
                        <div className="hero-tags">
                            <span>热门搜索：</span>
                            {['专精特新', '高新企业认定', '研发费用加计扣除', '人才补贴'].map((tag) => (
                                <button key={tag}>{tag}</button>
                            ))}
                        </div>
                    </div>
                </section>

                <section className="feature-block home-section">
                    <div className="feature-grid">
                        {FEATURES.map((item, index) => {
                            const Icon = item.icon;
                            return (
                                <article
                                    key={item.title}
                                    className={`feature-card ${item.tone} fade-in-up`}
                                    style={{ animationDelay: `${index * 90}ms` }}
                                    onClick={() => navigate(item.route)}
                                >
                                    <div className="feature-icon">
                                        <Icon size={22} />
                                    </div>
                                    <h3>{item.title}</h3>
                                    <p>{item.desc}</p>
                                    <span>
                                        立即体验
                                        <ChevronRight size={14} />
                                    </span>
                                </article>
                            );
                        })}
                    </div>
                </section>

                <section className="home-section">
                    <div className="section-title-row">
                        <div>
                            <h2>
                                <TrendingUp size={22} />
                                热门政策推荐
                            </h2>
                            <p>为您精选近期关注度最高的政策文件</p>
                        </div>
                        <button onClick={() => navigate('/policies')}>
                            查看更多
                            <ChevronRight size={16} />
                        </button>
                    </div>
                    <div className="policy-grid">
                        {MOCK_POLICIES.slice(0, 4).map((policy, index) => (
                            <article key={policy.id} className="policy-card fade-in-up" style={{ animationDelay: `${index * 80}ms` }}>
                                <div className="policy-tags">
                                    <span>{policy.type}</span>
                                    <time>{policy.date}</time>
                                </div>
                                <h3>{policy.title}</h3>
                                <p>{policy.summary}</p>
                            </article>
                        ))}
                    </div>
                </section>

                <section className="trust-block">
                    <h2>为什么选择 AI 政策通</h2>
                    <div className="trust-grid">
                        <article>
                            <ShieldCheck size={34} />
                            <h3>权威数据</h3>
                            <p>直连官方渠道，确保政策信息真实可靠。</p>
                        </article>
                        <article>
                            <Target size={34} />
                            <h3>智能匹配</h3>
                            <p>结合企业画像，实现人策精准对接。</p>
                        </article>
                        <article>
                            <Zap size={34} />
                            <h3>即时响应</h3>
                            <p>AI 全天候在线，秒级回复政策疑问。</p>
                        </article>
                    </div>
                </section>
            </main>

            <footer className="home-footer">
                <div className="home-footer-inner">
                    <div className="footer-brand">
                        <Target size={22} />
                        <span>AI政策通</span>
                        <p>为您提供专业、及时、可追溯的政策咨询服务。</p>
                    </div>
                    <div>
                        <h4>快速链接</h4>
                        <button onClick={() => navigate('/policies')}>政策查询</button>
                        <button onClick={() => navigate('/matching')}>政策匹配</button>
                        <button onClick={() => navigate('/chat')}>智能问答</button>
                    </div>
                    <div>
                        <h4>帮助与支持</h4>
                        <p>隐私政策</p>
                        <p>服务条款</p>
                        <p>常见问题</p>
                    </div>
                    <div>
                        <h4>联系我们</h4>
                        <p>邮箱：support@aipolicy.com</p>
                        <p>电话：400-123-4567</p>
                        <p>地址：山东省济南市高新区</p>
                    </div>
                </div>
                <p className="copyright">© 2026 AI政策咨询智能体. All rights reserved.</p>
            </footer>
        </div>
    );
};

export default HomePage;
