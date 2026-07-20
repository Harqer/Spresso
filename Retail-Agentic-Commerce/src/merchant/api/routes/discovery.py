import asyncio
import base64
import contextlib
import json
import logging
from typing import Any

from fastapi import (
    APIRouter,
    Body,
    Depends,
    Header,
    Request,
    WebSocket,
    WebSocketDisconnect,
)
from pydantic import BaseModel
from sqlmodel import Session

from src.merchant.api.dependencies import get_secure_session, verify_api_key
from src.merchant.db.models import Product, ProductDiscovery
from src.merchant.services.gemini_agents import get_gemini_service

logger = logging.getLogger(__name__)

router = APIRouter(
    prefix="/discovery",
    tags=["discovery"],
)


@router.websocket("/live")
async def discovery_live(
    websocket: WebSocket,
    x_spresso_internal_key: str | None = Header(None),
    x_spresso_user_tier: str = Header("free"),
    x_spresso_user_features: str = Header(""),
):
    """Real-time multimodal discovery stream (Vision + Audio) via Gemini Live API."""
    from src.merchant.config import get_settings

    settings = get_settings()

    if (
        not x_spresso_internal_key
        or x_spresso_internal_key != settings.spresso_internal_secret
    ):
        await websocket.close(code=4003)
        return

    await websocket.accept()
    gemini = get_gemini_service()

    entitlements = [f.strip() for f in x_spresso_user_features.split(",") if f.strip()]
    # Added explicit instruction for Voice Output
    spresso_prompt = (
        f"You are Spresso, an ambient fashion concierge. User Tier: {x_spresso_user_tier}. "
        f"Entitlements: {', '.join(entitlements)}. "
        "You can see what the user sees and hear what they say. "
        "Respond naturally with both text and voice. "
        "If you identify a garment, provide style insights. "
        "Format action JSON when needed: {'intent': 'ADD_TO_CART', 'product_id': '...'}"
    )

    response_task = None
    try:
        async with gemini.start_live_session(
            system_instruction=spresso_prompt
        ) as session:

            async def forward_responses():
                try:
                    async for message in session:
                        # 2026 industrial Standard: Handle Text & Audio Parts
                        payload = {"type": "server_content", "content": ""}

                        if message.server_content and message.server_content.model_turn:
                            for part in message.server_content.model_turn.parts:
                                if part.text:
                                    payload["content"] += part.text
                                if (
                                    part.inline_data
                                    and part.inline_data.mime_type.startswith("audio")
                                ):
                                    payload["audio_data"] = base64.b64encode(
                                        part.inline_data.data
                                    ).decode()

                        # Check for Agentic JSON in text
                        try:
                            action_data = json.loads(payload["content"])
                            if action_data.get("intent") == "ADD_TO_CART":
                                await websocket.send_json(
                                    {
                                        "type": "agentic_action",
                                        "action": "ADD_TO_CART",
                                        "product_id": action_data["product_id"],
                                    }
                                )
                                continue
                        except (json.JSONDecodeError, KeyError):
                            pass

                        await websocket.send_json(payload)

                except asyncio.CancelledError:
                    # Billion-user scale: Graceful shutdown on task cancellation
                    pass
                except Exception as e:
                    logger.error(f"Error forwarding Gemini responses: {e}")

            response_task = asyncio.create_task(forward_responses())

            while True:
                # Set dynamic timeout to prevent orphaned ghost connections
                data = await asyncio.wait_for(websocket.receive_json(), timeout=90.0)
                if data["type"] == "client_content":
                    # Forwarding multimodal client data (Vision frames / Audio PCM)
                    await session.send(
                        input=data["content"],
                        end_of_turn=data.get("end_of_turn", False),
                    )

    except (TimeoutError, WebSocketDisconnect):
        logger.info(
            "Discovery Live session terminated due to disconnect or inactivity."
        )
    except Exception as e:
        logger.error(f"Discovery Live error: {e}")
    finally:
        if response_task:
            response_task.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await response_task


