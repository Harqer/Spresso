/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
*/


import React from 'react';
import { Product } from '../types';

interface ProductCardProps {
  product: Product;
  onClick: (product: Product) => void;
}

const ProductCard: React.FC<ProductCardProps> = ({ product, onClick }) => {
  return (
    <div className="group flex flex-col gap-6 cursor-pointer" onClick={() => onClick(product)}>
      <div className="relative w-full aspect-[4/5] overflow-hidden bg-[#EBE7DE]">
        <img 
          src={product.imageUrl} 
          alt={product.name} 
          className="w-full h-full object-cover transition-transform duration-1000 ease-in-out group-hover:scale-110 sepia-[0.1]"
        />
        
        {/* Luxury Badge */}
        <div className="absolute top-6 left-6 z-10">
          <span className="bg-[#2C2A26] text-[#F5F2EB] px-3 py-1 text-[10px] uppercase tracking-[0.2em] font-bold">
            Limited Edition
          </span>
        </div>

        {/* Refined Quick View Overlay */}
        <div className="absolute inset-0 bg-[#2C2A26]/0 group-hover:bg-[#2C2A26]/10 transition-all duration-700 flex items-end justify-center pb-12">
            <div className="opacity-0 group-hover:opacity-100 transition-all duration-500 translate-y-8 group-hover:translate-y-0">
                <button className="bg-white text-[#2C2A26] px-10 py-4 text-[10px] uppercase tracking-[0.3em] font-bold shadow-2xl hover:bg-[#2C2A26] hover:text-white transition-colors duration-300">
                    Quick View
                </button>
            </div>
        </div>
      </div>
      
      <div className="space-y-2">
        <div className="flex justify-between items-start">
          <div className="text-left">
            <p className="text-[10px] uppercase tracking-[0.2em] text-[#A8A29E] font-bold mb-1">{product.category} • Vaultier Studio</p>
            <h3 className="text-2xl font-serif text-[#2C2A26] group-hover:opacity-70 transition-opacity">{product.name}</h3>
          </div>
          <span className="text-lg font-light text-[#2C2A26]">${product.price}</span>
        </div>
        <p className="text-xs font-light text-[#5D5A53] tracking-wide line-clamp-1">{product.tagline}</p>
      </div>
    </div>
  );
};

export default ProductCard;
