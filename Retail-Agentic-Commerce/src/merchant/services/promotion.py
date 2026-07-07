# SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
# SPDX-License-Identifier: Apache-2.0

"""Promotion service implementing the 3-layer hybrid architecture.

Industrial Architecture:
- NO FALLBACKS: If the agent is unavailable, the system fails fast to ensure
  pricing integrity and prevent unauthorized discount applications.
"""

import asyncio
import json
import logging
import time
from datetime import date
from enum import StrEnum
from typing import Any, TypedDict

import httpx
from sqlmodel import Session, select

from src.merchant.config import get_settings
from src.merchant.db.models import CompetitorPrice, Product
from src.merchant.services.agent_outcomes import record_agent_outcome

logger = logging.getLogger(__name__)


class PromotionAction(StrEnum):
    """Allowed promotion actions - LLM must choose from these only."""

    NO_PROMO = "NO_PROMO"
    DISCOUNT_5_PCT = "DISCOUNT_5_PCT"
    DISCOUNT_10_PCT = "DISCOUNT_10_PCT"
    DISCOUNT_15_PCT = "DISCOUNT_15_PCT"
    FREE_SHIPPING = "FREE_SHIPPING"


ACTION_DISCOUNT_MAP: dict[PromotionAction, float] = {
    PromotionAction.NO_PROMO: 0.0,
    PromotionAction.DISCOUNT_5_PCT: 0.05,
    PromotionAction.DISCOUNT_10_PCT: 0.10,
    PromotionAction.DISCOUNT_15_PCT: 0.15,
    PromotionAction.FREE_SHIPPING: 0.0,
}


class InventoryPressure(StrEnum):
    HIGH = "high"
    LOW = "low"


class CompetitionPosition(StrEnum):
    ABOVE_MARKET = "above_market"
    AT_MARKET = "at_market"
    BELOW_MARKET = "below_market"


class SeasonalUrgency(StrEnum):
    PEAK = "peak"
    PRE_SEASON = "pre_season"
    POST_SEASON = "post_season"
    OFF_SEASON = "off_season"


class ProductLifecycle(StrEnum):
    NEW_ARRIVAL = "new_arrival"
    GROWTH = "growth"
    MATURE = "mature"
    CLEARANCE = "clearance"


class DemandVelocity(StrEnum):
    ACCELERATING = "accelerating"
    FLAT = "flat"
    DECELERATING = "decelerating"


STOCK_THRESHOLD = 50
RETAIL_EVENTS: list[tuple[str, int, int]] = [
    ("valentines_day", 2, 14),
    ("easter", 4, 20),
    ("mothers_day", 5, 11),
    ("memorial_day", 5, 26),
    ("fathers_day", 6, 15),
    ("independence_day", 7, 4),
    ("labor_day", 9, 1),
    ("back_to_school", 8, 15),
    ("halloween", 10, 31),
    ("black_friday", 11, 28),
    ("cyber_monday", 12, 1),
    ("christmas", 12, 25),
    ("new_years", 1, 1),
]

_PEAK_WINDOW = 3
_PRE_SEASON_WINDOW = 14
_POST_SEASON_WINDOW = 14


class ReasonCode(StrEnum):
    HIGH_INVENTORY = "HIGH_INVENTORY"
    LOW_INVENTORY = "LOW_INVENTORY"
    ABOVE_MARKET = "ABOVE_MARKET"
    AT_MARKET = "AT_MARKET"
    BELOW_MARKET = "BELOW_MARKET"
    MARGIN_PROTECTED = "MARGIN_PROTECTED"
    NO_URGENCY = "NO_URGENCY"
    PEAK_SEASON = "PEAK_SEASON"
    PRE_SEASON = "PRE_SEASON"
    POST_SEASON = "POST_SEASON"
    OFF_SEASON = "OFF_SEASON"
    NEW_ARRIVAL = "NEW_ARRIVAL"
    CLEARANCE = "CLEARANCE"
    DEMAND_ACCELERATING = "DEMAND_ACCELERATING"
    DEMAND_DECELERATING = "DEMAND_DECELERATING"


