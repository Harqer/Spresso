/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
*/


import React, { useState, useMemo, useEffect } from 'react';
import { PRODUCTS as FALLBACK_PRODUCTS } from '../constants';
import { Product } from '../types';
import ProductCard from './ProductCard';

const categories = ['All', 'Audio', 'Wearable', 'Mobile', 'Home'];

interface ProductGridProps {
  onProductClick: (product: Product) => void;
}

interface BackendProduct {
  id: string;
  sku: string;
  name: string;
  base_price: number;
  stock_count: number;
  min_margin: number;
  image_url: string;
  category: string;
  tagline: string;
  features: string[];
  description: string;
}

const ProductGrid: React.FC<ProductGridProps> = ({ onProductClick }) => {
  const [activeCategory, setActiveCategory] = useState('All');
  const [products, setProducts] = useState<Product[]>(FALLBACK_PRODUCTS);

  useEffect(() => {
    const fetchProducts = async () => {
      try {
        const response = await fetch('/products');
        if (response.ok) {
          const data: BackendProduct[] = await response.json();
          // Transform backend schema to UI schema if needed
          const transformed: Product[] = data.map((p) => ({
            id: p.id,
            name: p.name,
            tagline: p.tagline || p.category,
            description: p.description,
            price: p.base_price / 100,
            category: p.category as any, // Cast to expected enum-like type
            imageUrl: p.image_url,
            features: p.features || []
          }));
          setProducts(transformed);
        }
      } catch (error) {
        console.error("Failed to fetch live products, using fallback.", error);
      }
    };
    fetchProducts();
  }, []);

  const filteredProducts = useMemo(() => {
    if (activeCategory === 'All') return products;
    return products.filter(p => p.category === activeCategory);
  }, [activeCategory, products]);

  return (
    <section id="products" className="py-32 px-6 md:px-12 bg-[#F5F2EB]">
      <div className="max-w-[1800px] mx-auto">
        
        {/* Header Area */}
        <div className="flex flex-col items-center text-center mb-24 space-y-8">
          <h2 className="text-4xl md:text-6xl font-serif text-[#2C2A26]">The Collection</h2>
          
          {/* Minimal Filter */}
          <div className="flex flex-wrap justify-center gap-8 pt-4 border-t border-[#D6D1C7]/50 w-full max-w-2xl">
            {categories.map(cat => (
              <button
                key={cat}
                onClick={() => setActiveCategory(cat)}
                className={`text-sm uppercase tracking-widest pb-1 border-b transition-all duration-300 ${
                  activeCategory === cat 
                    ? 'border-[#2C2A26] text-[#2C2A26]' 
                    : 'border-transparent text-[#A8A29E] hover:text-[#2C2A26]'
                }`}
              >
                {cat}
              </button>
            ))}
          </div>
        </div>

        {/* Large Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-x-8 gap-y-20">
          {filteredProducts.map(product => (
            <ProductCard key={product.id} product={product} onClick={onProductClick} />
          ))}
        </div>
      </div>
    </section>
  );
};

export default ProductGrid;
