# SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
# SPDX-License-Identifier: Apache-2.0

"""Pytest configuration for Strict Production Integration Testing.

This suite enforces a "No-Fallback" policy. All tests require real
secrets to be injected (e.g., via Infisical). If secrets are missing,
the suite will crash immediately to prevent false positives.
"""

import os
from collections.abc import Generator
from unittest.mock import AsyncMock, MagicMock, patch

# Disable Gemini for unit tests globally before loading modules
os.environ["USE_GEMINI"] = "false"

import pytest
from fastapi.testclient import TestClient

from src.merchant.config import get_settings
from src.merchant.main import app
from src.merchant.services.idempotency import reset_idempotency_store
from src.merchant.services.promotion import PromotionAction, PromotionDecisionOutput


def get_required_secret(name: str) -> str:
    """Retrieves a secret or crashes the test run if missing."""
    secret = os.environ.get(name)
    if not secret:
        pytest.exit(
            f"CRITICAL CONFIGURATION ERROR: Required secret '{name}' is missing from the environment. "
            "Ensure you are running with 'infisical run'.",
            returncode=1,
        )
    return secret


def get_test_api_key() -> str:
    return get_required_secret("MERCHANT_API_KEY")


def get_test_internal_secret() -> str:
    return get_required_secret("VAULTIER_INTERNAL_SECRET")


@pytest.fixture(autouse=True)
def setup_test_environment(tmp_path) -> Generator[None, None, None]:
    """Strict environment synchronization."""
    from src.merchant.db.database import init_db, reset_engine

    get_settings.cache_clear()

    # Verify presence of production-grade secrets
    _ = get_test_api_key()
    _ = get_test_internal_secret()

    # Capture original state
    original_db_url = os.environ.get("DATABASE_URL")

    # Use a file-based SQLite for test reliability across connections
    db_file = tmp_path / "test_vaultier.db"
    os.environ["DATABASE_URL"] = f"sqlite:///{db_file}"

    # Reset engine to use the new URL and create tables
    reset_engine()
    init_db()

    get_settings.cache_clear()

    # Apply global mock for promotion agent client to prevent network calls during integration tests
    default_decision = PromotionDecisionOutput(
        product_id="test",
        action=PromotionAction.NO_PROMO,
        reason_codes=["NO_URGENCY"],
        reasoning="Default mock decision for tests",
    )
    mock_client = MagicMock()
    mock_client.get_promotion_decision = AsyncMock(return_value=default_decision)

    with patch(
        "src.merchant.services.promotion.get_promotion_client"
    ) as mock_get_client:
        mock_get_client.return_value = mock_client
        yield

    # Restore clean state
    reset_idempotency_store()
    if original_db_url:
        os.environ["DATABASE_URL"] = original_db_url
    else:
        os.environ.pop("DATABASE_URL", None)

    reset_engine()
    get_settings.cache_clear()


@pytest.fixture
def client() -> Generator[TestClient, None, None]:
    """Industrial Test Client with real secret propagation."""
    with TestClient(app) as test_client:
        test_client.headers["X-Vaultier-Internal-Key"] = get_test_internal_secret()
        yield test_client


@pytest.fixture
def auth_client() -> Generator[TestClient, None, None]:
    """Authenticated Production Client using real keys."""
    with TestClient(app) as test_client:
        test_client.headers["Authorization"] = f"Bearer {get_test_api_key()}"
        test_client.headers["X-Vaultier-Internal-Key"] = get_test_internal_secret()
        yield test_client


@pytest.fixture
def auth_client_x_api_key() -> Generator[TestClient, None, None]:
    """Authenticated Client (X-API-Key variant) using real keys."""
    with TestClient(app) as test_client:
        test_client.headers["X-API-Key"] = get_test_api_key()
        test_client.headers["X-Vaultier-Internal-Key"] = get_test_internal_secret()
        yield test_client


# External service mocks are disabled by default.
# They must be explicitly patched in individual unit tests if needed.
