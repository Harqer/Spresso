/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
*/


import React, { useState } from 'react';
import { useAuth } from './components/AuthProvider';
import { signInWithPopup, GoogleAuthProvider } from 'firebase/auth';
import { auth } from './services/firebase';
import Navbar from './components/Navbar';
import ChatDiscovery from './components/ChatDiscovery';
import DiscoveryFeed from './components/DiscoveryFeed';
import Footer from './components/Footer';
import ProductDetail from './components/ProductDetail';
import JournalDetail from './components/JournalDetail';
import CartDrawer from './components/CartDrawer';
import Checkout from './components/Checkout';
import { Product, JournalArticle, ViewState } from './types';

function App() {
  const { user, loading } = useAuth();
  const [view, setView] = useState<ViewState>({ type: 'home' });
  const [cartItems, setCartItems] = useState<Product[]>([]);
  const [isCartOpen, setIsCartOpen] = useState(false);

  // Vaultier Handlers
  const handleSignIn = async () => {
    const provider = new GoogleAuthProvider();
    await signInWithPopup(auth, provider);
  };

  const addToCart = (product: Product) => {
    setCartItems(prev => [...prev, product]);
    setIsCartOpen(true);
  };

  const removeFromCart = (productId: string) => {
    setCartItems(prev => prev.filter(item => item.id !== productId));
  };

  const handleProductClick = (product: Product) => {
    setView({ type: 'product', product });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const handleNavClick = (e: React.MouseEvent, target: string) => {
      e.preventDefault();
      if (target === 'feed') {
          setView({ type: 'feed' });
      } else if (target === 'products') {
          // In home view we'd scroll, but here we just reset to home if needed
          setView({ type: 'home' });
      } else {
          setView({ type: 'home' });
      }
      window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  // Vaultier Branding for Clerk
  const clerkAppearance = {
    elements: {
      formButtonPrimary: 'bg-[#2C2A26] hover:bg-[#433E38] text-sm uppercase tracking-widest transition-all rounded-none py-4',
      card: 'bg-white border border-[#EBE7DE] shadow-none rounded-none p-8',
      headerTitle: 'font-serif text-3xl text-[#2C2A26] text-center',
      headerSubtitle: 'text-[#5D5A53] text-center mb-8',
      socialButtonsBlockButton: 'border border-[#EBE7DE] rounded-none hover:bg-[#F5F2EB] transition-colors',
      footerActionLink: 'text-[#2C2A26] font-medium hover:text-[#433E38]',
      dividerLine: 'bg-[#EBE7DE]',
      dividerText: 'text-[#A8A29E] text-xs uppercase tracking-widest'
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-[#F5F2EB] flex items-center justify-center">
        <div className="w-8 h-8 border-4 border-[#2C2A26] border-t-transparent rounded-full animate-spin"></div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#F5F2EB] font-sans text-[#2C2A26] selection:bg-[#D6D1C7] selection:text-[#2C2A26] flex flex-col">
      {!user ? (
        <div className="flex-1 flex items-center justify-center p-6 bg-[url('https://images.unsplash.com/photo-1494438639946-1ebd1d20bf85?auto=format&fit=crop&q=80&w=2000')] bg-cover bg-center text-center">
          <div className="absolute inset-0 bg-[#F5F2EB]/90 backdrop-blur-sm"></div>
          <div className="w-full max-w-md relative z-10 animate-fade-in-up">
            <div className="mb-16">
                <h1 className="text-5xl font-serif mb-4 tracking-tight">Vaultier</h1>
                <p className="text-[#A8A29E] uppercase tracking-[0.3em] text-[10px] font-medium">AI Fashion Concierge & Space</p>
            </div>
            <div className="bg-white border border-[#EBE7DE] p-12 space-y-8">
              <h2 className="text-2xl font-serif">Enter the Vault</h2>
              <p className="text-sm text-[#5D5A53]">Please sign in with your identity to access personalized discovery.</p>
              <button
                onClick={handleSignIn}
                className="w-full py-5 bg-[#2C2A26] text-[#F5F2EB] uppercase tracking-widest text-xs font-bold hover:bg-[#433E38] transition-all"
              >
                Sign In with Google
              </button>
            </div>
          </div>
        </div>
      ) : (
        <>
          {view.type !== 'checkout' && (
              <Navbar
                  onNavClick={handleNavClick}
                  cartCount={cartItems.length}
                  onOpenCart={() => setIsCartOpen(true)}
              />
          )}

          <main className="flex-1">
              {view.type === 'home' && (
              <ChatDiscovery
                  onAddToCart={addToCart}
                  onProductClick={handleProductClick}
              />
              )}

              {view.type === 'feed' && (
              <DiscoveryFeed
                  onAddToCart={addToCart}
                  onProductClick={handleProductClick}
              />
              )}

              {view.type === 'product' && (
              <ProductDetail
                  product={view.product}
                  onBack={() => setView({ type: 'home' })}
                  onAddToCart={addToCart}
              />
              )}

              {view.type === 'journal' && (
              <JournalDetail
                  article={view.article}
                  onBack={() => setView({ type: 'home' })}
              />
              )}

              {view.type === 'checkout' && (
                  <Checkout
                      items={cartItems}
                      onBack={() => setView({ type: 'home' })}
                  />
              )}
          </main>

          {view.type !== 'home' && view.type !== 'feed' && view.type !== 'checkout' && <Footer onLinkClick={handleNavClick} />}

          <CartDrawer
              isOpen={isCartOpen}
              onClose={() => setIsCartOpen(false)}
              items={cartItems}
              onRemoveItem={removeFromCart}
              onCheckout={() => {
                  setIsCartOpen(false);
                  window.scrollTo({ top: 0, behavior: 'smooth' });
                  setView({ type: 'checkout' });
              }}
          />
        </>
      )}
    </div>
  );
}

export default App;
