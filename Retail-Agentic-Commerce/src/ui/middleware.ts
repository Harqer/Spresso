import arcjet, { detectBot, fixedWindow, shield } from "@arcjet/next";
import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

// 1. Arcjet Configuration (Production-Grade Edge Security)
const aj = arcjet({
  key: process.env.ARCJET_API_KEY!,
  rules: [
    shield({ mode: "LIVE" }), // Protection against common attacks (SQLi, XSS, etc.)
    detectBot({
      mode: "LIVE",
      allow: ["CATEGORY:SEARCH_ENGINE"], // Allow search engines but block scrapers
    }),
    fixedWindow({
      mode: "LIVE",
      window: "60s",
      max: 100, // 100 requests per minute per IP
    }),
  ],
});

// 2. Identity Gate Pattern (Firebase Transition)
// In a full production implementation, we would verify the Firebase Session Cookie here.
// For now, we maintain the security perimeter with Arcjet.
export async function middleware(req: NextRequest) {
  // --- PRODUCTION SECURITY GATE: ARCJET ---
  const decision = await aj.protect(req);

  if (decision.isDenied()) {
    if (decision.reason.isRateLimit()) {
      return NextResponse.json({ error: "Too many requests" }, { status: 429 });
    }
    if (decision.reason.isBot()) {
      return NextResponse.json({ error: "Bot access denied" }, { status: 403 });
    }
    return NextResponse.json({ error: "Access denied" }, { status: 403 });
  }

  return NextResponse.next();
}

export const config = {
  matcher: [
    // Skip Next.js internals and all static files, unless found in search params
    "/((?!_next|[^?]*\\.(?:html?|css|js(?!on)|jpe?g|webp|png|gif|svg|ttf|woff2?|ico|csv|docx?|xlsx?|zip|webmanifest)).*)",
    // Always run for API routes
    "/(api|trpc)(.*)",
  ],
};
