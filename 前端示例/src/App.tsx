import React from 'react';
import { motion } from 'motion/react';
import { Search, MessageSquare, FileText, Target, User, ChevronRight, Bell, Menu, X } from 'lucide-react';

// --- Components ---

const Navbar = ({ activeTab, setActiveTab }: { activeTab: string, setActiveTab: (t: string) => void }) => {
  const [isOpen, setIsOpen] = React.useState(false);
  const navItems = [
    { id: 'home', label: '首页', icon: <Search size={18} /> },
    { id: 'query', label: '政策查询', icon: <FileText size={18} /> },
    { id: 'match', label: '政策匹配', icon: <Target size={18} /> },
    { id: 'chat', label: '智能问答', icon: <MessageSquare size={18} /> },
  ];

  return (
    <nav className="sticky top-0 z-50 bg-white/80 backdrop-blur-md border-b border-slate-200">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between h-16 items-center">
          <div className="flex items-center gap-2 cursor-pointer" onClick={() => setActiveTab('home')}>
            <div className="w-10 h-10 bg-blue-600 rounded-xl flex items-center justify-center text-white shadow-lg shadow-blue-200">
              <Target size={24} />
            </div>
            <span className="text-xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-blue-600 to-indigo-600">
              AI政策通
            </span>
          </div>

          {/* Desktop Nav */}
          <div className="hidden md:flex items-center gap-8">
            {navItems.map((item) => (
              <button
                key={item.id}
                onClick={() => setActiveTab(item.id)}
                className={`flex items-center gap-1.5 px-3 py-2 rounded-lg transition-all ${
                  activeTab === item.id 
                    ? 'text-blue-600 bg-blue-50 font-medium' 
                    : 'text-slate-600 hover:text-blue-600 hover:bg-slate-50'
                }`}
              >
                {item.icon}
                {item.label}
              </button>
            ))}
          </div>

          <div className="flex items-center gap-4">
            <button className="p-2 text-slate-500 hover:text-blue-600 transition-colors relative">
              <Bell size={20} />
              <span className="absolute top-1.5 right-1.5 w-2 h-2 bg-red-500 rounded-full border-2 border-white"></span>
            </button>
            <button 
              onClick={() => setActiveTab('user')}
              className={`flex items-center gap-2 px-3 py-1.5 rounded-full border transition-all ${
                activeTab === 'user' ? 'border-blue-600 text-blue-600 bg-blue-50' : 'border-slate-200 text-slate-700 hover:border-blue-400'
              }`}
            >
              <User size={18} />
              <span className="hidden sm:inline text-sm font-medium">用户中心</span>
            </button>
            <button className="md:hidden p-2 text-slate-600" onClick={() => setIsOpen(!isOpen)}>
              {isOpen ? <X /> : <Menu />}
            </button>
          </div>
        </div>
      </div>

      {/* Mobile Nav */}
      {isOpen && (
        <motion.div 
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
          className="md:hidden bg-white border-b border-slate-200 px-4 py-4 space-y-2"
        >
          {navItems.map((item) => (
            <button
              key={item.id}
              onClick={() => { setActiveTab(item.id); setIsOpen(false); }}
              className={`w-full flex items-center gap-3 px-4 py-3 rounded-xl transition-all ${
                activeTab === item.id 
                  ? 'text-blue-600 bg-blue-50 font-medium' 
                  : 'text-slate-600 hover:bg-slate-50'
              }`}
            >
              {item.icon}
              {item.label}
            </button>
          ))}
        </motion.div>
      )}
    </nav>
  );
};

const Footer = () => (
  <footer className="bg-slate-900 text-slate-400 py-12">
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
      <div className="grid grid-cols-1 md:grid-cols-4 gap-8 mb-8">
        <div className="col-span-1 md:col-span-1">
          <div className="flex items-center gap-2 text-white mb-4">
            <Target size={24} className="text-blue-500" />
            <span className="text-xl font-bold">AI政策通</span>
          </div>
          <p className="text-sm leading-relaxed">
            为您提供最专业、最及时的政策咨询服务，助力企业高质量发展。
          </p>
        </div>
        <div>
          <h4 className="text-white font-semibold mb-4">快速链接</h4>
          <ul className="space-y-2 text-sm">
            <li><a href="#" className="hover:text-blue-400 transition-colors">政策查询</a></li>
            <li><a href="#" className="hover:text-blue-400 transition-colors">智能解读</a></li>
            <li><a href="#" className="hover:text-blue-400 transition-colors">匹配推荐</a></li>
          </ul>
        </div>
        <div>
          <h4 className="text-white font-semibold mb-4">帮助与支持</h4>
          <ul className="space-y-2 text-sm">
            <li><a href="#" className="hover:text-blue-400 transition-colors">常见问题</a></li>
            <li><a href="#" className="hover:text-blue-400 transition-colors">隐私政策</a></li>
            <li><a href="#" className="hover:text-blue-400 transition-colors">服务条款</a></li>
          </ul>
        </div>
        <div>
          <h4 className="text-white font-semibold mb-4">联系我们</h4>
          <ul className="space-y-2 text-sm">
            <li>邮箱：support@aipolicy.com</li>
            <li>电话：400-123-4567</li>
            <li>地址：北京市海淀区科技园</li>
          </ul>
        </div>
      </div>
      <div className="pt-8 border-t border-slate-800 text-center text-xs">
        © 2024 AI政策咨询智能体. All rights reserved.
      </div>
    </div>
  </footer>
);

// --- Pages ---

import HomePage from './pages/HomePage';
import QueryPage from './pages/QueryPage';
import MatchPage from './pages/MatchPage';
import ChatPage from './pages/ChatPage';
import UserCenterPage from './pages/UserCenterPage';

export default function App() {
  const [activeTab, setActiveTab] = React.useState('home');

  const renderPage = () => {
    switch (activeTab) {
      case 'home': return <HomePage onNavigate={setActiveTab} />;
      case 'query': return <QueryPage />;
      case 'match': return <MatchPage />;
      case 'chat': return <ChatPage />;
      case 'user': return <UserCenterPage />;
      default: return <HomePage onNavigate={setActiveTab} />;
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col font-sans selection:bg-blue-100 selection:text-blue-900">
      <Navbar activeTab={activeTab} setActiveTab={setActiveTab} />
      <main className="flex-grow">
        {renderPage()}
      </main>
      <Footer />
    </div>
  );
}
