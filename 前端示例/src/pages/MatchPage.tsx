import React from 'react';
import { motion } from 'motion/react';
import { Target, Building2, Users, MapPin, Briefcase, ChevronRight, CheckCircle2, AlertCircle, Loader2 } from 'lucide-react';
import { MOCK_POLICIES, INDUSTRIES, REGIONS } from '../constants';
import { matchPolicies } from '../services/gemini';
import { MatchResult } from '../types';

const MatchPage = () => {
  const [step, setStep] = React.useState(1);
  const [loading, setLoading] = React.useState(false);
  const [results, setResults] = React.useState<MatchResult[]>([]);
  const [formData, setFormData] = React.useState({
    companyName: '',
    industry: '人工智能',
    region: '北京',
    scale: '小型企业',
    employees: '50-100人',
    revenue: '1000万-5000万',
  });

  const handleMatch = async () => {
    setLoading(true);
    try {
      const matched = await matchPolicies(formData, MOCK_POLICIES);
      setResults(matched);
      setStep(2);
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-5xl mx-auto px-4 py-12">
      <div className="text-center mb-12">
        <h1 className="text-3xl font-bold text-slate-900 mb-4">政策精准匹配</h1>
        <p className="text-slate-500">填写企业基本信息，AI为您实时匹配最适合的扶持政策</p>
      </div>

      {step === 1 ? (
        <motion.div 
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="bg-white rounded-3xl border border-slate-200 shadow-xl overflow-hidden"
        >
          <div className="p-8 sm:p-12">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
              <div className="space-y-2">
                <label className="text-sm font-bold text-slate-700 flex items-center gap-2">
                  <Building2 size={16} className="text-blue-600" /> 企业名称
                </label>
                <input 
                  type="text" 
                  placeholder="请输入您的企业全称"
                  className="w-full p-3 bg-slate-50 border border-slate-200 rounded-xl focus:ring-2 focus:ring-blue-500 focus:outline-none transition-all"
                  value={formData.companyName}
                  onChange={(e) => setFormData({...formData, companyName: e.target.value})}
                />
              </div>

              <div className="space-y-2">
                <label className="text-sm font-bold text-slate-700 flex items-center gap-2">
                  <Briefcase size={16} className="text-blue-600" /> 所属行业
                </label>
                <select 
                  className="w-full p-3 bg-slate-50 border border-slate-200 rounded-xl focus:ring-2 focus:ring-blue-500 focus:outline-none"
                  value={formData.industry}
                  onChange={(e) => setFormData({...formData, industry: e.target.value})}
                >
                  {INDUSTRIES.filter(i => i !== '全部').map(i => <option key={i} value={i}>{i}</option>)}
                </select>
              </div>

              <div className="space-y-2">
                <label className="text-sm font-bold text-slate-700 flex items-center gap-2">
                  <MapPin size={16} className="text-blue-600" /> 注册地区
                </label>
                <select 
                  className="w-full p-3 bg-slate-50 border border-slate-200 rounded-xl focus:ring-2 focus:ring-blue-500 focus:outline-none"
                  value={formData.region}
                  onChange={(e) => setFormData({...formData, region: e.target.value})}
                >
                  {REGIONS.filter(r => r !== '全国').map(r => <option key={r} value={r}>{r}</option>)}
                </select>
              </div>

              <div className="space-y-2">
                <label className="text-sm font-bold text-slate-700 flex items-center gap-2">
                  <Users size={16} className="text-blue-600" /> 企业规模
                </label>
                <select 
                  className="w-full p-3 bg-slate-50 border border-slate-200 rounded-xl focus:ring-2 focus:ring-blue-500 focus:outline-none"
                  value={formData.scale}
                  onChange={(e) => setFormData({...formData, scale: e.target.value})}
                >
                  <option>微型企业</option>
                  <option>小型企业</option>
                  <option>中型企业</option>
                  <option>大型企业</option>
                </select>
              </div>
            </div>

            <div className="mt-12 flex justify-center">
              <button 
                onClick={handleMatch}
                disabled={loading || !formData.companyName}
                className="bg-blue-600 hover:bg-blue-700 text-white px-12 py-4 rounded-2xl font-bold text-lg shadow-xl shadow-blue-200 transition-all flex items-center gap-3 disabled:opacity-50 disabled:shadow-none"
              >
                {loading ? <Loader2 className="animate-spin" /> : <Target />}
                {loading ? 'AI正在深度匹配中...' : '开始智能匹配'}
              </button>
            </div>
          </div>
          
          <div className="bg-slate-50 px-8 py-4 border-t border-slate-100 flex items-center gap-2 text-xs text-slate-500">
            <AlertCircle size={14} /> 您的信息仅用于政策匹配，我们将严格保护您的隐私安全。
          </div>
        </motion.div>
      ) : (
        <motion.div 
          initial={{ opacity: 0, x: 20 }}
          animate={{ opacity: 1, x: 0 }}
          className="space-y-6"
        >
          <div className="flex justify-between items-center bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
            <div>
              <h2 className="text-xl font-bold text-slate-900">匹配结果</h2>
              <p className="text-sm text-slate-500 mt-1">为您找到 {results.length} 项高度契合的政策</p>
            </div>
            <button 
              onClick={() => setStep(1)}
              className="text-blue-600 font-semibold hover:underline"
            >
              重新匹配
            </button>
          </div>

          <div className="grid grid-cols-1 gap-6">
            {results.map((result, idx) => {
              const policy = MOCK_POLICIES.find(p => p.id === result.policyId);
              if (!policy) return null;
              return (
                <motion.div 
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: idx * 0.1 }}
                  key={result.policyId}
                  className="bg-white rounded-3xl border border-slate-200 overflow-hidden flex flex-col md:flex-row shadow-sm hover:shadow-md transition-all"
                >
                  <div className="p-8 flex-grow">
                    <div className="flex items-center gap-2 mb-4">
                      <span className="px-2 py-0.5 bg-blue-50 text-blue-600 text-[10px] font-bold rounded uppercase">
                        {policy.type}
                      </span>
                      <span className="text-slate-400 text-xs">{policy.agency}</span>
                    </div>
                    <h3 className="text-xl font-bold text-slate-900 mb-3">{policy.title}</h3>
                    <p className="text-slate-500 text-sm mb-6">{policy.summary}</p>
                    
                    <div className="bg-emerald-50 border border-emerald-100 p-4 rounded-2xl">
                      <h4 className="text-emerald-900 font-bold text-sm mb-2 flex items-center gap-2">
                        <CheckCircle2 size={16} /> 适配建议
                      </h4>
                      <p className="text-emerald-800 text-sm leading-relaxed">
                        {result.suggestion}
                      </p>
                    </div>
                  </div>
                  
                  <div className="bg-slate-50 p-8 md:w-48 flex flex-col items-center justify-center border-t md:border-t-0 md:border-l border-slate-100">
                    <div className="text-center">
                      <div className="text-3xl font-black text-blue-600">{result.score}%</div>
                      <div className="text-xs text-slate-400 mt-1 font-bold uppercase tracking-wider">匹配度</div>
                    </div>
                    <button className="mt-6 w-full py-2 bg-white border border-slate-200 text-slate-700 rounded-xl text-xs font-bold hover:bg-slate-100 transition-all flex items-center justify-center gap-1">
                      详情 <ChevronRight size={14} />
                    </button>
                  </div>
                </motion.div>
              );
            })}
          </div>
        </motion.div>
      )}
    </div>
  );
};

export default MatchPage;
