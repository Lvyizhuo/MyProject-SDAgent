import React, { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { Building2, User, Lock, ArrowRight, UserPlus } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import TopNavbar from '../components/TopNavbar';
import './LoginPage.css';

const LoginPage = () => {
    const [isLogin, setIsLogin] = useState(true);
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    
    const { login, register, isAuthenticated } = useAuth();
    const navigate = useNavigate();
    const location = useLocation();
    
    const params = new URLSearchParams(location.search);
    const redirectPath = params.get('redirect');
    const from = redirectPath || location.state?.from?.pathname || '/chat';

    useEffect(() => {
        if (isAuthenticated) {
            navigate(from, { replace: true });
        }
    }, [isAuthenticated, navigate, from]);

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');

        if (!username.trim() || !password.trim()) {
            setError('请填写用户名和密码');
            return;
        }

        if (!isLogin && password !== confirmPassword) {
            setError('两次输入的密码不一致');
            return;
        }

        if (!isLogin && password.length < 6) {
            setError('密码长度至少6位');
            return;
        }

        setLoading(true);
        try {
            if (isLogin) {
                await login(username, password);
            } else {
                await register(username, password);
            }
        } catch (err) {
            setError(err.message || (isLogin ? '登录失败' : '注册失败'));
        } finally {
            setLoading(false);
        }
    };

    const toggleMode = () => {
        setIsLogin(!isLogin);
        setError('');
        setConfirmPassword('');
    };

    return (
        <div className="login-page">
            <TopNavbar />
            <div className="login-container">
                <div className="login-card">
                    <div className="login-header">
                        <div className="login-logo">
                            <Building2 size={32} />
                        </div>
                        <h1>山东省智能政策咨询助手</h1>
                        <p>以旧换新补贴政策一站式查询</p>
                    </div>

                    <form className="login-form" onSubmit={handleSubmit} autoComplete="on">
                        <h2>{isLogin ? '账号登录' : '注册账号'}</h2>

                        {error && <div className="error-message">{error}</div>}

                        <div className="input-group">
                            <User size={18} />
                            <input
                                type="text"
                                name="username"
                                id="username"
                                placeholder="用户名"
                                value={username}
                                onChange={(e) => setUsername(e.target.value)}
                                disabled={loading}
                                autoComplete="username"
                            />
                        </div>

                        <div className="input-group">
                            <Lock size={18} />
                            <input
                                type="password"
                                name="password"
                                id="password"
                                placeholder="密码"
                                value={password}
                                onChange={(e) => setPassword(e.target.value)}
                                disabled={loading}
                                autoComplete={isLogin ? 'current-password' : 'new-password'}
                            />
                        </div>

                        {!isLogin && (
                            <div className="input-group">
                                <Lock size={18} />
                                <input
                                    type="password"
                                    name="confirmPassword"
                                    id="confirmPassword"
                                    placeholder="确认密码"
                                    value={confirmPassword}
                                    onChange={(e) => setConfirmPassword(e.target.value)}
                                    disabled={loading}
                                    autoComplete="new-password"
                                />
                            </div>
                        )}

                        <button type="submit" className="login-btn" disabled={loading}>
                            {loading ? (
                                <span className="btn-loading"></span>
                            ) : (
                                <>
                                    {isLogin ? <ArrowRight size={18} /> : <UserPlus size={18} />}
                                    <span>{isLogin ? '登录' : '注册'}</span>
                                </>
                            )}
                        </button>
                    </form>

                    <div className="login-footer">
                        <span>{isLogin ? '还没有账号？' : '已有账号？'}</span>
                        <button type="button" className="toggle-btn" onClick={toggleMode}>
                            {isLogin ? '立即注册' : '立即登录'}
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default LoginPage;