class SignalsData(TypedDict):
    inventory_pressure: str
    competition_position: str
    seasonal_urgency: str
    product_lifecycle: str
    demand_velocity: str


class PromotionContextInput(TypedDict):
    product_id: str
    product_name: str
    base_price_cents: int
    stock_count: int
    min_margin: float
    lowest_competitor_price_cents: int
    signals: SignalsData
    allowed_actions: list[str]


class PromotionDecisionOutput(TypedDict):
    product_id: str
    action: str
    reason_codes: list[str]
    reasoning: str


class PromotionAgentClient:
    """Async HTTP client for calling the Promotion Agent REST API."""

    def __init__(self, base_url: str, timeout: float = 10.0):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout

    async def get_promotion_decision(
        self, context: PromotionContextInput
    ) -> PromotionDecisionOutput:
        """Call the Promotion Agent to get a promotion decision."""
        async with httpx.AsyncClient(timeout=self.timeout) as client:
            response = await client.post(
                f"{self.base_url}/generate",
                json={"query": json.dumps(context)},
                headers={"Content-Type": "application/json"},
            )

            if response.status_code != 200:
                raise RuntimeError(f"Promotion agent returned {response.status_code}")

            result = response.json()
            if "value" in result:
                decision = json.loads(result["value"])
                return PromotionDecisionOutput(
                    product_id=decision.get("product_id", context["product_id"]),
                    action=decision.get("action", PromotionAction.NO_PROMO.value),
                    reason_codes=decision.get("reason_codes", []),
                    reasoning=decision.get("reasoning", ""),
                )

            raise ValueError("Unexpected response format from promotion agent")


_default_client: PromotionAgentClient | None = None


def get_promotion_client(base_url: str, timeout: float = 10.0) -> PromotionAgentClient:
    global _default_client
    if _default_client is None or _default_client.base_url != base_url:
        _default_client = PromotionAgentClient(base_url, timeout)
    return _default_client


def compute_inventory_pressure(stock_count: int) -> InventoryPressure:
    return (
        InventoryPressure.HIGH
        if stock_count > STOCK_THRESHOLD
        else InventoryPressure.LOW
    )


def compute_competition_position(
    base_price: int, lowest_competitor_price: int | None
) -> CompetitionPosition:
    if lowest_competitor_price is None:
        return CompetitionPosition.BELOW_MARKET
    if base_price > lowest_competitor_price:
        return CompetitionPosition.ABOVE_MARKET
    elif base_price == lowest_competitor_price:
        return CompetitionPosition.AT_MARKET
    return CompetitionPosition.BELOW_MARKET


def compute_seasonal_urgency(today: date | None = None) -> SeasonalUrgency:
    if today is None:
        today = date.today()
    current_year = today.year
    for _event_name, month, day in RETAIL_EVENTS:
        try:
            event_date = date(current_year, month, day)
        except ValueError:
            continue
        delta_days = (today - event_date).days
        if abs(delta_days) <= _PEAK_WINDOW:
            return SeasonalUrgency.PEAK
        if -(_PEAK_WINDOW + _PRE_SEASON_WINDOW) <= delta_days < -_PEAK_WINDOW:
            return SeasonalUrgency.PRE_SEASON
        if _PEAK_WINDOW < delta_days <= _PEAK_WINDOW + _POST_SEASON_WINDOW:
            return SeasonalUrgency.POST_SEASON
    return SeasonalUrgency.OFF_SEASON


def filter_allowed_actions_by_margin(min_margin: float) -> list[str]:
    max_discount = 1.0 - min_margin
    allowed: list[str] = [
        action.value
        for action, discount in ACTION_DISCOUNT_MAP.items()
        if discount < max_discount
    ]
    if PromotionAction.NO_PROMO.value not in allowed:
        allowed.insert(0, PromotionAction.NO_PROMO.value)
    return allowed