class Citation(BaseModel):
    source: str
    url: str


class ChatRequest(BaseModel):
    message: str
    cart_items: list[dict[str, Any]] = []
    image_base64: str | None = None


class ChatResponse(BaseModel):
    intent: str
    product_id: str | None = None
    response: str
    vto_image_url: str | None = None
    vto_video_url: str | None = None
    grid: list[ProductDiscovery] = []
    citation: Citation | None = None
    can_upgrade: bool = False


class ProfileUpdateRequest(BaseModel):
    height: float | None = None
    weight: float | None = None
    avatar_url: str | None = None


@router.post("/profile", dependencies=[Depends(verify_api_key)])
async def update_profile(
    request: Request,
    payload: ProfileUpdateRequest = Body(...),
    db: Session = Depends(get_secure_session),
):
    """Update user physical profile for precise VTO fit."""
    user_id = getattr(request.state, "user_id", "anon")
    customer = db.get(Customer, user_id)
    if not customer:
        # Should be created during onboarding/auth
        return {"status": "ERROR", "message": "User not found"}

    if payload.height:
        customer.height = payload.height
    if payload.weight:
        customer.weight = payload.weight
    if payload.avatar_url:
        customer.avatar_url = payload.avatar_url

    db.add(customer)
    db.commit()
    return {"status": "SUCCESS", "profile": customer}


@router.get("/trending", response_model=list[dict[str, Any]])
async def get_trending_discovery(
    db: Session = Depends(get_secure_session),
):
    """Retrieve trending discovery items using Recommendation Agent attribution."""
    from datetime import UTC, datetime, timedelta

    from src.merchant.services.recommendation_attribution import (
        summarize_recommendation_attribution,
    )

    # 1. Get top products from attribution service
    end = datetime.now(UTC)
    start = end - timedelta(days=30)
    summary = summarize_recommendation_attribution(db, start=start, end=end, top_limit=3)

    top_products = summary.get("top_products", [])

    # 2. Map to discovery format with simulated video URLs (Production Assets)
    results = []
    for i, p in enumerate(top_products):
        product_id = p["product_id"]
        product = db.get(Product, product_id)
        if not product:
            continue

        results.append({
            "id": f"trend_{i+1}",
            "videoUrl": f"https://cdn.spresso.ai/vto/trending_{product_id}.mp4",
            "product": {
                "id": product.id,
                "name": product.name,
                "tagline": product.tagline or "Trending now.",
                "price": product.base_price // 100,
            },
            "style": "Enterprise Modern",
            "world": "Production Pulse",
        })

    return results

@router.post(
    "/chat", response_model=ChatResponse, dependencies=[Depends(verify_api_key)]
)
async def discovery_chat(
    request: Request,
    chat_payload: ChatRequest = Body(...),
) -> ChatResponse:
    """Spresso multimodal fashion chat with VTO and Trend support."""
    gemini = get_gemini_service()
    user_id = getattr(request.state, "user_id", "anon")
    user_tier = getattr(request.state, "user_tier", "free")
    user_features = getattr(request.state, "user_features", [])

    user_metadata = {
        "user_id": user_id,
        "tier": user_tier,
        "features": user_features,
        "style": "Minimalist",
        "size": "M",
    }

    result = await gemini.detect_intent(
        user_message=chat_payload.message,
        current_cart=chat_payload.cart_items,
        user_metadata=user_metadata,
        image_data=chat_payload.image_base64,
    )

    return ChatResponse(
        intent=result.get("intent", "CHAT"),
        product_id=result.get("product_id"),
        response=result.get("response", "I'm here to help."),
        vto_image_url=result.get("vto_image_url"),
        vto_video_url=result.get("vto_video_url"),
        grid=result.get("grid", []),
        citation=result.get("citation"),
        can_upgrade=result.get("can_upgrade", False),
    )
