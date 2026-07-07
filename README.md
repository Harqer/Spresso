<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Vaultier - Meta Wearables Agentic Retail Commerce

This project is a Meta Wearables implementation of the Agentic Retail Commerce protocol, powered by **Google Gemini 2.0 Flash** and **Higgsfield-1** for high-fidelity discovery.

## Key Features
- **Meta Wearables Integration**: Uses Meta's DAT SDK for registration and identity.
- **On-Device Discovery**: Powered by **Gemini Nano (Banana 2)** for zero-latency visual reasoning.
- **Higgsfield Motion VTO**: High-fidelity motion fit generation for industrial-grade virtual try-on.
- **Projected Glimmer UI**: Optimized for display glasses using Jetpack Compose Glimmer.
- **Zero-Mock Architecture**: Fully integrated with Neon Postgres, Clerk Auth, and Infisical.

## Project Structure
- `aura-mobile-android/`: Native Android project containing both Phone and Wear OS (Watch) implementations.
- `Retail-Agentic-Commerce/`: FastAPI middleware hosting the agentic business logic.
- Root: React/Vite web application and Protocol Inspector.

## Gemini Integration
Instead of NVIDIA NIM, this implementation uses Gemini 1.5 Flash for low-latency agent reasoning.
To enable:
1. Set `USE_GEMINI=true` in your environment (Infisical or `.env`).
2. Provide `GOOGLE_API_KEY`.

## Setup
1.  **Backend**: `cd Retail-Agentic-Commerce`, install dependencies, and run with `uvicorn`.
2.  **Android**: Open the `aura-mobile-android` project in Android Studio, register with Meta AI, and launch on glasses.
