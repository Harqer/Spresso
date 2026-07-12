/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
*/


import React, { useState, useEffect } from 'react';
import { JournalArticle } from '@/types';

interface JournalProps {
  onArticleClick: (article: JournalArticle) => void;
}

const Journal: React.FC<JournalProps> = ({ onArticleClick }) => {
  const [articles, setArticles] = useState<JournalArticle[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchJournal = async () => {
      try {
        const response = await fetch('/discovery/journal');
        if (response.ok) {
          const data = await response.json();
          setArticles(data);
        }
      } catch (error) {
        console.error("Failed to fetch journal articles:", error);
      } finally {
        setLoading(false);
      }
    };
    fetchJournal();
  }, []);

  if (loading) {
    return (
      <section id="journal" className="bg-[#F5F2EB] py-32 px-6 md:px-12 flex items-center justify-center min-h-[500px]">
        <div className="animate-pulse font-serif text-2xl tracking-widest text-[#A8A29E]">Loading Journal...</div>
      </section>
    );
  }

  return (
    <section id="journal" className="bg-[#F5F2EB] py-32 px-6 md:px-12">
      <div className="max-w-[1800px] mx-auto">
        <div className="flex flex-col md:flex-row justify-between items-start md:items-end mb-20 pb-8 border-b border-[#D6D1C7]">
            <div>
                <span className="block text-xs font-bold uppercase tracking-[0.2em] text-[#A8A29E] mb-4">Editorial</span>
                <h2 className="text-4xl md:text-6xl font-serif text-[#2C2A26]">The Journal</h2>
            </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-12">
            {articles.map((article) => (
                <div key={article.id} className="group cursor-pointer flex flex-col text-left" onClick={() => onArticleClick(article)}>
                    <div className="w-full aspect-[4/3] overflow-hidden mb-8 bg-[#EBE7DE]">
                        <img 
                            src={article.image} 
                            alt={article.title} 
                            className="w-full h-full object-cover transition-transform duration-1000 group-hover:scale-105 grayscale-[0.2] group-hover:grayscale-0"
                        />
                    </div>
                    <div className="flex flex-col flex-1 text-left">
                        <span className="text-xs font-medium uppercase tracking-widest text-[#A8A29E] mb-3">{article.date}</span>
                        <h3 className="text-2xl font-serif text-[#2C2A26] mb-4 leading-tight group-hover:underline decoration-1 underline-offset-4">{article.title}</h3>
                        <p className="text-[#5D5A53] font-light leading-relaxed">{article.excerpt}</p>
                    </div>
                </div>
            ))}
        </div>
      </div>
    </section>
  );
};

export default Journal;
