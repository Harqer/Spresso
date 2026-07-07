/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
*/

import React from 'react';

export interface Product {
  id: string;
  name: string;
  tagline: string;
  description: string;
  longDescription?: string;
  price: number;
  category: 'Audio' | 'Wearable' | 'Mobile' | 'Home';
  imageUrl: string;
  gallery?: string[];
  features: string[];
  sizes?: string[];
}

export interface JournalArticle {
  id: number;
  title: string;
  date: string;
  excerpt: string;
  image: string;
  content: React.ReactNode; // Allowing JSX for rich formatting/poems
}

export interface ChatMessage {
  role: 'user' | 'model';
  text: string;
  timestamp: number;
  grid?: string[];
  compare?: any[];
  filters?: string[];
  match_score?: number;
  vto_image_url?: string;
  vto_video_url?: string;
  citation?: {
    source: string;
    url: string;
  };
}

export enum LoadingState {
  IDLE = 'IDLE',
  LOADING = 'LOADING',
  ERROR = 'ERROR',
  SUCCESS = 'SUCCESS'
}

export type ViewState = 
  | { type: 'home' }
  | { type: 'feed' }
  | { type: 'product', product: Product }
  | { type: 'journal', article: JournalArticle }
  | { type: 'checkout' };
