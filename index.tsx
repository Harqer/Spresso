/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
*/

import React from 'react';
import { createRoot } from 'react-dom/client';
import { AuthProvider } from './components/AuthProvider';
import * as Sentry from "@sentry/react";
import App from './App';

const SENTRY_DSN = import.meta.env.VITE_SENTRY_DSN || import.meta.env.NEXT_PUBLIC_SENTRY_DSN;

if (SENTRY_DSN) {
  Sentry.init({
    dsn: SENTRY_DSN,
    integrations: [
      Sentry.browserTracingIntegration(),
      Sentry.replayIntegration(),
    ],
    tracesSampleRate: 1.0,
    replaysSessionSampleRate: 0.1,
    replaysOnErrorSampleRate: 1.0,
    environment: import.meta.env.MODE,
  });
}

// Clerk was removed in favor of Firebase, removing stale check

const rootElement = document.getElementById('root');
if (!rootElement) {
  throw new Error("Could not find root element to mount to");
}

const root = createRoot(rootElement);
root.render(
  <React.StrictMode>
    <Sentry.ErrorBoundary fallback={
      <div className="min-h-screen bg-[#F5F2EB] flex flex-col items-center justify-center p-6 text-center">
        <h1 className="text-3xl font-serif text-[#2C2A26] mb-4">Something went wrong.</h1>
        <p className="text-[#5D5A53]">The application encountered an unexpected error. Our team has been notified.</p>
        <button onClick={() => window.location.reload()} className="mt-8 px-6 py-3 bg-[#2C2A26] text-[#F5F2EB] text-sm uppercase tracking-widest hover:bg-[#433E38] transition-colors">
          Reload Page
        </button>
      </div>
    }>
      <AuthProvider>
        <App />
      </AuthProvider>
    </Sentry.ErrorBoundary>
  </React.StrictMode>
);
