# SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Application configuration using pydantic-settings."""

import os
from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Application settings loaded from environment variables."""

    model_config = SettingsConfigDict(
        # Secure Production Pattern: Strictly use OS environment variables.
        # No local .env files are permitted in the source tree for production.
        case_sensitive=False,
        extra="ignore",
    )

    # Application
    app_name: str = "Agentic Commerce Middleware"
    app_version: str = "1.0.0"
    debug: bool = os.getenv("DEBUG", "false").lower() == "true"
    log_level: str = "INFO"  # DEBUG, INFO, WARNING, ERROR
    log_sql: bool = False  # Enable verbose SQL logging (very noisy)

    # Database
    database_url: str = os.getenv("DATABASE_URL", "sqlite:///./agentic_commerce.db")

    # Webhook Configuration (Merchant → Client Agent)
    # Default to localhost for local development; Docker overrides via environment
    webhook_url: str = "http://localhost:3000/api/webhooks/acp"
    webhook_secret: str = os.getenv("WEBHOOK_SECRET", "")

    # Merchant API Security (for client authentication)
    merchant_api_key: str = ""
    vaultier_internal_secret: str = os.getenv("VAULTIER_INTERNAL_SECRET", "")
    # Hardened: Strictly use the paid Firebase domain from the vault
    vaultier_domain: str = os.getenv("VAULTIER_DOMAIN", "spresso-5561f.web.app")
    allowed_origins: list[str] = [
        "https://spresso-5561f.web.app",
        "https://vaultier-retail.web.app" # Legacy fallback for grace period
    ]

    # UCP Discovery Configuration
    ucp_version: str = "2026-01-23"
    ucp_base_url: str | None = (
        None  # Fully qualified base URL; None derives from request
    )
    ucp_business_name: str | None = None
    ucp_continue_url: str | None = None  # Fallback URL for negotiation failures
    ucp_order_webhook_url: str = "http://localhost:3000/api/webhooks/ucp"

    # UCP Signing Key (public key for webhook verification)
    ucp_signing_key_id: str = "ucp-key-1"
    ucp_signing_key_kty: str = "EC"  # "EC" or "OKP"
    ucp_signing_key_crv: str = "P-256"  # "P-256" or "Ed25519"
    ucp_signing_key_alg: str = "ES256"  # "ES256" or "EdDSA"
    ucp_signing_key_x: str = ""  # Base64url-encoded public key x
    ucp_signing_key_y: str = ""  # Base64url-encoded public key y (EC only)

    # Promotion Agent Configuration
    promotion_agent_url: str = "http://localhost:8002"
    promotion_agent_timeout: float = 10.0  # seconds (NFR-LAT target)

    # Post-Purchase Agent Configuration
    post_purchase_agent_url: str = "http://localhost:8003"
    post_purchase_agent_timeout: float = 15.0  # seconds

    # Gemini Integration
    use_gemini: bool = True
    google_api_key: str = ""
    vaultier_policy_path: str = "src/merchant/services/ai/config/stylist_policy.yaml"

    # AI Security Firewall
    lakera_guard_api_key: str = os.getenv("LAKERA_GUARD_API_KEY", "")

    # Sentry Configuration
    sentry_dsn: str | None = os.getenv("SENTRY_DSN")
    sentry_environment: str = os.getenv("ENV", "development")
    sentry_traces_sample_rate: float = 1.0

    # Redis Configuration
    redis_url: str = os.getenv("REDIS_URL", "")

    # Stripe Configuration
    stripe_secret_key: str = os.getenv("STRIPE_SECRET_KEY", "")
    stripe_webhook_secret: str = os.getenv("STRIPE_WEBHOOK_SECRET", "")
    stripe_api_version: str = "2024-06-20"


@lru_cache
def get_settings() -> Settings:
    """Get cached application settings."""
    return Settings()
