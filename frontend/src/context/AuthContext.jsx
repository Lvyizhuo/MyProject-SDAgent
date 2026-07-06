/* eslint-disable react-refresh/only-export-components */
import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { authApi } from '../services/api';
import { adminApi } from '../services/adminApi';

const CACHED_USER_KEY = 'cached_user';

const AuthContext = createContext(null);

export const useAuth = () => {
    const context = useContext(AuthContext);
    if (!context) {
        throw new Error('useAuth must be used within an AuthProvider');
    }
    return context;
};

const restoreCachedUser = () => {
    try {
        const cached = localStorage.getItem(CACHED_USER_KEY);
        return cached ? JSON.parse(cached) : null;
    } catch {
        return null;
    }
};

export const AuthProvider = ({ children }) => {
    // 从 localStorage 中恢复缓存用户信息，避免白屏等待
    const [user, setUser] = useState(() => {
        const token = localStorage.getItem('token');
        return token ? restoreCachedUser() : null;
    });
    const [loading, setLoading] = useState(() => !localStorage.getItem('token'));

    const checkAuth = useCallback(async () => {
        const token = localStorage.getItem('token');
        if (!token) {
            setLoading(false);
            return;
        }

        try {
            const userData = await authApi.getCurrentUser();
            setUser(userData);
            // 更新缓存
            localStorage.setItem(CACHED_USER_KEY, JSON.stringify(userData));
        } catch (err) {
            // 仅当服务端明确拒绝时（401）才清除登录状态
            // 网络错误（后端重启等）保留本地缓存，用户依然可用
            if (err.status === 401) {
                localStorage.removeItem('token');
                localStorage.removeItem(CACHED_USER_KEY);
                setUser(null);
            }
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        checkAuth();
    }, [checkAuth]);

    const persistUser = (token, userInfo) => {
        localStorage.setItem('token', token);
        localStorage.setItem(CACHED_USER_KEY, JSON.stringify(userInfo));
        setUser(userInfo);
    };

    const login = async (username, password) => {
        const response = await authApi.login(username, password);
        persistUser(response.token, { username: response.username, role: response.role });
        return response;
    };

    const adminLogin = async (username, password) => {
        const response = await adminApi.login(username, password);
        persistUser(response.token, { username: response.username, role: response.role });
        return response;
    };

    const register = async (username, password) => {
        const response = await authApi.register(username, password);
        persistUser(response.token, { username: response.username, role: response.role });
        return response;
    };

    const logout = useCallback(() => {
        localStorage.removeItem('token');
        localStorage.removeItem(CACHED_USER_KEY);
        setUser(null);
    }, []);

    const isAdmin = user?.role === 'ADMIN';

    const value = {
        user,
        loading,
        login,
        adminLogin,
        register,
        logout,
        isAuthenticated: !!user,
        isAdmin
    };

    return (
        <AuthContext.Provider value={value}>
            {children}
        </AuthContext.Provider>
    );
};

export default AuthContext;
