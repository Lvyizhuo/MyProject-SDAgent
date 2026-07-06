import React from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import ProtectedRoute from './components/ProtectedRoute';
import MainLayout from './components/MainLayout';
import { AuthProvider } from './context/AuthContext';
import ChatPage from './pages/ChatPage';
import UIChatPage from './components/ui/ChatPage';
import HomePage from './pages/HomePage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import PolicyMatchingPage from './pages/PolicyMatchingPage';
import PolicyQueryPage from './pages/PolicyQueryPage';
import UserCenterPage from './pages/UserCenterPage';
import AdminConsolePage from './pages/AdminConsolePage';
import './variables.css';

function App() {
    return (
        <AuthProvider>
            <BrowserRouter>
                <Routes>
                    <Route path="/" element={<Navigate to="/home" replace />} />

                    {/* 需要 TopNavbar 的页面 */}
                    <Route element={<MainLayout />}>
                        <Route path="/home" element={<HomePage />} />
                        <Route path="/policies" element={<PolicyQueryPage />} />
                        <Route path="/matching" element={<PolicyMatchingPage />} />
                        <Route
                            path="/chat"
                            element={(
                                <ProtectedRoute>
                                    <ChatPage />
                                </ProtectedRoute>
                            )}
                        />
                        <Route path="/user" element={<UserCenterPage />} />
                    </Route>

                    {/* 不需要 TopNavbar 的页面 */}
                    <Route path="/login" element={<LoginPage />} />
                    <Route path="/register" element={<RegisterPage />} />
                    <Route
                        path="/admin-console"
                        element={(
                            <ProtectedRoute>
                                <AdminConsolePage />
                            </ProtectedRoute>
                        )}
                    />

                    {/* 新 UI 预览路由（DeepSeek 风格） */}
                    <Route path="/chat-ui" element={<UIChatPage />} />

                    <Route path="*" element={<Navigate to="/home" replace />} />
                </Routes>
            </BrowserRouter>
        </AuthProvider>
    );
}

export default App;
