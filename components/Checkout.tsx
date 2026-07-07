
/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
*/

import React, { useState } from 'react';
import { useAuth } from './AuthProvider';
import { signInWithPopup, GoogleAuthProvider } from 'firebase/auth';
import { auth as firebaseAuth } from '../services/firebase';
import { Product } from '../types';

interface CheckoutProps {
  items: Product[];
  onBack: () => void;
}

const Checkout: React.FC<CheckoutProps> = ({ items, onBack }) => {
  const { user, loading, getToken } = useAuth();

  const [isProcessing, setIsProcessing] = useState(false);
  const [isCompleted, setIsCompleted] = useState(false);
  const [isAwaitingSecureConfirmation, setIsAwaitingSecureConfirmation] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);

  const subtotal = items.reduce((sum, item) => sum + item.price, 0);
  const shipping = 0; // Free shipping
  const total = subtotal + shipping;

  const handleInitialPay = async () => {
    if (items.length === 0) return;
    setIsProcessing(true);
    // Real logic: Prepare metadata and transition to secure auth gate
    console.log("Vault engagement initialized. Transitioning to secure human authorization...");
    setIsAwaitingSecureConfirmation(true);
    setIsProcessing(false);
  };

  const handleSecureConfirm = async () => {
    if (loading) return;

    setIsProcessing(true);
    setAuthError(null);

    try {
      if (!user) {
        const provider = new GoogleAuthProvider();
        await signInWithPopup(firebaseAuth, provider);
        setIsProcessing(false);
        return;
      }

      // 1. Retrieve the signed session JWT
      const token = await getToken();
      if (!token) throw new Error("Security check failed: Identity token not found.");

      // 2. Transmit to the Aura Backend for stateless verification and order fulfillment
      // Note: Removed the /api prefix to match our FastAPI router exactly.
      const response = await fetch('/orders/complete', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ items, total })
      });

      if (!response.ok) {
          const errorData = await response.json();
          throw new Error(errorData.detail || "The Aura vault rejected the transaction signature.");
      }

      // Success: Transaction completed
      setIsCompleted(true);

    } catch (err: any) {
      console.error("Agentic Loop Interruption:", err);
      setAuthError(err.message || "Security validation failed. Please try again.");
    } finally {
      setIsProcessing(false);
    }
  };

  if (isCompleted) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-[#F5F2EB] animate-fade-in">
        <div className="text-center max-w-md px-6">
          <div className="w-20 h-20 bg-green-900 rounded-full flex items-center justify-center mx-auto mb-8">
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="white" className="w-10 h-10">
              <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 12.75l6 6 9-13.5" />
            </svg>
          </div>
          <h1 className="text-3xl font-serif text-[#2C2A26] mb-4">Purchase Confirmed</h1>
          <p className="text-[#5D5A53] mb-12 font-light">Your secure order has been placed. Your items are now being synchronized with your Aura Wearables.</p>
          <button
            onClick={onBack}
            className="w-full py-5 bg-[#2C2A26] text-[#F5F2EB] uppercase tracking-widest text-sm font-medium hover:bg-[#433E38] transition-colors"
          >
            Return to Home
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen pt-24 pb-24 px-6 bg-[#F5F2EB] animate-fade-in-up">
      <div className="max-w-6xl mx-auto">
        <button 
          onClick={onBack}
          className="group flex items-center gap-2 text-xs font-medium uppercase tracking-widest text-[#A8A29E] hover:text-[#2C2A26] transition-colors mb-12"
        >
          <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-4 h-4 group-hover:-translate-x-1 transition-transform">
            <path strokeLinecap="round" strokeLinejoin="round" d="M15.75 19.5L8.25 12l7.5-7.5" />
          </svg>
          Back to Shop
        </button>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-16 lg:gap-24">
          <div>
            <h1 className="text-3xl font-serif text-[#2C2A26] mb-4">Checkout</h1>
            <p className="text-sm text-[#5D5A53] mb-12">
                {isAwaitingSecureConfirmation
                    ? "Order prepared by Aura. Please confirm securely."
                    : "Complete your purchase using our secure agentic protocol."}
            </p>
            
            <div className="space-y-12">
              <div className={isAwaitingSecureConfirmation ? "opacity-50" : ""}>
                <h2 className="text-xl font-serif text-[#2C2A26] mb-6">Contact Information</h2>
                <div className="space-y-4">
                   <input type="email" placeholder="Email address" defaultValue={user?.email || ""} disabled={isAwaitingSecureConfirmation} className="w-full bg-transparent border-b border-[#D6D1C7] py-3 text-[#2C2A26] placeholder-[#A8A29E] outline-none focus:border-[#2C2A26] transition-colors" />
                </div>
              </div>

              <div className={isAwaitingSecureConfirmation ? "opacity-50" : ""}>
                <h2 className="text-xl font-serif text-[#2C2A26] mb-6">Shipping Address</h2>
                <div className="space-y-4">
                   <div className="grid grid-cols-2 gap-4">
                      <input type="text" placeholder="Full name" defaultValue={user?.displayName || ""} disabled={isAwaitingSecureConfirmation} className="w-full bg-transparent border-b border-[#D6D1C7] py-3 text-[#2C2A26] placeholder-[#A8A29E] outline-none focus:border-[#2C2A26] transition-colors" />
                   </div>
                   <input type="text" placeholder="Address" defaultValue="" disabled={isAwaitingSecureConfirmation} className="w-full bg-transparent border-b border-[#D6D1C7] py-3 text-[#2C2A26] placeholder-[#A8A29E] outline-none focus:border-[#2C2A26] transition-colors" />
                </div>
              </div>

              <div>
                {!isAwaitingSecureConfirmation ? (
                    <button
                        onClick={handleInitialPay}
                        disabled={isProcessing || items.length === 0}
                        className={`w-full py-5 bg-[#2C2A26] text-[#F5F2EB] uppercase tracking-widest text-sm font-medium transition-all ${
                            isProcessing ? 'opacity-50 cursor-wait' : 'hover:bg-[#433E38]'
                        }`}
                    >
                        {isProcessing ? 'Synchronizing...' : `Pay Now — $${total}`}
                    </button>
                ) : (
                    <div className="space-y-4 animate-fade-in">
                        <div className="p-6 border-2 border-dashed border-[#2C2A26] bg-white rounded-lg text-center">
                            <h3 className="font-serif text-lg mb-2">Stateless Authorization Required</h3>
                            <p className="text-sm text-[#5D5A53] mb-6">
                                {user
                                    ? `Confirm purchase as ${user.displayName || user.email}`
                                    : "Please use your identity to authorize this agentic purchase."}
                            </p>

                            {authError && <p className="text-xs text-red-600 mb-4">{authError}</p>}

                            <button
                                onClick={handleSecureConfirm}
                                disabled={isProcessing}
                                className={`w-full py-4 bg-green-900 text-white uppercase tracking-widest text-xs font-bold transition-all ${
                                    isProcessing ? 'opacity-50 cursor-wait' : 'hover:bg-green-800'
                                }`}
                            >
                                {isProcessing
                                    ? 'Verifying...'
                                    : user ? 'Confirm Securely' : 'Authorize Identity'}
                            </button>
                        </div>
                        <button onClick={() => setIsAwaitingSecureConfirmation(false)} className="w-full text-xs text-[#A8A29E] uppercase tracking-widest">Cancel and Edit Cart</button>
                    </div>
                )}
              </div>
            </div>
          </div>

          <div className="lg:pl-12 lg:border-l border-[#D6D1C7]">
            <h2 className="text-xl font-serif text-[#2C2A26] mb-8">Order Summary</h2>
            
            <div className="space-y-6 mb-8">
               {items.map((item, idx) => (
                 <div key={idx} className="flex gap-4">
                    <div className="w-16 h-16 bg-[#EBE7DE] relative overflow-hidden">
                       <img src={item.imageUrl} alt={item.name} className="w-full h-full object-cover" />
                    </div>
                    <div className="flex-1">
                       <h3 className="font-serif text-[#2C2A26] text-base">{item.name}</h3>
                       <p className="text-xs text-[#A8A29E]">{item.category}</p>
                    </div>
                    <span className="text-sm text-[#5D5A53]">${item.price}</span>
                 </div>
               ))}
            </div>

            <div className="border-t border-[#D6D1C7] pt-6 space-y-2">
              <div className="flex justify-between text-sm text-[#5D5A53]">
                 <span>Subtotal</span>
                 <span>${subtotal}</span>
              </div>
            </div>
            
            <div className="border-t border-[#D6D1C7] mt-6 pt-6">
               <div className="flex justify-between items-center">
                 <span className="font-serif text-xl text-[#2C2A26]">Total</span>
                 <div className="flex items-end gap-2">
                   <span className="text-xs text-[#A8A29E] mb-1">USD</span>
                   <span className="font-serif text-2xl text-[#2C2A26]">${total}</span>
                 </div>
               </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Checkout;
