import React from 'react';
import { useNavigate } from 'react-router-dom';
import { Building2, User, Lock, UserPlus } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import TopNavbar from '../components/TopNavbar';
import './LoginPage.css';

const RegisterPage = () => {
    const [username, setUsername] = React.useState('');
    const [password, setPassword] = React.useState('');
    const [confirmPassword, setConfirmPassword] = React.useState('');
    const [error, setError] = React.useState('');
    const [loading, setLoading] = React.useState(false);

    const { register } = useAuth();
    const navigate = useNavigate();

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');

        if (!username.trim() || !password.trim()) {
            setError('请填写用户名和密码');
            return;
        }

        if (password !== confirmPassword) {
            setError('两次输入的密码不一致');
            return;
        }

        if (password.length < 6) {
            setError('密码长度至少6位');
            return;
        }

        setLoading(true);
        try {
            await register(username, password);
            // 注册成功后跳转到登录页面
            navigate('/login', { replace: true });
        } catch (err) {
            setError(err.message || '注册失败');
        } finally {
            setLoading(false);
        }
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
                        <h2>注册账号</h2>

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
                                autoComplete="new-password"
                            />
                        </div>

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

                        <button type="submit" className="login-btn" disabled={loading}>
                            {loading ? (
                                <span className="btn-loading"></span>
                            ) : (
                                <>
                                    <UserPlus size={18} />
                                    <span>注册</span>
                                </>
                            )}
                        </button>
                    </form>

                    <div className="login-footer">
                        <span>已有账号？</span>
                        <button type="button" className="toggle-btn" onClick={() => navigate('/login')}>
                            立即登录
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default RegisterPage;
