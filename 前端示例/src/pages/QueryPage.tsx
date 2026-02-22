import React from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { Search, Filter, Calendar, MapPin, Building2, ChevronRight, X, FileText, Share2, Star, Zap } from 'lucide-react';
import { MOCK_POLICIES, INDUSTRIES, REGIONS, POLICY_TYPES } from '../constants';
import { Policy } from '../types';
import { interpretPolicy } from '../services/gemini';

const QueryPage = () => {
  const [searchTerm, setSearchTerm] = React.useState('');
  const [selectedIndustry, setSelectedIndustry] = React.useState('全部');
  const [selectedRegion, setSelectedRegion] = React.useState('全国');
  const [selectedType, setSelectedType] = React.useState('全部');
  const [selectedPolicy, setSelectedPolicy] = React.useState<Policy | null>(null);
  const [interpretation, setInterpretation] = React.useState<string | null>(null);
  const [isInterpreting, setIsInterpreting] = React.useState(false);

  const filteredPolicies = MOCK_POLICIES.filter(p => {
    const matchesSearch = p.title.includes(searchTerm) || p.summary.includes(searchTerm);
    const matchesIndustry = selectedIndustry === '全部' || p.industry === selectedIndustry;
    const matchesRegion = selectedRegion === '全国' || p.region === selectedRegion;
    const matchesType = selectedType === '全部' || p.type === selectedType;
    return matchesSearch && matchesIndustry && matchesRegion && matchesType;
  });

  const handleInterpret = async (policy: Policy) => {
    setIsInterpreting(true);
    try {
      const result = await interpretPolicy(policy);
      setInterpretation(result);
    } catch (error) {
      console.error(error);
    } finally {
      setIsInterpreting(false);
    }
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
      <div className="flex flex-col md:flex-row gap-8">
        {/* Sidebar Filters */}
        <aside className="w-full md:w-64 space-y-8">
          <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
            <h3 className="font-bold text-slate-900 mb-4 flex items-center gap-2">
              <Filter size={18} className="text-blue-600" /> 政策筛选
            </h3>
            
            <div className="space-y-6">
              <div>
                <label className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-2 block">行业领域</label>
                <div className="flex flex-wrap gap-2">
                  {INDUSTRIES.map(item => (
                    <button
                      key={item}
                      onClick={() => setSelectedIndustry(item)}
                      className={`px-3 py-1.5 rounded-lg text-sm transition-all ${
                        selectedIndustry === item ? 'bg-blue-600 text-white shadow-md shadow-blue-100' : 'bg-slate-50 text-slate-600 hover:bg-slate-100'
                      }`}
                    >
                      {item}
                    </button>
                  ))}
                </div>
              </div>

              <div>
                <label className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-2 block">地区范围</label>
                <select 
                  value={selectedRegion}
                  onChange={(e) => setSelectedRegion(e.target.value)}
                  className="w-full p-2 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none"
                >
                  {REGIONS.map(item => <option key={item} value={item}>{item}</option>)}
                </select>
              </div>

              <div>
                <label className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-2 block">政策类型</label>
                <div className="space-y-2">
                  {POLICY_TYPES.map(item => (
                    <label key={item} className="flex items-center gap-2 cursor-pointer group">
                      <input 
                        type="radio" 
                        name="type" 
                        checked={selectedType === item}
                        onChange={() => setSelectedType(item)}
                        className="w-4 h-4 text-blue-600 focus:ring-blue-500" 
                      />
                      <span className={`text-sm ${selectedType === item ? 'text-blue-600 font-medium' : 'text-slate-600 group-hover:text-slate-900'}`}>{item}</span>
                    </label>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </aside>

        {/* Main Content */}
        <div className="flex-grow space-y-6">
          <div className="bg-white p-4 rounded-2xl border border-slate-200 shadow-sm flex items-center gap-3">
            <Search className="text-slate-400" size={20} />
            <input 
              type="text" 
              placeholder="搜索政策标题、文号或关键词..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="flex-grow bg-transparent focus:outline-none text-slate-700"
            />
          </div>

          <div className="space-y-4">
            {filteredPolicies.length > 0 ? (
              filteredPolicies.map(policy => (
                <motion.div
                  layout
                  key={policy.id}
                  onClick={() => setSelectedPolicy(policy)}
                  className="bg-white p-6 rounded-2xl border border-slate-200 hover:border-blue-300 hover:shadow-md transition-all cursor-pointer group"
                >
                  <div className="flex justify-between items-start mb-3">
                    <div className="flex items-center gap-2">
                      <span className="px-2 py-0.5 bg-blue-50 text-blue-600 text-[10px] font-bold rounded">
                        {policy.type}
                      </span>
                      <span className="px-2 py-0.5 bg-slate-50 text-slate-500 text-[10px] font-bold rounded">
                        {policy.region}
                      </span>
                    </div>
                    <span className="text-slate-400 text-xs flex items-center gap-1">
                      <Calendar size={12} /> {policy.date}
                    </span>
                  </div>
                  <h3 className="text-lg font-bold text-slate-900 group-hover:text-blue-600 transition-colors mb-2">
                    {policy.title}
                  </h3>
                  <p className="text-slate-500 text-sm line-clamp-2 mb-4">
                    {policy.summary}
                  </p>
                  <div className="flex items-center justify-between pt-4 border-t border-slate-50">
                    <div className="flex items-center gap-4 text-xs text-slate-400">
                      <span className="flex items-center gap-1"><Building2 size={14} /> {policy.agency}</span>
                    </div>
                    <div className="flex items-center text-blue-600 text-sm font-semibold">
                      查看详情 <ChevronRight size={16} />
                    </div>
                  </div>
                </motion.div>
              ))
            ) : (
              <div className="text-center py-20 bg-white rounded-2xl border border-dashed border-slate-300">
                <FileText className="mx-auto text-slate-300 mb-4" size={48} />
                <p className="text-slate-500">暂无符合条件的政策</p>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Policy Detail Modal */}
      <AnimatePresence>
        {selectedPolicy && (
          <div className="fixed inset-0 z-[60] flex items-center justify-center p-4 sm:p-6">
            <motion.div 
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={() => { setSelectedPolicy(null); setInterpretation(null); }}
              className="absolute inset-0 bg-slate-900/60 backdrop-blur-sm"
            />
            <motion.div 
              initial={{ opacity: 0, scale: 0.95, y: 20 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: 20 }}
              className="relative w-full max-w-4xl bg-white rounded-3xl shadow-2xl overflow-hidden flex flex-col max-h-[90vh]"
            >
              <div className="p-6 border-b border-slate-100 flex justify-between items-center bg-slate-50/50">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 bg-blue-100 rounded-xl flex items-center justify-center text-blue-600">
                    <FileText size={20} />
                  </div>
                  <div>
                    <h2 className="font-bold text-slate-900 line-clamp-1">{selectedPolicy.title}</h2>
                    <p className="text-xs text-slate-500">{selectedPolicy.agency} · {selectedPolicy.date}</p>
                  </div>
                </div>
                <button 
                  onClick={() => { setSelectedPolicy(null); setInterpretation(null); }}
                  className="p-2 hover:bg-slate-200 rounded-full transition-colors"
                >
                  <X size={20} className="text-slate-500" />
                </button>
              </div>

              <div className="flex-grow overflow-y-auto p-6 sm:p-8 space-y-8">
                {/* Actions */}
                <div className="flex flex-wrap gap-3">
                  <button 
                    onClick={() => handleInterpret(selectedPolicy)}
                    disabled={isInterpreting}
                    className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-xl text-sm font-semibold hover:bg-blue-700 transition-all disabled:opacity-50"
                  >
                    <Zap size={16} /> {isInterpreting ? 'AI正在解读...' : 'AI智能解读'}
                  </button>
                  <button className="flex items-center gap-2 px-4 py-2 bg-slate-100 text-slate-700 rounded-xl text-sm font-semibold hover:bg-slate-200 transition-all">
                    <Star size={16} /> 收藏政策
                  </button>
                  <button className="flex items-center gap-2 px-4 py-2 bg-slate-100 text-slate-700 rounded-xl text-sm font-semibold hover:bg-slate-200 transition-all">
                    <Share2 size={16} /> 分享
                  </button>
                </div>

                {/* Interpretation Result */}
                {interpretation && (
                  <motion.div 
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    className="bg-blue-50 border border-blue-100 p-6 rounded-2xl"
                  >
                    <h3 className="text-blue-900 font-bold mb-3 flex items-center gap-2">
                      <Zap size={18} /> AI解读报告
                    </h3>
                    <div className="text-blue-800 text-sm leading-relaxed whitespace-pre-wrap">
                      {interpretation}
                    </div>
                  </motion.div>
                )}

                <div className="space-y-4">
                  <h3 className="font-bold text-slate-900 text-lg border-l-4 border-blue-600 pl-3">政策原文</h3>
                  <div className="text-slate-700 text-sm leading-relaxed whitespace-pre-wrap bg-slate-50 p-6 rounded-2xl border border-slate-100">
                    {selectedPolicy.content}
                  </div>
                </div>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </div>
  );
};

export default QueryPage;
