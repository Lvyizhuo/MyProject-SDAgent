import React from 'react';
import { motion } from 'motion/react';
import { Search, MessageSquare, FileText, Target, ChevronRight, TrendingUp, Zap, ShieldCheck } from 'lucide-react';
import { MOCK_POLICIES } from '../constants';

const HomePage = ({ onNavigate }: { onNavigate: (t: string) => void }) => {
  return (
    <div className="space-y-16 pb-20">
      {/* Hero Section */}
      <section className="relative overflow-hidden bg-gradient-to-br from-blue-700 via-blue-600 to-indigo-700 py-24 sm:py-32">
        <div className="absolute inset-0 opacity-10">
          <div className="absolute top-0 left-0 w-96 h-96 bg-white rounded-full blur-3xl -translate-x-1/2 -translate-y-1/2"></div>
          <div className="absolute bottom-0 right-0 w-96 h-96 bg-blue-400 rounded-full blur-3xl translate-x-1/2 translate-y-1/2"></div>
        </div>
        
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10 text-center">
          <motion.h1 
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            className="text-4xl sm:text-6xl font-extrabold text-white tracking-tight mb-6"
          >
            智能政策咨询，<span className="text-blue-200">触手可及</span>
          </motion.h1>
          <motion.p 
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.1 }}
            className="text-lg sm:text-xl text-blue-100 max-w-2xl mx-auto mb-10"
          >
            基于大模型技术的专业政策服务平台，为您提供政策查询、智能解读、精准匹配及AI问答全方位支持。
          </motion.p>

          <motion.div 
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.2 }}
            className="max-w-3xl mx-auto bg-white p-2 rounded-2xl shadow-2xl flex items-center gap-2"
          >
            <div className="flex-grow flex items-center px-4">
              <Search className="text-slate-400 mr-2" size={20} />
              <input 
                type="text" 
                placeholder="输入政策关键词或您的问题，例如：人工智能补贴政策..."
                className="w-full py-3 text-slate-700 focus:outline-none text-lg"
              />
            </div>
            <button 
              onClick={() => onNavigate('chat')}
              className="bg-blue-600 hover:bg-blue-700 text-white px-8 py-3 rounded-xl font-semibold transition-all flex items-center gap-2 shadow-lg shadow-blue-200"
            >
              智能咨询
            </button>
          </motion.div>

          <div className="mt-8 flex flex-wrap justify-center gap-3">
            <span className="text-blue-200 text-sm">热门搜索：</span>
            {['专精特新', '高新企业认定', '研发费用加计扣除', '人才补贴'].map(tag => (
              <button key={tag} className="text-xs bg-white/10 hover:bg-white/20 text-white px-3 py-1 rounded-full border border-white/20 transition-colors">
                {tag}
              </button>
            ))}
          </div>
        </div>
      </section>

      {/* Feature Modules */}
      <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
          {[
            { id: 'query', title: '政策查询', desc: '海量政策库，多维筛选', icon: <FileText className="text-blue-600" />, color: 'bg-blue-50' },
            { id: 'chat', title: '智能解读', desc: 'AI深度分析，直击重点', icon: <Zap className="text-amber-600" />, color: 'bg-amber-50' },
            { id: 'match', title: '精准匹配', desc: '企业画像，一键匹配', icon: <Target className="text-emerald-600" />, color: 'bg-emerald-50' },
            { id: 'chat', title: '智能问答', desc: '7x24小时，即问即答', icon: <MessageSquare className="text-indigo-600" />, color: 'bg-indigo-50' },
          ].map((item, idx) => (
            <motion.div
              key={item.id}
              whileHover={{ y: -5 }}
              onClick={() => onNavigate(item.id)}
              className="bg-white p-6 rounded-2xl border border-slate-100 shadow-sm hover:shadow-md transition-all cursor-pointer group"
            >
              <div className={`w-12 h-12 ${item.color} rounded-xl flex items-center justify-center mb-4 group-hover:scale-110 transition-transform`}>
                {item.icon}
              </div>
              <h3 className="text-lg font-bold text-slate-900 mb-2">{item.title}</h3>
              <p className="text-slate-500 text-sm mb-4">{item.desc}</p>
              <div className="flex items-center text-blue-600 text-xs font-semibold">
                立即体验 <ChevronRight size={14} />
              </div>
            </motion.div>
          ))}
        </div>
      </section>

      {/* Hot Policies */}
      <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between items-end mb-8">
          <div>
            <h2 className="text-2xl font-bold text-slate-900 flex items-center gap-2">
              <TrendingUp className="text-red-500" /> 热门政策推荐
            </h2>
            <p className="text-slate-500 mt-1">为您精选近期关注度最高的政策文件</p>
          </div>
          <button 
            onClick={() => onNavigate('query')}
            className="text-blue-600 hover:text-blue-700 font-medium flex items-center gap-1"
          >
            查看更多 <ChevronRight size={18} />
          </button>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {MOCK_POLICIES.slice(0, 4).map(policy => (
            <div key={policy.id} className="bg-white p-6 rounded-2xl border border-slate-100 flex gap-4 hover:border-blue-200 transition-colors cursor-pointer group">
              <div className="flex-grow">
                <div className="flex items-center gap-2 mb-2">
                  <span className="px-2 py-0.5 bg-blue-50 text-blue-600 text-[10px] font-bold rounded uppercase tracking-wider">
                    {policy.type}
                  </span>
                  <span className="text-slate-400 text-xs">{policy.date}</span>
                </div>
                <h4 className="text-lg font-bold text-slate-900 group-hover:text-blue-600 transition-colors line-clamp-1">
                  {policy.title}
                </h4>
                <p className="text-slate-500 text-sm mt-2 line-clamp-2">
                  {policy.summary}
                </p>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* Trust Section */}
      <section className="bg-slate-100 py-16">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
          <h2 className="text-2xl font-bold text-slate-900 mb-12">为什么选择 AI政策通</h2>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-12">
            {[
              { title: '权威数据', desc: '直连政府官网，确保政策信息真实可靠', icon: <ShieldCheck className="mx-auto text-blue-600" size={40} /> },
              { title: '智能匹配', desc: '深度学习算法，实现人策精准对接', icon: <Target className="mx-auto text-blue-600" size={40} /> },
              { title: '即时响应', desc: 'AI全天候在线，秒级回复政策疑问', icon: <Zap className="mx-auto text-blue-600" size={40} /> },
            ].map(item => (
              <div key={item.title}>
                <div className="mb-4">{item.icon}</div>
                <h4 className="text-lg font-bold text-slate-900 mb-2">{item.title}</h4>
                <p className="text-slate-500 text-sm">{item.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>
    </div>
  );
};

export default HomePage;
