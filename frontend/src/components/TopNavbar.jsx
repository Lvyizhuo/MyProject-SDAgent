import React, { useEffect, useRef, useState } from 'react';
import {
    Bell,
    ChevronDown,
    FileText,
    Menu,
    MessageSquare,
    Search,
    Target,
    User,
    X
} from 'lucide-react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import './TopNavbar.css';

const NAV_ITEMS = [
    { path: '/home', label: '首页', icon: Search },
    { path: '/policies', label: '政策查询', icon: FileText },
    { path: '/matching', label: '政策匹配', icon: Target },
    { path: '/chat', label: '智能问答', icon: MessageSquare }
];

const TopNavbar = () => {
    const navigate = useNavigate();
    const { isAuthenticated, user, logout } = useAuth();
    const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
    const [userMenuOpen, setUserMenuOpen] = useState(false);
    const [scrolled, setScrolled] = useState(false);
    const userMenuRef = useRef(null);
    const mobileMenuBoxRef = useRef(null);

    useEffect(() => {
        const handleScroll = () => setScrolled(window.scrollY > 30);
        const handleClickOutside = (event) => {
            if (userMenuRef.current && !userMenuRef.current.contains(event.target)) {
                setUserMenuOpen(false);
            }
            if (mobileMenuBoxRef.current && !mobileMenuBoxRef.current.contains(event.target)) {
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

    const handleAuthAction = () => {
        if (isAuthenticated) {
            logout();
            navigate('/home');
            return;
        }
        navigate('/login?redirect=/user');
    };

    return (
        <header className={`top-navbar ${scrolled ? 'elevated' : ''}`}>
            <div className="top-navbar-inner">
                <button className="brand" onClick={() => navigate('/home')}>
                    <span className="brand-mark">
                        <Target size={18} />
                    </span>
                    <span className="brand-text">AI政策通</span>
                </button>

                <nav className="top-nav-links" aria-label="主导航">
                    {NAV_ITEMS.map((item) => {
                        const Icon = item.icon;
                        return (
                            <NavLink
                                key={item.path}
                                to={item.path}
                                className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
                                onClick={() => {
                                    setMobileMenuOpen(false);
                                    setUserMenuOpen(false);
                                }}
                            >
                                <Icon size={16} />
                                <span>{item.label}</span>
                            </NavLink>
                        );
                    })}
                </nav>

                <div className="top-navbar-actions">
                    <button className="notify-btn" title="通知">
                        <Bell size={18} />
                        <span className="dot" />
                    </button>

                    <div className="user-entry" ref={userMenuRef}>
                        <button className="user-center-btn" onClick={() => setUserMenuOpen(prev => !prev)}>
                            <User size={16} />
                            <span>{isAuthenticated ? user?.username || '用户中心' : '用户中心'}</span>
                            <ChevronDown size={14} className={userMenuOpen ? 'rotated' : ''} />
                        </button>
                        {userMenuOpen && (
                            <div className="user-dropdown" role="menu">
                                <button onClick={() => navigate('/user')}>个人信息</button>
                                <button onClick={() => navigate('/user')}>咨询记录</button>
                                <button onClick={() => navigate('/user')}>我的收藏</button>
                                <button onClick={handleAuthAction}>{isAuthenticated ? '退出登录' : '去登录'}</button>
                            </div>
                        )}
                    </div>

                    <div ref={mobileMenuBoxRef} className="mobile-menu-wrap">
                        <button className="mobile-menu-btn" onClick={() => setMobileMenuOpen(prev => !prev)}>
                            {mobileMenuOpen ? <X size={20} /> : <Menu size={20} />}
                        </button>
                        {mobileMenuOpen && (
                            <div className="mobile-menu">
                                {NAV_ITEMS.map((item) => {
                                    const Icon = item.icon;
                                    return (
                                        <NavLink
                                            key={item.path}
                                            to={item.path}
                                            className={({ isActive }) => `mobile-nav-link ${isActive ? 'active' : ''}`}
                                            onClick={() => setMobileMenuOpen(false)}
                                        >
                                            <Icon size={16} />
                                            <span>{item.label}</span>
                                        </NavLink>
                                    );
                                })}
                                <button
                                    className="mobile-nav-link"
                                    onClick={() => {
                                        setMobileMenuOpen(false);
                                        navigate('/user');
                                    }}
                                >
                                    <User size={16} />
                                    <span>用户中心</span>
                                </button>
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </header>
    );
};

export default TopNavbar;
