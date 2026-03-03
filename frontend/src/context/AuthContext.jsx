/* eslint-disable react-refresh/only-export-components */
import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { authApi } from '../services/api';
import { adminApi } from '../services/adminApi';

const AuthContext = createContext(null);

export const useAuth = () => {
    const context = useContext(AuthContext);
    if (!context) {
        throw new Error('useAuth must be used within an AuthProvider');
    }
    return context;
};

export const AuthProvider = ({ children }) => {
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);

    const checkAuth = useCallback(async () => {
        const token = localStorage.getItem('token');
        if (!token) {
            setLoading(false);
            return;
        }

        try {
            const userData = await authApi.getCurrentUser();
            setUser(userData);
        } catch {
            localStorage.removeItem('token');
            setUser(null);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        checkAuth();
    }, [checkAuth]);

    const login = async (username, password) => {
        const response = await authApi.login(username, password);
        localStorage.setItem('token', response.token);
        setUser({ username: response.username, role: response.role });
        return response;
    };

    const adminLogin = async (username, password) => {
        const response = await adminApi.login(username, password);
        localStorage.setItem('token', response.token);
        setUser({ username: response.username, role: response.role });
        return response;
    };

    const register = async (username, password) => {
        const response = await authApi.register(username, password);
        localStorage.setItem('token', response.token);
        setUser({ username: response.username, role: response.role });
        return response;
    };

    const logout = useCallback(() => {
        localStorage.removeItem('token');
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
