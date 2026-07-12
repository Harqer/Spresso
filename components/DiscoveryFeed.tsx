/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
*/

import React, { useState, useRef } from 'react';
import { Product } from '@/types';

interface VideoItem {
  id: string;
  videoUrl: string;
  product: Product;
  style: string;
  world: string;
}

interface DiscoveryFeedProps {
  onAddToCart: (product: Product) => void;
  onProductClick: (product: Product) => void;
}

const DiscoveryFeed: React.FC<DiscoveryFeedProps> = ({ onAddToCart, onProductClick }) => {
  const [videos, setVideos] = useState<VideoItem[]>([]);
  const [loading, setLoading] = useState(true);

  React.useEffect(() => {
    const fetchTrending = async () => {
      try {
        const response = await fetch('/discovery/trending');
        if (response.ok) {
          const data: VideoItem[] = await response.json();
          setVideos(data);
        }
      } catch (error) {
        console.error("Failed to fetch trending videos:", error);
      } finally {
        setLoading(false);
      }
    };
    fetchTrending();
  }, []);

  if (loading) {
    return (
      <div className="h-[calc(100vh-85px)] flex items-center justify-center bg-black text-white">
        <div className="animate-pulse font-serif text-2xl tracking-widest opacity-50">Vaultier is preparing your discovery feed...</div>
      </div>
    );
  }

  return (
    <div className="h-[calc(100vh-85px)] overflow-y-scroll snap-y snap-mandatory bg-black">
      {videos.map((item) => (
        <div key={item.id} className="h-full w-full snap-start relative group">
          {/* Video Player */}
          <video
            src={item.videoUrl}
            autoPlay
            loop
            muted
            playsInline
            className="h-full w-full object-cover"
          />

          {/* Overlay Content */}
          <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-transparent pointer-events-none" />

          {/* Product Info & Action */}
          <div className="absolute bottom-0 left-0 right-0 p-8 flex justify-between items-end">
            <div className="space-y-4 max-w-[70%]">
              <div className="flex gap-2">
                <span className="px-3 py-1 bg-white/20 backdrop-blur-md rounded-full text-[10px] text-white uppercase tracking-widest font-bold border border-white/30">
                  {item.style}
                </span>
                <span className="px-3 py-1 bg-white/20 backdrop-blur-md rounded-full text-[10px] text-white uppercase tracking-widest font-bold border border-white/30">
                  {item.world}
                </span>
              </div>
              <h3 className="font-serif text-3xl text-white tracking-tight">{item.product.name}</h3>
              <p className="text-white/80 text-sm font-light leading-relaxed">
                {item.product.tagline}
              </p>
            </div>

            <div className="flex flex-col gap-6 items-center">
              {/* Profile/Avatar */}
              <div className="w-12 h-12 rounded-full border-2 border-white overflow-hidden bg-[#2C2A26] flex items-center justify-center text-white font-serif italic shadow-xl pointer-events-auto cursor-pointer">
                V
              </div>

              {/* Action Buttons */}
              <button
                onClick={() => onAddToCart(item.product)}
                className="w-14 h-14 bg-white rounded-full flex items-center justify-center shadow-2xl pointer-events-auto hover:scale-110 transition-transform"
              >
                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="#2C2A26" className="w-6 h-6">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
                </svg>
              </button>

              <button
                onClick={() => onProductClick(item.product)}
                className="w-14 h-14 bg-black/40 backdrop-blur-md border border-white/30 rounded-full flex items-center justify-center pointer-events-auto hover:bg-black/60 transition-colors"
              >
                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="white" className="w-6 h-6">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c3.58 0 8.062 3.129 9.964 7.183a1.012 1.012 0 010 .639C20.577 16.49 16.51 19.5 12 19.5c-4.638 0-8.577-3.011-9.964-7.178z" />
                  <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                </svg>
              </button>
            </div>
          </div>

          {/* Progress Bar */}
          <div className="absolute top-0 left-0 right-0 h-1 bg-white/10">
            <div className="h-full bg-white/60 w-[30%]" />
          </div>
        </div>
      ))}
    </div>
  );
};

export default DiscoveryFeed;
