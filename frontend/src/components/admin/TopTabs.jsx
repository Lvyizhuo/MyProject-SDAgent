import React from 'react';
import { Sparkles, BookOpen, Wrench, Cpu, Bell, User as UserIcon, ChevronDown } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import './TopTabs.css';

const TABS = [
    { id: 'agent', label: '智能体', icon: Sparkles },
    { id: 'knowledge', label: '知识库', icon: BookOpen },
    { id: 'tools', label: '工具', icon: Wrench },
    { id: 'models', label: '模型', icon: Cpu }
];

const TopTabs = ({ activeTab, onTabChange }) => {
    const navigate = useNavigate();
    const { user, logout } = useAuth();
    const [menuOpen, setMenuOpen] = React.useState(false);

    return (
        <header className="admin-toptabs">
            <div className="admin-toptabs-inner">
                <button className="admin-toptabs-brand" onClick={() => navigate('/home')}>
                    <span className="brand-mark">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                            <path d="M12 2L9 9 2 12l7 3 3 7 3-7 7-3-7-3z" />
                        </svg>
                    </span>
                    <span className="brand-text">AI 政策通</span>
                    <span className="brand-workspace">Lonez&apos;s Workspace</span>
                </button>

                <nav className="admin-toptabs-tabs" aria-label="主导航">
                    {TABS.map((tab) => {
                        const Icon = tab.icon;
                        return (
                            <button
                                key={tab.id}
                                className={`admin-toptabs-tab ${activeTab === tab.id ? 'active' : ''}`}
                                onClick={() => onTabChange(tab.id)}
                            >
                                <Icon size={16} strokeWidth={2.2} />
                                <span>{tab.label}</span>
                            </button>
                        );
                    })}
                </nav>

                <div className="admin-toptabs-right">
                    <button
                        className="admin-toptabs-icon-btn"
                        title="通知"
                        onClick={() => navigate('/user?tab=notifications')}
                    >
                        <Bell size={16} />
                        <span className="dot" />
                    </button>
                    <div className="admin-toptabs-user-wrap">
                        <button className="admin-toptabs-user" onClick={() => setMenuOpen((v) => !v)}>
                            <span className="user-avatar-mini">
                                {(user?.username || 'A').slice(0, 1).toUpperCase()}
                            </span>
                            <span>{user?.username || 'admin'}</span>
                            <ChevronDown size={12} className={menuOpen ? 'rotated' : ''} />
                        </button>
                        {menuOpen && (
                            <div className="admin-toptabs-dropdown" role="menu">
                                <button onClick={() => { navigate('/user'); setMenuOpen(false); }}>个人中心</button>
                                <button onClick={() => { navigate('/user'); setMenuOpen(false); }}>咨询记录</button>
                                <hr style={{ margin: '4px 0', border: 'none', borderTop: '1px solid hsl(var(--color-border-soft))' }} />
                                <button
                                    onClick={() => {
                                        logout();
                                        navigate('/home');
                                    }}
                                >
                                    退出登录
                                </button>
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </header>
    );
};

export default TopTabs;
