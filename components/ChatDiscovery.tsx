/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
*/

import React, { useState, useRef, useEffect } from 'react';
import { useAuth } from './AuthProvider';
import { Turnstile, type TurnstileInstance } from '@marsidev/react-turnstile';
import { ChatMessage, Product } from '../types';
import { sendMessageToGemini } from '../services/geminiService';
import { PRODUCTS } from '../constants';

interface ChatDiscoveryProps {
  onAddToCart: (product: Product) => void;
  onProductClick: (product: Product) => void;
}

const ChatDiscovery: React.FC<ChatDiscoveryProps> = ({ onAddToCart, onProductClick }) => {
  const { getToken } = useAuth();
  const [messages, setMessages] = useState<ChatMessage[]>([
    { role: 'model', text: 'Welcome to Vaultier. I am your personal fashion concierge. I can help you find latest drops, trend reports, or perform virtual try-ons. How can I assist your style discovery today?', timestamp: Date.now() }
  ]);
  const [inputValue, setInputValue] = useState('');
  const [isThinking, setIsThinking] = useState(false);
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [selectedImage, setSelectedImage] = useState<string | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const turnstileRef = useRef<TurnstileInstance>(null);
  const [turnstileToken, setTurnstileToken] = useState<string | null>(null);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages, isThinking]);

  const handleSend = async (forcedMessage?: string, imageBase64?: string) => {
    const text = forcedMessage || inputValue;
    if (!text.trim() && !imageBase64) return;

    const userMsg: ChatMessage = {
        role: 'user',
        text: text || "Analyzing image...",
        timestamp: Date.now()
    };

    setMessages(prev => [...prev, userMsg]);
    setInputValue('');
    setSelectedImage(null);
    setIsThinking(true);

    try {
      const token = await getToken();
      if (!token) throw new Error("Not authenticated");

      // Verify Turnstile for web requests
      if (!turnstileToken) {
          throw new Error("Security check required. Please wait for Turnstile to verify.");
      }

      const history = messages.map(m => ({ role: m.role, text: m.text }));
      const response = await sendMessageToGemini(history, text, token, [], imageBase64, turnstileToken);

      const aiMsg: ChatMessage = {
        role: 'model',
        text: response.text,
        timestamp: Date.now(),
        grid: response.grid,
        compare: response.compare,
        vto_image_url: response.vto_image_url,
        vto_video_url: response.vto_video_url,
        citation: response.citation
      };
      setMessages(prev => [...prev, aiMsg]);

      // Handle Agentic Actions
      if (response.action && response.action.type === 'ADD_TO_CART') {
          const product = PRODUCTS.find(p => p.id === response.action?.id);
          if (product) {
              onAddToCart(product);
          }
      }
    } catch (error) {
        console.error("Chat Error:", error);
    } finally {
      setIsThinking(false);
    }
  };

  const handleImageUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
        const reader = new FileReader();
        reader.onloadend = () => {
            const base64String = reader.result as string;
            setSelectedImage(base64String);
            const pureBase64 = base64String.split(',')[1];
            handleSend("Virtual Try-On requested for this item.", pureBase64);
        };
        reader.readAsDataURL(file);
    }
    setIsMenuOpen(false);
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="flex flex-col h-[calc(100vh-85px)] bg-[#F5F2EB]">
      {/* Messages Area */}
      <div className="flex-1 overflow-y-auto px-6 md:px-0" ref={scrollRef}>
        <div className="max-w-3xl mx-auto py-12 space-y-12">
          {messages.map((msg, idx) => (
            <div key={idx} className="space-y-6">
                <div className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'} animate-fade-in-up`}>
                    <div
                        className={`max-w-[90%] md:max-w-[80%] p-6 rounded-2xl shadow-sm ${
                        msg.role === 'user'
                            ? 'bg-[#2C2A26] text-[#F5F2EB]'
                            : 'bg-white border border-[#EBE7DE] text-[#5D5A53]'
                        }`}
                    >
                        <div className="text-base leading-relaxed whitespace-pre-wrap">
                            {msg.text}
                        </div>
                        {msg.citation && (
                            <div className="mt-4 pt-4 border-t border-[#EBE7DE] flex items-center gap-2">
                                <span className="text-[10px] uppercase tracking-widest text-[#A8A29E]">Source:</span>
                                <a href={msg.citation.url} target="_blank" className="text-[10px] font-bold text-[#2C2A26] hover:underline uppercase tracking-tighter">
                                    {msg.citation.source}
                                </a>
                            </div>
                        )}
                    </div>
                </div>

                {/* VTO Result (Nano Banana 2 / Higgsfield) */}
                {(msg.vto_image_url || msg.vto_video_url) && (
                    <div className="max-w-sm mx-auto bg-white border border-[#EBE7DE] rounded-3xl overflow-hidden shadow-2xl animate-fade-in-up">
                        <div className="aspect-[9/16] relative bg-black">
                            {msg.vto_video_url ? (
                                <video
                                    src={msg.vto_video_url}
                                    autoPlay
                                    loop
                                    muted
                                    playsInline
                                    className="w-full h-full object-cover"
                                />
                            ) : (
                                <div className="relative h-full w-full">
                                    <img
                                        src={msg.vto_image_url}
                                        className="w-full h-full object-cover"
                                        alt="Virtual Try-On Result"
                                    />
                                    {/* Upgrade Overlay for Free Tier */}
                                    <div className="absolute inset-0 bg-black/40 backdrop-blur-[2px] flex flex-col items-center justify-center p-6 text-center">
                                        <p className="text-white text-xs font-bold uppercase tracking-widest mb-4">Unlock Motion Modeling</p>
                                        <button
                                            onClick={() => { /* Open Clerk Pricing/Checkout */ }}
                                            className="px-6 py-3 bg-white text-[#2C2A26] text-[10px] font-bold uppercase tracking-widest rounded-full hover:bg-[#F5F2EB] transition-colors"
                                        >
                                            Upgrade to Creator
                                        </button>
                                    </div>
                                </div>
                            )}
                            <div className="absolute top-4 left-4 bg-white/20 backdrop-blur-md px-3 py-1 rounded-full border border-white/30">
                                <span className="text-[10px] text-white uppercase tracking-widest font-bold">Vaultier VTO</span>
                            </div>
                        </div>
                        <div className="p-6 text-center">
                            <p className="text-xs text-[#A8A29E] uppercase tracking-widest mb-4">Higgsfield-1 Motion Engine</p>
                            <button
                                onClick={() => {
                                    // Extract the primary product for VTO (usually the first in the catalog for this build)
                                    onAddToCart(PRODUCTS[0]);
                                }}
                                className="w-full py-4 bg-[#2C2A26] text-white text-xs uppercase tracking-widest font-bold"
                            >
                                Add to Cart
                            </button>
                        </div>
                    </div>
                )}

                {/* Grid UI */}
                {msg.grid && (
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 animate-fade-in-up">
                        {msg.grid.map(id => {
                            const p = PRODUCTS.find(prod => prod.id === id);
                            if (!p) return null;
                            return (
                                <div key={id} className="bg-white border border-[#EBE7DE] overflow-hidden group cursor-pointer" onClick={() => onProductClick(p)}>
                                    <div className="aspect-[4/3] bg-[#EBE7DE] overflow-hidden">
                                        <img src={p.imageUrl} className="w-full h-full object-cover transition-transform group-hover:scale-105" />
                                    </div>
                                    <div className="p-4 space-y-1">
                                        <h4 className="font-serif text-[#2C2A26]">{p.name}</h4>
                                        <p className="text-xs text-[#A8A29E]">${p.price} • {p.tagline}</p>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                )}
            </div>
          ))}
          {isThinking && (
             <div className="flex justify-start animate-fade-in">
               <div className="bg-white border border-[#EBE7DE] p-6 rounded-2xl flex gap-2 items-center shadow-sm">
                 <div className="w-2 h-2 bg-[#A8A29E] rounded-full animate-bounce"></div>
                 <div className="w-2 h-2 bg-[#A8A29E] rounded-full animate-bounce [animation-delay:-0.15s]"></div>
                 <div className="w-2 h-2 bg-[#A8A29E] rounded-full animate-bounce [animation-delay:-0.3s]"></div>
               </div>
             </div>
          )}
        </div>
      </div>

      {/* Input Area */}
      <div className="bg-gradient-to-t from-[#F5F2EB] via-[#F5F2EB] to-transparent pt-12 pb-8 px-6">
        <div className="max-w-3xl mx-auto relative group">

          {/* Tool Menu (Expansion List) */}
          {isMenuOpen && (
              <div className="absolute bottom-full left-0 mb-4 w-64 bg-white border border-[#EBE7DE] shadow-2xl rounded-2xl overflow-hidden animate-fade-in-up z-50">
                  <button
                    onClick={() => fileInputRef.current?.click()}
                    className="w-full px-6 py-4 text-left text-sm text-[#5D5A53] hover:bg-[#F5F2EB] transition-colors border-b border-[#F5F2EB] flex items-center gap-3"
                  >
                      <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-5 h-5">
                        <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 15.75l5.159-5.159a2.25 2.25 0 013.182 0l5.159 5.159m-1.5-1.5l1.409-1.409a2.25 2.25 0 013.182 0l2.909 2.909m-18 3.75h16.5a1.5 1.5 0 001.5-1.5V6a1.5 1.5 0 00-1.5-1.5H3.75A1.5 1.5 0 002.25 6v12a1.5 1.5 0 001.5 1.5zm10.5-11.25h.008v.008h-.008V8.25zm.375 0a.375.375 0 11-.75 0 .375.375 0 01.75 0z" />
                      </svg>
                      Try-On from Photo
                  </button>
                  <button
                    onClick={() => handleSend("Give me a trend report from Vogue and E! News.")}
                    className="w-full px-6 py-4 text-left text-sm text-[#5D5A53] hover:bg-[#F5F2EB] transition-colors flex items-center gap-3"
                  >
                      <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-5 h-5">
                        <path strokeLinecap="round" strokeLinejoin="round" d="M12 7.5h1.5m-1.5 3h1.5m-7.5 3h7.5m-7.5 3h7.5m3-9h3.375c.621 0 1.125.504 1.125 1.125V18a2.25 2.25 0 01-2.25 2.25H5.25A2.25 2.25 0 013 18V6.125C3 5.504 3.504 5 4.125 5H8.25c.621 0 1.125.504 1.125 1.125v3.5m7.5 10.375H9.375a1.125 1.125 0 01-1.125-1.125v-9.25m12 6.625v-1.875a3.375 3.375 0 00-3.375-3.375h-1.5a1.125 1.125 0 01-1.125-1.125v-1.5a3.375 3.375 0 00-3.375-3.375H9.75" />
                      </svg>
                      Latest Trend Report
                  </button>
              </div>
          )}

          <div className="relative flex items-end bg-white border border-[#D6D1C7] rounded-3xl shadow-xl shadow-[#2C2A26]/5 focus-within:border-[#2C2A26] transition-all duration-300">
            {/* The Plus Symbol (Expansion List) */}
            <div className="pb-3 pl-3">
                <input
                    type="file"
                    className="hidden"
                    ref={fileInputRef}
                    accept="image/*"
                    onChange={handleImageUpload}
                />
                <button
                    onClick={() => setIsMenuOpen(!isMenuOpen)}
                    className={`p-3 rounded-2xl transition-all duration-300 ${isMenuOpen ? 'bg-[#2C2A26] text-white rotate-45' : 'text-[#A8A29E] hover:bg-[#F5F2EB]'}`}
                >
                    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2.5} stroke="currentColor" className="w-5 h-5">
                        <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
                    </svg>
                </button>
            </div>

            <textarea
              rows={1}
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              onKeyDown={handleKeyPress}
              placeholder="Tell Vaultier what you're looking for..."
              className="w-full bg-transparent px-6 py-5 text-base outline-none resize-none placeholder-[#A8A29E] text-[#2C2A26] min-h-[64px] max-h-[200px]"
              style={{ height: 'auto' }}
            />
            <div className="pb-3 pr-3">
                <button
                    onClick={() => handleSend()}
                    disabled={!inputValue.trim() || isThinking || !turnstileToken}
                    className="bg-[#2C2A26] text-[#F5F2EB] p-3 rounded-2xl hover:bg-[#433E38] transition-all duration-300 disabled:opacity-20 disabled:grayscale"
                >
                    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2.5} stroke="currentColor" className="w-5 h-5">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 10.5 12 3m0 0 7.5 7.5M12 3v18" />
                    </svg>
                </button>
            </div>
          </div>

          {/* Invisible Turnstile Integration */}
          <Turnstile
            ref={turnstileRef}
            siteKey={import.meta.env.VITE_TURNSTILE_SITE_KEY}
            options={{
              action: 'chat_discovery',
              theme: 'light',
              size: 'invisible'
            }}
            onSuccess={(token) => setTurnstileToken(token)}
            onExpire={() => setTurnstileToken(null)}
            onError={() => setTurnstileToken(null)}
          />

          <p className="mt-4 text-[10px] text-center text-[#A8A29E] uppercase tracking-widest font-medium">
            AI can make mistakes. Verify important information.
          </p>
        </div>
      </div>
    </div>
  );
};

export default ChatDiscovery;
