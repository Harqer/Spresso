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

# NAT Agents Dockerfile
# Shared image for promotion, post-purchase, and recommendation agents
# Multi-stage build for optimized production image and layer caching

FROM python:3.12-slim AS base

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    git \
    && rm -rf /var/lib/apt/lists/*

# Install uv package manager
RUN pip install uv

WORKDIR /app

# Dependencies stage
FROM base AS deps
# Copy only files required for dependency resolution
COPY pyproject.toml uv.lock README.md ./
COPY src/agents/pyproject.toml ./src/agents/pyproject.toml

# Install dependencies with frozen lockfile for deterministic builds
# We sync at the root to leverage the workspace-wide uv.lock
RUN uv sync --frozen --no-dev

# Runtime stage
FROM base AS runtime
# Copy virtual environment from deps stage
COPY --from=deps /app/.venv /app/.venv
ENV PATH="/app/.venv/bin:$PATH"

# Copy agent application code
COPY src/agents /app/src/agents
COPY src/data /app/src/data

# Working directory for agent execution
WORKDIR /app/src/agents

# No default CMD - set via docker-compose command for each agent
# Examples:
# - nat serve --config_file configs/promotion.yml --port 8002
# - nat serve --config_file configs/post-purchase.yml --port 8003
# - nat serve --config_file configs/recommendation.yml --port 8004
# - python scripts/seed_milvus.py (for Milvus seeder)
