import React, { useEffect, useRef, useState } from 'react';
import { ChevronDown, Bell, Menu, X } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import './AdminNavbar.css';

const AdminNavbar = ({ onMobileMenuToggle, mobileMenuOpen = false }) => {
    const navigate = useNavigate();
    const { user, logout } = useAuth();
    const [userMenuOpen, setUserMenuOpen] = useState(false);
    const [scrolled, setScrolled] = useState(false);
    const userMenuRef = useRef(null);

    useEffect(() => {
        const handleScroll = () => setScrolled(window.scrollY > 30);
        const handleClickOutside = (event) => {
            if (userMenuRef.current && !userMenuRef.current.contains(event.target)) {
                setUserMenuOpen(false);
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

    return (
        <header className={`admin-navbar ${scrolled ? 'elevated' : ''}`}>
            <div className="admin-navbar-inner">
                {/* 左侧：移动端折叠 + 面包屑占位 */}
                <div className="admin-navbar-left">
                    {typeof onMobileMenuToggle === 'function' && (
                        <button
                            className="admin-navbar-mobile-toggle"
                            onClick={onMobileMenuToggle}
                            aria-label="切换菜单"
                        >
                            {mobileMenuOpen ? <X size={18} /> : <Menu size={18} />}
                        </button>
                    )}
                </div>

                {/* 右侧：通知 + 用户 */}
                <div className="admin-navbar-actions">
                    <button
                        className="notify-btn"
                        title="消息通知"
                        onClick={() => navigate('/user?tab=notifications')}
                    >
                        <Bell size={18} />
                        <span className="dot" />
                    </button>

                    <div ref={userMenuRef} className="user-entry">
                        <button
                            className="admin-user-btn"
                            onClick={() => setUserMenuOpen((prev) => !prev)}
                        >
                            <span className="admin-user-avatar-mini">
                                {(user?.username || 'A').slice(0, 1).toUpperCase()}
                            </span>
                            <span>{user?.username || 'admin'}</span>
                            <ChevronDown size={14} className={userMenuOpen ? 'rotated' : ''} />
                        </button>
                        {userMenuOpen && (
                            <div className="admin-user-dropdown" role="menu">
                                <button onClick={() => { navigate('/user'); setUserMenuOpen(false); }}>个人中心</button>
                                <button onClick={() => { navigate('/user'); setUserMenuOpen(false); }}>咨询记录</button>
                                <button onClick={() => { navigate('/user'); setUserMenuOpen(false); }}>我的收藏</button>
                                <hr style={{ margin: '4px 0', border: 'none', borderTop: '1px solid hsl(var(--color-border-soft))' }} />
                                <button onClick={handleLogout}>退出登录</button>
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </header>
    );
};

export default AdminNavbar;
