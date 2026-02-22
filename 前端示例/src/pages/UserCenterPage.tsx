import React from 'react';
import { motion } from 'motion/react';
import { User, Settings, Shield, Bell, History, Star, LogOut, ChevronRight, Building2, Mail, Phone } from 'lucide-react';

const UserCenterPage = () => {
  const [activeMenu, setActiveMenu] = React.useState('profile');

  const menuItems = [
    { id: 'profile', label: '个人资料', icon: <User size={18} /> },
    { id: 'history', label: '咨询记录', icon: <History size={18} /> },
    { id: 'favorites', label: '我的收藏', icon: <Star size={18} /> },
    { id: 'security', label: '账号安全', icon: <Shield size={18} /> },
    { id: 'notifications', label: '消息通知', icon: <Bell size={18} /> },
    { id: 'settings', label: '通用设置', icon: <Settings size={18} /> },
  ];

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
      <div className="flex flex-col lg:flex-row gap-8">
        {/* Sidebar Menu */}
        <aside className="w-full lg:w-64 space-y-2">
          <div className="bg-white p-6 rounded-3xl border border-slate-200 shadow-sm mb-6 flex flex-col items-center text-center">
            <div className="w-20 h-20 bg-blue-100 rounded-full flex items-center justify-center text-blue-600 mb-4 border-4 border-white shadow-inner">
              <User size={40} />
            </div>
            <h3 className="font-bold text-slate-900">张经理</h3>
            <p className="text-xs text-slate-500 mt-1">某科技发展有限公司 · CEO</p>
            <div className="mt-4 px-3 py-1 bg-blue-50 text-blue-600 text-[10px] font-bold rounded-full border border-blue-100">
              企业认证用户
            </div>
          </div>

          <div className="bg-white p-2 rounded-3xl border border-slate-200 shadow-sm">
            {menuItems.map(item => (
              <button
                key={item.id}
                onClick={() => setActiveMenu(item.id)}
                className={`w-full flex items-center gap-3 px-4 py-3 rounded-2xl text-sm transition-all ${
                  activeMenu === item.id 
                    ? 'bg-blue-600 text-white shadow-lg shadow-blue-200 font-medium' 
                    : 'text-slate-600 hover:bg-slate-50'
                }`}
              >
                {item.icon}
                {item.label}
              </button>
            ))}
            <div className="my-2 border-t border-slate-100"></div>
            <button className="w-full flex items-center gap-3 px-4 py-3 rounded-2xl text-sm text-red-500 hover:bg-red-50 transition-all">
              <LogOut size={18} />
              退出登录
            </button>
          </div>
        </aside>

        {/* Main Content Area */}
        <div className="flex-grow">
          <motion.div 
            key={activeMenu}
            initial={{ opacity: 0, x: 10 }}
            animate={{ opacity: 1, x: 0 }}
            className="bg-white rounded-3xl border border-slate-200 shadow-sm overflow-hidden"
          >
            {activeMenu === 'profile' && (
              <div className="p-8 space-y-10">
                <div>
                  <h2 className="text-xl font-bold text-slate-900 mb-6 flex items-center gap-2">
                    <User size={20} className="text-blue-600" /> 基本信息
                  </h2>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                    <div className="space-y-2">
                      <label className="text-xs font-bold text-slate-400 uppercase tracking-wider">姓名</label>
                      <input type="text" defaultValue="张经理" className="w-full p-3 bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-500" />
                    </div>
                    <div className="space-y-2">
                      <label className="text-xs font-bold text-slate-400 uppercase tracking-wider">联系电话</label>
                      <div className="flex gap-2">
                        <div className="flex-grow flex items-center gap-2 p-3 bg-slate-50 border border-slate-200 rounded-xl">
                          <Phone size={16} className="text-slate-400" />
                          <span className="text-slate-700">138****8888</span>
                        </div>
                        <button className="px-4 py-2 text-blue-600 font-medium text-sm hover:bg-blue-50 rounded-xl transition-all">修改</button>
                      </div>
                    </div>
                    <div className="space-y-2">
                      <label className="text-xs font-bold text-slate-400 uppercase tracking-wider">电子邮箱</label>
                      <div className="flex gap-2">
                        <div className="flex-grow flex items-center gap-2 p-3 bg-slate-50 border border-slate-200 rounded-xl">
                          <Mail size={16} className="text-slate-400" />
                          <span className="text-slate-700">zhang@company.com</span>
                        </div>
                        <button className="px-4 py-2 text-blue-600 font-medium text-sm hover:bg-blue-50 rounded-xl transition-all">绑定</button>
                      </div>
                    </div>
                  </div>
                </div>

                <div>
                  <h2 className="text-xl font-bold text-slate-900 mb-6 flex items-center gap-2">
                    <Building2 size={20} className="text-blue-600" /> 企业信息
                  </h2>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                    <div className="space-y-2">
                      <label className="text-xs font-bold text-slate-400 uppercase tracking-wider">企业全称</label>
                      <input type="text" defaultValue="某某科技发展有限公司" className="w-full p-3 bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-500" />
                    </div>
                    <div className="space-y-2">
                      <label className="text-xs font-bold text-slate-400 uppercase tracking-wider">统一社会信用代码</label>
                      <input type="text" defaultValue="91110108MA00****" className="w-full p-3 bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-500" />
                    </div>
                  </div>
                </div>

                <div className="pt-6 border-t border-slate-100 flex justify-end">
                  <button className="bg-blue-600 hover:bg-blue-700 text-white px-8 py-3 rounded-xl font-bold transition-all shadow-lg shadow-blue-200">
                    保存修改
                  </button>
                </div>
              </div>
            )}

            {activeMenu === 'history' && (
              <div className="p-8">
                <h2 className="text-xl font-bold text-slate-900 mb-6">咨询历史</h2>
                <div className="space-y-4">
                  {[
                    { title: '关于人工智能研发补贴的咨询', date: '2024-02-20', type: '智能问答' },
                    { title: '高新企业认定政策匹配', date: '2024-02-15', type: '政策匹配' },
                    { title: '北京市通用人工智能产业创新伙伴计划解读', date: '2024-02-10', type: '政策解读' },
                  ].map((item, idx) => (
                    <div key={idx} className="flex items-center justify-between p-4 rounded-2xl border border-slate-100 hover:bg-slate-50 transition-all cursor-pointer group">
                      <div className="flex items-center gap-4">
                        <div className="w-10 h-10 bg-slate-100 rounded-xl flex items-center justify-center text-slate-400 group-hover:bg-blue-100 group-hover:text-blue-600 transition-all">
                          <History size={20} />
                        </div>
                        <div>
                          <h4 className="font-bold text-slate-900 text-sm">{item.title}</h4>
                          <p className="text-xs text-slate-500 mt-1">{item.date} · {item.type}</p>
                        </div>
                      </div>
                      <ChevronRight size={18} className="text-slate-300 group-hover:text-blue-600 transition-all" />
                    </div>
                  ))}
                </div>
              </div>
            )}

            {activeMenu === 'favorites' && (
              <div className="p-8">
                <h2 className="text-xl font-bold text-slate-900 mb-6">我的收藏</h2>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {[
                    { title: '关于加快推动人工智能产业高质量发展的若干措施', agency: '工信部' },
                    { title: '2024年中小企业数字化转型专项资金管理办法', agency: '财政部' },
                  ].map((item, idx) => (
                    <div key={idx} className="p-4 rounded-2xl border border-slate-100 hover:border-blue-200 transition-all cursor-pointer group">
                      <div className="flex justify-between items-start mb-2">
                        <span className="text-[10px] font-bold text-blue-600 bg-blue-50 px-2 py-0.5 rounded uppercase">{item.agency}</span>
                        <Star size={14} className="text-amber-400 fill-amber-400" />
                      </div>
                      <h4 className="font-bold text-slate-900 text-sm group-hover:text-blue-600 transition-colors line-clamp-2">{item.title}</h4>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {['security', 'notifications', 'settings'].includes(activeMenu) && (
              <div className="p-20 text-center">
                <Settings className="mx-auto text-slate-200 mb-4" size={64} />
                <h3 className="text-lg font-bold text-slate-900">功能开发中</h3>
                <p className="text-slate-500 text-sm mt-2">更多精彩功能敬请期待</p>
              </div>
            )}
          </motion.div>
        </div>
      </div>
    </div>
  );
};

export default UserCenterPage;
