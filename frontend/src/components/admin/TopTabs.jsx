import React from 'react';
import { Sparkles, BookOpen, Wrench, Cpu, Bell, User as UserIcon, ChevronDown, Target } from 'lucide-react';
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
                        <Target size={18} />
                    </span>
                    <span className="brand-text">AI政策通</span>
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
                                <Icon size={16} />
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
                        <Bell size={18} />
                        <span className="dot" />
                    </button>
                    <div className="admin-toptabs-user-wrap">
                        <button className="admin-toptabs-user" onClick={() => setMenuOpen((v) => !v)}>
                            <UserIcon size={16} />
                            <span>{user?.username || 'admin'}</span>
                            <ChevronDown size={14} className={menuOpen ? 'rotated' : ''} />
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
