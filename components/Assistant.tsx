/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
*/

import React, { useState, useRef, useEffect } from 'react';
import { ChatMessage, Product } from '../types';
import { sendMessageToGemini } from '../services/geminiService';
import { PRODUCTS } from '../constants';

interface AssistantProps {
    onAddToCart?: (product: Product) => void;
}

const Assistant: React.FC<AssistantProps> = ({ onAddToCart }) => {
  const [isOpen, setIsOpen] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([
    { role: 'model', text: 'Welcome to Aura. I am here to help you find objects that resonate with your life. How may I assist?', timestamp: Date.now() }
  ]);
  const [inputValue, setInputValue] = useState('');
  const [isThinking, setIsThinking] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages, isOpen]);

  const handleSend = async () => {
    if (!inputValue.trim()) return;

    const userMsg: ChatMessage = { role: 'user', text: inputValue, timestamp: Date.now() };
    setMessages(prev => [...prev, userMsg]);
    setInputValue('');
    setIsThinking(true);

    try {
      const history = messages.map(m => ({ role: m.role, text: m.text }));
      const response = await sendMessageToGemini(history, userMsg.text);
      
      const aiMsg: ChatMessage = { role: 'model', text: response.text, timestamp: Date.now() };
      setMessages(prev => [...prev, aiMsg]);

      // Handle Agentic Actions
      if (response.action && response.action.type === 'ADD_TO_CART') {
          const product = PRODUCTS.find(p => p.id === response.action?.id);
          if (product && onAddToCart) {
              onAddToCart(product);
          }
      }
    } catch (error) {
        // Error handled in service
    } finally {
      setIsThinking(false);
    }
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="fixed bottom-8 right-8 z-50 flex flex-col items-end font-sans">
      {isOpen && (
        <div className="bg-[#F5F2EB] rounded-none shadow-2xl shadow-[#2C2A26]/10 w-[90vw] sm:w-[380px] h-[550px] mb-6 flex flex-col overflow-hidden border border-[#D6D1C7] animate-slide-up-fade">
          {/* Header */}
          <div className="bg-[#EBE7DE] p-5 border-b border-[#D6D1C7] flex justify-between items-center">
            <div className="flex items-center gap-3">
                <div className="w-2 h-2 bg-[#2C2A26] rounded-full animate-pulse"></div>
                <span className="font-serif italic text-[#2C2A26] text-lg">Concierge</span>
            </div>
            <button onClick={() => setIsOpen(false)} className="text-[#A8A29E] hover:text-[#2C2A26] transition-colors">
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1} stroke="currentColor" className="w-6 h-6">
                <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          {/* Chat Area */}
          <div className="flex-1 overflow-y-auto p-6 space-y-8 bg-[#F5F2EB]" ref={scrollRef}>
            {messages.map((msg, idx) => (
              <div key={idx} className="space-y-4 animate-in fade-in slide-in-from-bottom-2 duration-500">
                <div className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}>
                  <div
                    className={`max-w-[85%] p-5 text-sm leading-relaxed shadow-sm transition-all duration-300 ${
                      msg.role === 'user'
                        ? 'bg-[#2C2A26] text-[#F5F2EB] rounded-2xl rounded-tr-none'
                        : 'bg-white border border-[#EBE7DE] text-[#5D5A53] rounded-2xl rounded-tl-none'
                    }`}
                  >
                    {msg.text}
                  </div>
                </div>

                {/* --- STITCH UI: FILTER CHIPS --- */}
                {msg.filters && msg.filters.length > 0 && (
                  <div className="flex gap-2 overflow-x-auto no-scrollbar py-1">
                    {msg.filters.map((filter, fIdx) => (
                       <button key={fIdx} className="whitespace-nowrap px-4 py-1.5 rounded-full border border-[#D6D1C7] bg-white text-[11px] font-mono uppercase tracking-wider text-[#2C2A26] hover:bg-[#2C2A26] hover:text-white transition-all">
                         {filter} ×
                       </button>
                    ))}
                  </div>
                )}

                {/* --- STITCH UI: PRODUCT DISCOVERY GRID (Same Vibe) --- */}
                {msg.grid && msg.grid.length > 0 && (
                  <div className="space-y-3">
                    <div className="flex items-center gap-2 text-[#2C2A26] font-serif italic text-sm px-1">
                       <span>🔥 Closest Matches (same vibe)</span>
                    </div>
                    <div className="flex gap-4 overflow-x-auto pb-4 no-scrollbar -mx-2 px-2">
                        {msg.grid.map((product: any, pIdx) => (
                        <div key={pIdx} className="min-w-[220px] bg-white border border-[#EBE7DE] rounded-[24px] overflow-hidden group hover:shadow-xl hover:shadow-[#2C2A26]/5 transition-all duration-500">
                            <div className="relative h-40 bg-[#F5F2EB]">
                                <img src={product.imageUrl} alt={product.name} className="w-full h-full object-cover transition-transform duration-700 group-hover:scale-105" />
                                <div className="absolute top-3 left-3 bg-white/90 backdrop-blur-md px-2 py-1 rounded-lg shadow-sm">
                                    <span className="text-[10px] font-bold text-[#2C2A26]">{product.matchScore || 98}% MATCH</span>
                                </div>
                            </div>
                            <div className="p-4 space-y-2">
                                <div className="flex justify-between items-start">
                                    <h4 className="text-xs font-serif font-bold text-[#2C2A26] leading-tight">{product.name}</h4>
                                    <span className="text-xs font-bold text-[#2C2A26]">${product.price}</span>
                                </div>
                                <p className="text-[10px] text-[#A8A29E] leading-relaxed line-clamp-2">{product.description}</p>
                            </div>
                        </div>
                        ))}
                    </div>
                  </div>
                )}

                {/* --- STITCH UI: COMPARISON ENGINE --- */}
                {msg.compare && msg.compare.length > 0 && (
                  <div className="bg-white border border-[#EBE7DE] rounded-[24px] overflow-hidden text-[11px] shadow-sm">
                    <div className="bg-[#EBE7DE]/50 p-3 font-serif italic text-[#2C2A26] border-b border-[#EBE7DE]">Feature Comparison</div>
                    <div className="overflow-x-auto no-scrollbar">
                      <table className="w-full text-left border-collapse">
                        <thead>
                           <tr className="bg-[#F5F2EB]/30">
                              <th className="p-3 font-medium text-[#A8A29E] border-b border-[#F5F2EB]">Metric</th>
                              {msg.compare.map((item: any, cIdx) => (
                                <th key={cIdx} className="p-3 font-bold text-[#2C2A26] border-b border-[#F5F2EB]">{item.name}</th>
                              ))}
                           </tr>
                        </thead>
                        <tbody className="divide-y divide-[#F5F2EB]">
                            {Object.keys(msg.compare[0] || {}).filter(k => k !== 'name' && k !== 'id').map((feature) => (
                                <tr key={feature} className="hover:bg-[#F5F2EB]/20 transition-colors">
                                    <td className="p-3 font-medium text-[#5D5A53] capitalize">{feature}</td>
                                    {msg.compare?.map((item: any, iIdx) => (
                                        <td key={iIdx} className="p-3 text-[#2C2A26]">{item[feature] || 'N/A'}</td>
                                    ))}
                                </tr>
                            ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                )}
              </div>
            ))}
            {isThinking && (
               <div className="flex justify-start">
                 <div className="bg-white border border-[#EBE7DE] p-5 flex gap-1 items-center shadow-sm">
                   <div className="w-1.5 h-1.5 bg-[#A8A29E] rounded-full animate-bounce"></div>
                   <div className="w-1.5 h-1.5 bg-[#A8A29E] rounded-full animate-bounce delay-75"></div>
                   <div className="w-1.5 h-1.5 bg-[#A8A29E] rounded-full animate-bounce delay-150"></div>
                 </div>
               </div>
            )}
          </div>

          {/* Input Area */}
          <div className="p-5 bg-[#F5F2EB] border-t border-[#D6D1C7]">
            <div className="flex gap-2 relative">
              <input 
                type="text" 
                value={inputValue}
                onChange={(e) => setInputValue(e.target.value)}
                onKeyDown={handleKeyPress}
                placeholder="Ask anything..." 
                className="flex-1 bg-white border border-[#D6D1C7] focus:border-[#2C2A26] px-4 py-3 text-sm outline-none transition-colors placeholder-[#A8A29E] text-[#2C2A26]"
              />
              <button 
                onClick={handleSend}
                disabled={!inputValue.trim() || isThinking}
                className="bg-[#2C2A26] text-[#F5F2EB] px-4 hover:bg-[#444] transition-colors disabled:opacity-50"
              >
                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-5 h-5">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M13.5 4.5L21 12m0 0l-7.5 7.5M21 12H3" />
                </svg>
              </button>
            </div>
          </div>
        </div>
      )}

      <button 
        onClick={() => setIsOpen(!isOpen)}
        className="bg-[#2C2A26] text-[#F5F2EB] w-14 h-14 flex items-center justify-center rounded-full shadow-xl hover:scale-105 transition-all duration-500 z-50"
      >
        {isOpen ? (
             <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1} stroke="currentColor" className="w-6 h-6">
                <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 8.25l-7.5 7.5-7.5-7.5" />
             </svg>
        ) : (
            <span className="font-serif italic text-lg">Ai</span>
        )}
      </button>
    </div>
  );
};

export default Assistant;