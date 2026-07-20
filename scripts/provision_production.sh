#!/bin/bash
# Spresso Industrial Provisioning Script (Production Readiness)
# Active Models: nano-banana-2-lite, imagen-4.0-upscale-preview, veo-3.1-r2v-preview

set -e

echo "🚀 Initializing Production Provisioning for Spresso Suite..."

# 1. Sync Agent Dependencies
echo "📦 Syncing ADK 2.0 dependencies..."
cd Retail-Agentic-Commerce/src/agents
uv sync --dev

# 2. Build Multi-Agent Wheel
echo "🔨 Building agent orchestration package..."
uv build --wheel --out-dir deployment

# 3. Deploy to Google Cloud Reasoning Engine
# This activates the On-Brand GenMedia, Economic Research, Global KYC, and Marketing Agency agents.
echo "🌍 Deploying agents to Vertex AI Agent Runtime..."
adk deploy --project $GOOGLE_CLOUD_PROJECT --agent register:vto_orchestrator --name vto-suite-v4
adk deploy --project $GOOGLE_CLOUD_PROJECT --agent register:economic_planner --name economic-research-v1
adk deploy --project $GOOGLE_CLOUD_PROJECT --agent register:global_kyc_router --name global-kyc-v1
adk deploy --project $GOOGLE_CLOUD_PROJECT --agent register:marketing_coordinator --name marketing-agency-v1

# 4. Finalize Android Configuration
echo "📱 Validating Android production endpoint..."
grep "SPRESSO_BACKEND_URL" ../../../wearable-retail-app/build.gradle.kts

echo "✅ Provisioning Complete. All features are now live on Google Cloud."
echo "🔗 Access the tracing UI via: adk web"
