# SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
# SPDX-License-Identifier: Apache-2.0

import os

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response


class InternalSecurityMiddleware(BaseHTTPMiddleware):
    """Zero-Trust Isolation & Identity Propagation Middleware.

    Ensures the FastAPI Brain only accepts requests from the Go Gateway
    and extracts the Clerk-administered subscription tier and entitlements.
    """

    async def dispatch(self, request: Request, call_next) -> Response:
        # Skip security for public endpoints like health checks and webhooks
        if request.url.path == "/health" or request.url.path.startswith("/webhooks"):
            return await call_next(request)

        # Retrieve internal secret from environment
        internal_secret = os.getenv("SPRESSO_INTERNAL_SECRET")
        request_secret = request.headers.get("X-Spresso-Internal-Key")

        # 2026 Zero-Trust Standard: Reject if secret is missing or mismatched
        if not internal_secret or request_secret != internal_secret:
            return Response(
                content="Network Isolation Violation: Direct access to Spresso Cortex is prohibited.",
                status_code=403,
            )

        # Identity Propagation: Store Tier and Entitlements in request state
        request.state.user_id = request.headers.get("X-Spresso-User-ID")
        request.state.user_tier = request.headers.get("X-Spresso-User-Tier", "free")

        # Features are propagated as a comma-separated string from the Go Gateway
        features_raw = request.headers.get("X-Spresso-User-Features", "")
        request.state.user_features = [
            f.strip() for f in features_raw.split(",") if f.strip()
        ]

        return await call_next(request)
