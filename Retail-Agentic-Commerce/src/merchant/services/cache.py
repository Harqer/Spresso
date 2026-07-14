# SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
# SPDX-License-Identifier: Apache-2.0

"""Industrial Caching Service using standard Redis."""

import json
import logging
from functools import wraps
from typing import Any, Callable

import redis
from src.merchant.config import get_settings

logger = logging.getLogger(__name__)
settings = get_settings()

# Expert Strategy: Singleton Redis Client with Connection Pooling
_redis_client: redis.Redis | None = None

def get_redis_client() -> redis.Redis | None:
    """Initialize or return the existing Redis client."""
    global _redis_client
    if _redis_client is None and settings.redis_url:
        try:
            _redis_client = redis.from_url(
                settings.redis_url,
                decode_responses=True,
                socket_timeout=5,
                retry_on_timeout=True
            )
            # Test connection
            _redis_client.ping()
            logger.info("Industrial Redis Connectivity: ESTABLISHED")
        except Exception as e:
            logger.error(f"CRITICAL: Redis Connection Failure: {e}")
            _redis_client = None
    return _redis_client

def cached(ttl: int = 300, prefix: str = "spresso"):
    """Decorator for caching function results in Redis."""
    def decorator(func: Callable):
        @wraps(func)
        def wrapper(*args, **kwargs):
            client = get_redis_client()
            if not client:
                return func(*args, **kwargs)

            # Create a deterministic cache key
            key_parts = [prefix, func.__name__]
            if args:
                key_parts.extend([str(arg) for arg in args])
            if kwargs:
                key_parts.extend([f"{k}:{v}" for k, v in sorted(kwargs.items())])

            cache_key = ":".join(key_parts)

            try:
                cached_val = client.get(cache_key)
                if cached_val:
                    logger.debug(f"Cache HIT: {cache_key}")
                    return json.loads(cached_val)
            except Exception as e:
                logger.error(f"Cache Read Error: {e}")

            # Execute real function
            result = func(*args, **kwargs)

            try:
                client.setex(cache_key, ttl, json.dumps(result))
                logger.debug(f"Cache SET: {cache_key}")
            except Exception as e:
                logger.error(f"Cache Write Error: {e}")

            return result
        return wrapper
    return decorator
