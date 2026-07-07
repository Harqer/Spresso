# SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
# SPDX-License-Identifier: Apache-2.0

"""Post-purchase service for generating multilingual shipping updates.

This service integrates with the Post-Purchase Agent (NAT) to generate
human-like shipping messages based on brand persona and order context.

Industrial Architecture:
- NO FALLBACKS: If the agent is unavailable, the system fails fast to ensure
  operational visibility and prevent stale communication delivery.
"""

import asyncio
import json
import logging
import time
from enum import StrEnum
from typing import TypedDict

import httpx

from src.merchant.config import get_settings
from src.merchant.services.agent_outcomes import record_agent_outcome

logger = logging.getLogger(__name__)


class ShippingStatus(StrEnum):
    """Shipping status values for post-purchase communications."""

    ORDER_CONFIRMED = "order_confirmed"
    ORDER_SHIPPED = "order_shipped"
    OUT_FOR_DELIVERY = "out_for_delivery"
    DELIVERED = "delivered"


class MessageTone(StrEnum):
    """Available tone options for brand persona."""

    FRIENDLY = "friendly"
    PROFESSIONAL = "professional"
    CASUAL = "casual"
    URGENT = "urgent"


class SupportedLanguage(StrEnum):
    """Supported languages for message generation."""

    ENGLISH = "en"
    SPANISH = "es"
    FRENCH = "fr"


class BrandPersona(TypedDict):
    """Brand persona configuration for message generation."""

    company_name: str
    tone: str
    preferred_language: str


class OrderContext(TypedDict):
    """Order information for message personalization."""

    order_id: str
    customer_name: str
    items: list["OrderItem"]
    tracking_url: str | None
    estimated_delivery: str | None


class OrderItem(TypedDict):
    """Item info for post-purchase messaging."""

    name: str
    quantity: int


class ShippingMessageRequest(TypedDict):
    """Input format sent from ACP endpoint to Post-Purchase Agent."""

    brand_persona: BrandPersona
    order: OrderContext
    status: str


class ShippingMessageResponse(TypedDict):
    """Output format returned by Post-Purchase Agent to ACP endpoint."""

    order_id: str
    status: str
    language: str
    subject: str
    message: str


class PostPurchaseAgentClient:
    """Async HTTP client for calling the Post-Purchase Agent REST API."""

    def __init__(self, base_url: str, timeout: float = 15.0):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout

    async def generate_message(
        self, request: ShippingMessageRequest
    ) -> ShippingMessageResponse:
        """Call the Post-Purchase Agent to generate a shipping message."""
        async with httpx.AsyncClient(timeout=self.timeout) as client:
            response = await client.post(
                f"{self.base_url}/generate",
                json={"query": json.dumps(request)},
                headers={"Content-Type": "application/json"},
            )

            if response.status_code != 200:
                raise RuntimeError(f"Agent service returned {response.status_code}")

            result = response.json()
            if "value" in result:
                message_data = json.loads(result["value"])
                return ShippingMessageResponse(
                    order_id=message_data.get("order_id", request["order"]["order_id"]),
                    status=message_data.get("status", request["status"]),
                    language=message_data.get(
                        "language", request["brand_persona"]["preferred_language"]
                    ),
                    subject=message_data.get("subject", ""),
                    message=message_data.get("message", ""),
                )

            raise ValueError("Unexpected response format from agent")


_default_client: PostPurchaseAgentClient | None = None


def get_post_purchase_client(
    base_url: str, timeout: float = 15.0
) -> PostPurchaseAgentClient:
    global _default_client
    if _default_client is None or _default_client.base_url != base_url:
        _default_client = PostPurchaseAgentClient(base_url, timeout)
    return _default_client


def format_order_items(items: list[OrderItem]) -> str:
    if not items:
        return ""
    return ", ".join(f"{item['name']} (x{item['quantity']})" for item in items)


def build_message_request(
    order_id: str,
    customer_name: str,
    items: list[OrderItem],
    status: ShippingStatus,
    company_name: str = "Vaultier Elite",
    tone: MessageTone = MessageTone.FRIENDLY,
    language: SupportedLanguage = SupportedLanguage.ENGLISH,
    tracking_url: str | None = None,
    estimated_delivery: str | None = None,
) -> ShippingMessageRequest:
    return ShippingMessageRequest(
        brand_persona=BrandPersona(
            company_name=company_name,
            tone=tone.value,
            preferred_language=language.value,
        ),
        order=OrderContext(
            order_id=order_id,
            customer_name=customer_name,
            items=items,
            tracking_url=tracking_url,
            estimated_delivery=estimated_delivery,
        ),
        status=status.value,
    )


async def generate_shipping_message(
    request: ShippingMessageRequest,
    client: PostPurchaseAgentClient | None = None,
) -> ShippingMessageResponse:
    """Generate a shipping message using the Post-Purchase Agent."""
    started = time.perf_counter()
    status = "success"
    error_code: str | None = None
    settings = get_settings()

    try:
        if settings.use_gemini:
            from src.merchant.services.gemini_agents import get_gemini_service

            gemini = get_gemini_service()
            return await gemini.generate_shipping_message(request)

        if client is None:
            agent_url = getattr(settings, "post_purchase_agent_url", None)
            if not agent_url:
                raise ValueError("Post-purchase agent URL not configured")
            client = get_post_purchase_client(
                agent_url, getattr(settings, "post_purchase_agent_timeout", 15.0)
            )

        return await client.generate_message(request)
    except Exception as e:
        status = "error_internal"
        error_code = type(e).__name__
        logger.error(f"Post-purchase generation failure: {e}")
        raise
    finally:
        latency_ms = int((time.perf_counter() - started) * 1000)
        record_agent_outcome(
            agent_type="post_purchase",
            channel="acp",
            status=status,
            latency_ms=latency_ms,
            error_code=error_code,
        )


async def generate_shipping_messages_batch(
    requests: list[ShippingMessageRequest],
    client: PostPurchaseAgentClient | None = None,
) -> list[ShippingMessageResponse]:
    """Generate shipping messages for multiple orders in parallel."""
    tasks = [generate_shipping_message(req, client) for req in requests]
    return await asyncio.gather(*tasks)
