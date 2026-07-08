
/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
*/

import React, { useState, useEffect } from 'react';
import { useAuth } from './AuthProvider';
import { signInWithPopup, GoogleAuthProvider } from 'firebase/auth';
import { auth as firebaseAuth } from '../services/firebase';
import { Product } from '../types';
import { loadStripe, Stripe } from '@stripe/stripe-js';

// Standardized production backend URL
const API_BASE_URL = 'https://***REDACTED_PROJECT_ID***.web.app';

// Stripe Publishable Key - Should be injected via env in production
const STRIPE_PUBLISHABLE_KEY =
    (import.meta as any).env?.VITE_STRIPE_PUBLISHABLE_KEY || 'pk_test_placeholder';

interface CheckoutProps {
  items: Product[];
  onBack: () => void;
}

const Checkout: React.FC<CheckoutProps> = ({ items, onBack }) => {
  const { user, loading, getToken } = useAuth();
  const [stripe, setStripe] = useState<Stripe | null>(null);

  const [isProcessing, setIsProcessing] = useState(false);
  const [isCompleted, setIsCompleted] = useState(false);
  const [isAwaitingSecureConfirmation, setIsAwaitingSecureConfirmation] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [orderId, setOrderId] = useState<string | null>(null);

  const subtotal = items.reduce((sum, item) => sum + item.price, 0);
  const shipping = 0; // Free shipping
  const total = subtotal + shipping;

  // Initialize Stripe
  useEffect(() => {
    loadStripe(STRIPE_PUBLISHABLE_KEY).then((s) => setStripe(s));
  }, []);

  const handleInitialPay = async () => {
    if (items.length === 0) return;
    setIsProcessing(true);
    setAuthError(null);

    try {
        if (!user) {
            const provider = new GoogleAuthProvider();
            await signInWithPopup(firebaseAuth, provider);
            setIsProcessing(false);
            return;
        }

        const token = await getToken();
        if (!token) throw new Error("Identity verification failed.");

        // 1. Initialize an agentic checkout session on the production backend
        // FR-ACP-01: Initialize an agentic checkout session.
        const response = await fetch(`${API_BASE_URL}/checkout_sessions`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                items: items.map(item => ({ id: item.id, quantity: 1 })),
                buyer: {
                    first_name: user.displayName?.split(' ')[0] || 'Human',
                    last_name: user.displayName?.split(' ').slice(1).join(' ') || 'User',
                    email: user.email
                },
                fulfillment_address: {
                    name: user.displayName || 'Human User',
                    line_one: 'Aura Wearable Street 1', // Placeholder for demo
                    city: 'San Francisco',
                    state: 'CA',
                    country: 'US',
                    postal_code: '94105'
                }
            })
        });

        if (!response.ok) {
            const errorData = await response.json();
            throw new Error(errorData.detail || "Vaultier rejected the session initialization.");
        }

        const sessionData = await response.json();
        setSessionId(sessionData.id);

        console.log(`Checkout session initialized: ${sessionData.id}`);
        setIsAwaitingSecureConfirmation(true);
    } catch (err: any) {
        console.error("Session Initialization Error:", err);
        setAuthError(err.message || "Failed to initialize secure session.");
    } finally {
        setIsProcessing(false);
    }
  };

  const handleSecureConfirm = async () => {
    if (loading || !sessionId || !stripe) return;

    setIsProcessing(true);
    setAuthError(null);

    try {
      const token = await getToken();
      if (!token) throw new Error("Security check failed: Identity token not found.");

      // 2. Request Stripe PaymentIntent from the Backend
      // FR-ACP-02: Create a Stripe PaymentIntent for the session.
      const piResponse = await fetch(`${API_BASE_URL}/checkout_sessions/${sessionId}/payment_intent`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        }
      });

      if (!piResponse.ok) {
          const errorData = await piResponse.json();
          throw new Error(errorData.detail || "The Aura vault rejected the transaction pulse.");
      }

      const { client_secret, payment_intent_id } = await piResponse.json();

      // 3. Human-In-The-Loop: Confirm payment via Stripe SDK
      // This is the definitive "Human Pulse" authorization
      console.log("Awaiting human authorization pulse...");
      const result = await stripe.confirmCardPayment(client_secret, {
          payment_method: {
              card: {
                  token: 'tok_visa' // In a real app, we would collect card details via Stripe Elements
              },
              billing_details: {
                  name: user?.displayName || 'Human User',
                  email: user?.email || undefined
              }
          }
      });

      if (result.error) {
          throw new Error(result.error.message || "Payment confirmation failed.");
      }

      // 4. Finalize transaction with the backend
      // FR-ACP-03: Finalize transaction with a verified Stripe PaymentIntent.
      const completeResponse = await fetch(`${API_BASE_URL}/checkout_sessions/${sessionId}/complete`, {
          method: 'POST',
          headers: {
              'Authorization': `Bearer ${token}`,
              'Content-Type': 'application/json'
          },
          body: JSON.stringify({
              payment_data: {
                  token: payment_intent_id,
                  provider: 'stripe'
              }
          })
      });

      if (!completeResponse.ok) {
          const errorData = await completeResponse.json();
          throw new Error(errorData.detail || "Order finalization failed.");
      }

      const finalData = await completeResponse.json();
      setOrderId(finalData.order_id);

      // Success: Transaction completed
      setIsCompleted(true);

    } catch (err: any) {
      console.error("HITL Loop Interruption:", err);
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
          <p className="text-[#5D5A53] mb-4 font-light">Your secure order {orderId} has been placed.</p>
          <p className="text-[#5D5A53] mb-12 font-light text-sm italic">Your items are now being synchronized with your Aura Wearables via the Agentic Commerce Protocol.</p>
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
                   <input type="text" placeholder="Address" defaultValue="Aura Wearable Street 1" disabled={isAwaitingSecureConfirmation} className="w-full bg-transparent border-b border-[#D6D1C7] py-3 text-[#2C2A26] placeholder-[#A8A29E] outline-none focus:border-[#2C2A26] transition-colors" />
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
