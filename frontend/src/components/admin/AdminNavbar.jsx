import React, { useEffect, useRef, useState } from 'react';
import { Bot, BookOpen, Wrench, ChevronDown, Menu, X, ArrowLeft, Target, User } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import './AdminNavbar.css';

const NAV_ITEMS = [
    { id: 'agent', label: '智能体', icon: Bot },
    { id: 'knowledge', label: '知识库', icon: BookOpen },
    { id: 'tools', label: '工具', icon: Wrench }
];

const AdminNavbar = ({ activeTab, onTabChange }) => {
    const navigate = useNavigate();
    const { user, logout } = useAuth();
    const [userMenuOpen, setUserMenuOpen] = useState(false);
    const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
    const [scrolled, setScrolled] = useState(false);
    const userMenuRef = useRef(null);
    const mobileMenuRef = useRef(null);

    useEffect(() => {
        const handleScroll = () => setScrolled(window.scrollY > 30);
        const handleClickOutside = (event) => {
            if (userMenuRef.current && !userMenuRef.current.contains(event.target)) {
                setUserMenuOpen(false);
            }
            if (mobileMenuRef.current && !mobileMenuRef.current.contains(event.target)) {
                setMobileMenuOpen(false);
            }
        };

        window.addEventListener('scroll', handleScroll);
        document.addEventListener('click', handleClickOutside);
        return () => {
            window.removeEventListener('scroll', handleScroll);
            document.removeEventListener('click', handleClickOutside);
        };
    }, []);

    const handleLogout = () => {
        logout();
        navigate('/home');
    };

    const handleTabClick = (tabId) => {
        onTabChange(tabId);
        setMobileMenuOpen(false);
    };

    return (
        <header className={`admin-navbar ${scrolled ? 'elevated' : ''}`}>
            <div className="admin-navbar-inner">
                {/* Brand */}
                <button className="brand" onClick={() => navigate('/home')}>
                    <span className="brand-mark">
                        <Target size={16} />
                    </span>
                    <span className="brand-text">AI政策通</span>
                    <span className="brand-subtitle">管理控制台</span>
                </button>

                {/* Navigation Links */}
                <nav className="admin-nav-links" aria-label="管理导航">
                    {NAV_ITEMS.map((item) => {
                        const Icon = item.icon;
                        return (
                            <button
                                key={item.id}
                                className={`admin-nav-link ${activeTab === item.id ? 'active' : ''}`}
                                onClick={() => handleTabClick(item.id)}
                            >
                                <Icon size={16} />
                                <span>{item.label}</span>
                            </button>
                        );
                    })}
                </nav>

                {/* Actions */}
                <div className="admin-navbar-actions">
                    <button
                        className="back-link"
                        onClick={() => navigate('/home')}
                        title="返回主站"
                    >
                        <ArrowLeft size={16} />
                        <span>返回主站</span>
                    </button>

                    <div ref={userMenuRef} className="user-entry">
                        <button
                            className="admin-user-btn"
                            onClick={() => setUserMenuOpen(prev => !prev)}
                        >
                            <User size={16} />
                            <span>{user?.username || '用户中心'}</span>
                            <ChevronDown size={14} className={userMenuOpen ? 'rotated' : ''} />
                        </button>
                        {userMenuOpen && (
                            <div className="admin-user-dropdown" role="menu">
                                <button onClick={() => { navigate('/user'); setUserMenuOpen(false); }}>个人中心</button>
                                <button onClick={() => { navigate('/user'); setUserMenuOpen(false); }}>咨询记录</button>
                                <button onClick={() => { navigate('/user'); setUserMenuOpen(false); }}>我的收藏</button>
                                <button onClick={handleLogout}>退出登录</button>
                            </div>
                        )}
                    </div>

                    {/* Mobile Menu */}
                    <div ref={mobileMenuRef} className="mobile-menu-wrap">
                        <button
                            className="mobile-menu-btn"
                            onClick={() => setMobileMenuOpen(prev => !prev)}
                        >
                            {mobileMenuOpen ? <X size={20} /> : <Menu size={20} />}
                        </button>
                        {mobileMenuOpen && (
                            <div className="mobile-admin-menu">
                                {NAV_ITEMS.map((item) => {
                                    const Icon = item.icon;
                                    return (
                                        <button
                                            key={item.id}
                                            className={`mobile-admin-nav-link ${activeTab === item.id ? 'active' : ''}`}
                                            onClick={() => handleTabClick(item.id)}
                                        >
                                            <Icon size={18} />
                                            <span>{item.label}</span>
                                        </button>
                                    );
                                })}
                                <hr style={{ margin: '4px 0', border: 'none', borderTop: `1px solid hsl(var(--color-border))` }} />
                                <button
                                    className="mobile-admin-nav-link"
                                    onClick={() => navigate('/home')}
                                >
                                    <ArrowLeft size={18} />
                                    <span>返回主站</span>
                                </button>
                                <button
                                    className="mobile-admin-nav-link"
                                    onClick={handleLogout}
                                >
                                    <span>退出登录</span>
                                </button>
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </header>
    );
};

export default AdminNavbar;
