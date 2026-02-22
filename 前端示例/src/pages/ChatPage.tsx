import React from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { MessageSquare, Send, User, Bot, Trash2, Clock, ChevronRight, HelpCircle, Loader2 } from 'lucide-react';
import { ChatMessage } from '../types';
import { chatWithAI } from '../services/gemini';

const ChatPage = () => {
  const [messages, setMessages] = React.useState<ChatMessage[]>([
    { role: 'model', text: '您好！我是您的AI政策咨询专家。您可以向我询问任何关于政策查询、解读或匹配的问题。', timestamp: Date.now() }
  ]);
  const [input, setInput] = React.useState('');
  const [isTyping, setIsTyping] = React.useState(false);
  const scrollRef = React.useRef<HTMLDivElement>(null);

  React.useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages, isTyping]);

  const handleSend = async () => {
    if (!input.trim() || isTyping) return;

    const userMsg: ChatMessage = { role: 'user', text: input, timestamp: Date.now() };
    setMessages(prev => [...prev, userMsg]);
    setInput('');
    setIsTyping(true);

    try {
      const response = await chatWithAI([...messages, userMsg]);
      setMessages(prev => [...prev, { role: 'model', text: response || '抱歉，我暂时无法回答这个问题。', timestamp: Date.now() }]);
    } catch (error) {
      console.error(error);
      setMessages(prev => [...prev, { role: 'model', text: '发生了一些错误，请稍后再试。', timestamp: Date.now() }]);
    } finally {
      setIsTyping(false);
    }
  };

  const clearHistory = () => {
    setMessages([{ role: 'model', text: '对话已清空。请问还有什么我可以帮您的？', timestamp: Date.now() }]);
  };

  return (
    <div className="max-w-6xl mx-auto px-4 py-8 h-[calc(100vh-120px)] flex gap-6">
      {/* History Sidebar */}
      <aside className="hidden lg:flex flex-col w-72 bg-white rounded-3xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="p-6 border-b border-slate-100 flex justify-between items-center">
          <h3 className="font-bold text-slate-900 flex items-center gap-2">
            <Clock size={18} className="text-blue-600" /> 咨询历史
          </h3>
          <button 
            onClick={clearHistory}
            className="p-1.5 text-slate-400 hover:text-red-500 hover:bg-red-50 rounded-lg transition-all"
            title="清空历史"
          >
            <Trash2 size={16} />
          </button>
        </div>
        <div className="flex-grow overflow-y-auto p-4 space-y-2">
          {['关于高新企业认定的咨询', '2024年AI补贴政策', '中小企业数字化转型'].map((item, idx) => (
            <button key={idx} className="w-full text-left p-3 rounded-xl text-sm text-slate-600 hover:bg-slate-50 hover:text-blue-600 transition-all flex items-center justify-between group">
              <span className="truncate">{item}</span>
              <ChevronRight size={14} className="opacity-0 group-hover:opacity-100 transition-opacity" />
            </button>
          ))}
        </div>
        <div className="p-6 bg-slate-50 border-t border-slate-100">
          <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-4">常见问题</h4>
          <div className="space-y-2">
            {['如何申请研发补贴？', '专精特新认定标准', '北京AI产业政策'].map((q, idx) => (
              <button 
                key={idx}
                onClick={() => setInput(q)}
                className="w-full text-left text-xs text-blue-600 hover:underline flex items-start gap-1.5"
              >
                <HelpCircle size={12} className="mt-0.5 flex-shrink-0" />
                {q}
              </button>
            ))}
          </div>
        </div>
      </aside>

      {/* Chat Area */}
      <div className="flex-grow flex flex-col bg-white rounded-3xl border border-slate-200 shadow-xl overflow-hidden relative">
        <div className="p-6 border-b border-slate-100 flex items-center gap-3 bg-white/80 backdrop-blur-md sticky top-0 z-10">
          <div className="w-10 h-10 bg-blue-600 rounded-xl flex items-center justify-center text-white shadow-lg shadow-blue-200">
            <Bot size={24} />
          </div>
          <div>
            <h2 className="font-bold text-slate-900">AI政策助手</h2>
            <p className="text-[10px] text-emerald-500 font-bold flex items-center gap-1">
              <span className="w-1.5 h-1.5 bg-emerald-500 rounded-full animate-pulse"></span> 在线服务中
            </p>
          </div>
        </div>

        <div 
          ref={scrollRef}
          className="flex-grow overflow-y-auto p-6 space-y-6 scroll-smooth"
        >
          {messages.map((msg, idx) => (
            <motion.div
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              key={idx}
              className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}
            >
              <div className={`flex gap-3 max-w-[85%] ${msg.role === 'user' ? 'flex-row-reverse' : ''}`}>
                <div className={`w-8 h-8 rounded-lg flex-shrink-0 flex items-center justify-center ${
                  msg.role === 'user' ? 'bg-slate-100 text-slate-600' : 'bg-blue-100 text-blue-600'
                }`}>
                  {msg.role === 'user' ? <User size={18} /> : <Bot size={18} />}
                </div>
                <div className={`p-4 rounded-2xl text-sm leading-relaxed ${
                  msg.role === 'user' 
                    ? 'bg-blue-600 text-white rounded-tr-none shadow-md shadow-blue-100' 
                    : 'bg-slate-50 text-slate-800 rounded-tl-none border border-slate-100'
                }`}>
                  {msg.text}
                </div>
              </div>
            </motion.div>
          ))}
          {isTyping && (
            <div className="flex justify-start">
              <div className="flex gap-3">
                <div className="w-8 h-8 rounded-lg bg-blue-100 text-blue-600 flex items-center justify-center">
                  <Bot size={18} />
                </div>
                <div className="bg-slate-50 p-4 rounded-2xl rounded-tl-none border border-slate-100 flex items-center gap-1">
                  <span className="w-1.5 h-1.5 bg-blue-400 rounded-full animate-bounce"></span>
                  <span className="w-1.5 h-1.5 bg-blue-400 rounded-full animate-bounce [animation-delay:0.2s]"></span>
                  <span className="w-1.5 h-1.5 bg-blue-400 rounded-full animate-bounce [animation-delay:0.4s]"></span>
                </div>
              </div>
            </div>
          )}
        </div>

        <div className="p-6 border-t border-slate-100 bg-slate-50/50">
          <div className="relative flex items-center gap-2">
            <input 
              type="text" 
              placeholder="请输入您的问题..."
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleSend()}
              className="flex-grow p-4 bg-white border border-slate-200 rounded-2xl shadow-sm focus:ring-2 focus:ring-blue-500 focus:outline-none transition-all pr-16"
            />
            <button 
              onClick={handleSend}
              disabled={!input.trim() || isTyping}
              className="absolute right-2 p-3 bg-blue-600 text-white rounded-xl hover:bg-blue-700 transition-all disabled:opacity-50 disabled:hover:bg-blue-600 shadow-lg shadow-blue-200"
            >
              <Send size={20} />
            </button>
          </div>
          <p className="text-[10px] text-slate-400 text-center mt-3">
            AI生成内容仅供参考，具体请以政府官方发布文件为准
          </p>
        </div>
      </div>
    </div>
  );
};

export default ChatPage;