def get_lowest_competitor_price(db: Session, product_id: str) -> int | None:
    statement = select(CompetitorPrice).where(CompetitorPrice.product_id == product_id)
    competitor_prices = db.exec(statement).all()
    if not competitor_prices:
        return None
    return min(cp.price for cp in competitor_prices)


def compute_promotion_context(db: Session, product: Product) -> PromotionContextInput:
    lowest_competitor_price = get_lowest_competitor_price(db, product.id)
    signals = SignalsData(
        inventory_pressure=compute_inventory_pressure(product.stock_count).value,
        competition_position=compute_competition_position(
            product.base_price, lowest_competitor_price
        ).value,
        seasonal_urgency=compute_seasonal_urgency().value,
        product_lifecycle=ProductLifecycle(product.lifecycle).value,
        demand_velocity=DemandVelocity(product.demand_velocity).value,
    )
    return PromotionContextInput(
        product_id=product.id,
        product_name=product.name,
        base_price_cents=product.base_price,
        stock_count=product.stock_count,
        min_margin=product.min_margin,
        lowest_competitor_price_cents=lowest_competitor_price or product.base_price,
        signals=signals,
        allowed_actions=filter_allowed_actions_by_margin(product.min_margin),
    )


async def call_promotion_agent(
    context: PromotionContextInput,
    client: PromotionAgentClient | None = None,
) -> PromotionDecisionOutput:
    """Call the Promotion Agent to get a promotion decision."""
    settings = get_settings()
    if settings.use_gemini:
        from src.merchant.services.gemini_agents import get_gemini_service

        gemini = get_gemini_service()
        return await gemini.get_promotion_decision(context)

    if client is None:
        client = get_promotion_client(
            settings.promotion_agent_url, settings.promotion_agent_timeout
        )

    return await client.get_promotion_decision(context)


def apply_promotion_action(base_price: int, action: str) -> int:
    try:
        promotion_action = PromotionAction(action)
        discount_rate = ACTION_DISCOUNT_MAP.get(promotion_action, 0.0)
    except ValueError:
        logger.warning(f"Invalid promotion action '{action}', reverting to NO_PROMO")
        discount_rate = 0.0
    return int(base_price * discount_rate)


def validate_discount_against_margin(
    base_price: int, discount: int, min_margin: float
) -> bool:
    return (base_price - discount) >= int(base_price * min_margin)


async def get_promotion_for_product(
    db: Session,
    product: Product,
    client: PromotionAgentClient | None = None,
) -> dict[str, Any]:
    """Get promotion decision for a single product. Fail fast on error."""
    started = time.perf_counter()
    status = "success"
    error_code: str | None = None

    try:
        context = compute_promotion_context(db, product)
        decision = await call_promotion_agent(context, client)

        if decision is None:
            raise RuntimeError(f"Promotion agent returned no decision for {product.id}")

        action = decision["action"]
        discount = apply_promotion_action(product.base_price, action)

        if not validate_discount_against_margin(
            product.base_price, discount, product.min_margin
        ):
            raise ValueError(
                f"Agent proposed discount {discount} violates margin for {product.id}"
            )

        return {
            "discount": discount,
            "action": action,
            "reason_codes": decision["reason_codes"],
            "reasoning": decision["reasoning"],
            "signals": context["signals"],
        }
    except Exception as e:
        status = "error_internal"
        error_code = type(e).__name__
        logger.error(f"Promotion processing failure: {e}")
        raise
    finally:
        latency_ms = int((time.perf_counter() - started) * 1000)
        record_agent_outcome(
            agent_type="promotion",
            channel="acp",
            status=status,
            latency_ms=latency_ms,
            session_id=None,
            error_code=error_code,
        )


async def get_promotions_for_products(
    db: Session,
    products: list[Product],
    client: PromotionAgentClient | None = None,
) -> list[dict[str, Any]]:
    """Get promotion decisions for multiple products. Fail fast if any fail."""
    tasks = [get_promotion_for_product(db, p, client) for p in products]
    return await asyncio.gather(*tasks)
