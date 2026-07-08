# SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
# SPDX-License-Identifier: Apache-2.0

import pytest
from fastapi.testclient import TestClient

class TestAutonomicRepair:
    """Test suite for POST /autonomic/repair endpoint."""

    def test_report_failure_returns_202(self, auth_client: TestClient) -> None:
        """Happy path: Reporting a failure returns 202 Accepted."""
        payload = {
            "job": "test-job",
            "url": "http://gitlab.com/test-job",
            "error": "Simulated failure",
            "branch": "main"
        }
        response = auth_client.post("/autonomic/repair", json=payload)
        assert response.status_code == 202
        assert response.json()["status"] == "received"

    def test_report_failure_requires_auth(self, client: TestClient) -> None:
        """Failure case: Accessing endpoint without API key returns 401."""
        payload = {
            "job": "test-job",
            "url": "http://gitlab.com/test-job",
            "error": "Simulated failure"
        }
        response = client.post("/autonomic/repair", json=payload)
        assert response.status_code == 401
