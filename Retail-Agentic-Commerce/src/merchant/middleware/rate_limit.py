import logging
import os

from fastapi import Request, Response
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.responses import JSONResponse
from upstash_redis import Redis

logger = logging.getLogger(__name__)


class RedisRateLimitMiddleware(BaseHTTPMiddleware):
    """Industrial-grade Rate Limiting using Upstash Redis.

    Protects sensitive AI discovery and transaction endpoints from connection exhaustion.
    Follows Cloudflare/Upstash secure REST patterns.
    """

    def __init__(self, app, limit: int = 60, window: int = 60):
        super().__init__(app)
        self.limit = limit
        self.window = window

        _url = os.getenv("UPSTASH_REDIS_REST_URL")
        _token = os.getenv("UPSTASH_REDIS_REST_TOKEN")

        if not _url or not _token:
            logger.error(
                "CRITICAL: Redis Rate Limiter Credentials Missing. Rate Limiting is DISABLED."
            )
            self.redis = None
        else:
            self.redis = Redis(
                url=_url,
                token=_token,
                rest_retries=5,
                rest_retry_interval=1,
            )

    async def dispatch(self, request: Request, call_next) -> Response:
        # Only rate limit sensitive production paths
        sensitive_paths = ["/discovery", "/orders", "/checkout_sessions"]
        if not self.redis or not any(
            request.url.path.startswith(path) for path in sensitive_paths
        ):
            return await call_next(request)

        # identify user by IP or Auth Token
        client_id = request.headers.get(
            "Authorization", request.client.host if request.client else "unknown"
        )
        import hashlib

        # expert strategy: partition rate limits by path to prevent global lockout
        key = f"spresso:ratelimit:{hashlib.md5(client_id.encode()).hexdigest()[:12]}:{request.url.path}"

        try:
            current = self.redis.get(key)
            if current and int(current) >= self.limit:
                logger.warning(f"Industrial Rate Limit Triggered: {key}")
                return JSONResponse(
                    status_code=429,
                    content={
                        "error": "Rate limit exceeded. Please wait before retrying."
                    },
                )

            # Atomic Increment and Expire
            pipeline = self.redis.pipeline()
            pipeline.incr(key)
            pipeline.expire(key, self.window)
            pipeline.execute()

        except Exception as e:
            # Fail-open in production to ensure availability, but log the failure
            logger.error(f"Rate Limit Infrastructure Failure: {e}")

        return await call_next(request)
