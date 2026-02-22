import React, { useState } from 'react';
import {
    Bell,
    Building2,
    History,
    LogOut,
    Mail,
    Phone,
    Settings,
    Shield,
    Star,
    User
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import TopNavbar from '../components/TopNavbar';
import './UserCenterPage.css';

const MENU_ITEMS = [
    { id: 'profile', label: '个人资料', icon: User },
    { id: 'history', label: '咨询记录', icon: History },
    { id: 'favorites', label: '我的收藏', icon: Star },
    { id: 'security', label: '账号安全', icon: Shield },
    { id: 'notifications', label: '消息通知', icon: Bell },
    { id: 'settings', label: '通用设置', icon: Settings }
];

const UserCenterPage = () => {
    const navigate = useNavigate();
    const { user, isAuthenticated } = useAuth();
    const [activeMenu, setActiveMenu] = useState('profile');

    if (!isAuthenticated) {
        return (
            <div className="user-center-page">
                <TopNavbar />
                <main className="user-empty-wrap fade-in-up">
                    <h1>用户中心</h1>
                    <p>请先登录后查看个人资料与历史记录。</p>
                    <button onClick={() => navigate('/login?redirect=/user')}>去登录</button>
                </main>
            </div>
        );
    }

    return (
        <div className="user-center-page">
            <TopNavbar />
            <main className="user-center-wrap">
                <div className="user-layout">
                    <aside className="user-side fade-in-up">
                        <div className="user-card">
                            <div className="user-card-avatar"><User size={38} /></div>
                            <h3>{user?.username || '用户'}</h3>
                            <p>企业认证用户</p>
                        </div>

                        <div className="menu-card">
                            {MENU_ITEMS.map((item) => {
                                const Icon = item.icon;
                                return (
                                    <button
                                        key={item.id}
                                        className={activeMenu === item.id ? 'active' : ''}
                                        onClick={() => setActiveMenu(item.id)}
                                    >
                                        <Icon size={16} />
                                        {item.label}
                                    </button>
                                );
                            })}
                            <div className="divider" />
                            <button className="logout">
                                <LogOut size={16} />
                                退出登录
                            </button>
                        </div>
                    </aside>

                    <section className="user-main fade-in-up">
                        {activeMenu === 'profile' && (
                            <div className="panel">
                                <h2>基本信息</h2>
                                <div className="form-grid">
                                    <label>
                                        姓名
                                        <input defaultValue={user?.username || '用户'} />
                                    </label>
                                    <label>
                                        企业名称
                                        <input defaultValue="某某科技发展有限公司" />
                                    </label>
                                    <label>
                                        联系电话
                                        <div className="input-row">
                                            <Phone size={14} />
                                            <input defaultValue="138****8888" />
                                        </div>
                                    </label>
                                    <label>
                                        电子邮箱
                                        <div className="input-row">
                                            <Mail size={14} />
                                            <input defaultValue="user@company.com" />
                                        </div>
                                    </label>
                                    <label>
                                        统一社会信用代码
                                        <input defaultValue="91370000XXXXXX" />
                                    </label>
                                    <label>
                                        所属行业
                                        <input defaultValue="人工智能" />
                                    </label>
                                </div>
                                <div className="panel-action">
                                    <button>保存修改</button>
                                </div>
                            </div>
                        )}

                        {activeMenu === 'history' && (
                            <div className="panel">
                                <h2>咨询历史</h2>
                                <div className="history-list">
                                    {['以旧换新补贴咨询', '高新企业认定条件', '研发费用加计扣除比例'].map((item) => (
                                        <article key={item}>
                                            <div>
                                                <h3>{item}</h3>
                                                <p>2026-02-22 · 智能问答</p>
                                            </div>
                                        </article>
                                    ))}
                                </div>
                            </div>
                        )}

                        {activeMenu === 'favorites' && (
                            <div className="panel">
                                <h2>我的收藏</h2>
                                <div className="fav-grid">
                                    <article>
                                        <span>工信部</span>
                                        <h3>关于加快推动人工智能产业高质量发展的若干措施</h3>
                                    </article>
                                    <article>
                                        <span>财政部</span>
                                        <h3>中小企业数字化转型专项资金管理办法</h3>
                                    </article>
                                </div>
                            </div>
                        )}

                        {['security', 'notifications', 'settings'].includes(activeMenu) && (
                            <div className="panel developing">
                                <Settings size={52} />
                                <h2>功能开发中</h2>
                                <p>该模块将按后端接口进度逐步开放。</p>
                            </div>
                        )}
                    </section>
                </div>
            </main>
        </div>
    );
};

export default UserCenterPage;
