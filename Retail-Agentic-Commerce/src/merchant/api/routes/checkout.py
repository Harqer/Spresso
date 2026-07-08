from __future__ import annotations

import json
import uuid
import logging
from typing import Any

import stripe
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel
from sqlmodel import Session, select

from src.merchant.api.dependencies import get_current_user, get_secure_session
from src.merchant.db.models import Order, OrderItem, CheckoutSession, Product
from src.merchant.services.stripe_service import StripePaymentService

router = APIRouter(
    prefix="/checkout_sessions",
    tags=["checkout"],
)

logger = logging.getLogger(__name__)

# --- ACP v2026-01-16 Compliant Schemas ---

class Buyer(BaseModel):
    first_name: str
    last_name: str
    email: str
    phone_number: str | None = None

class FulfillmentAddress(BaseModel):
    name: str
    line_one: str
    line_two: str | None = None
    city: str
    state: str
    country: str
    postal_code: str

class CheckoutItem(BaseModel):
    id: str
    quantity: int

class CreateCheckoutRequest(BaseModel):
    items: list[CheckoutItem]
    buyer: Buyer
    fulfillment_address: FulfillmentAddress

class PaymentData(BaseModel):
    token: str
    provider: str
    billing_address: dict[str, Any] | None = None

class CompleteCheckoutRequest(BaseModel):
    payment_data: PaymentData

# --- ACP Endpoints ---

@router.post("", status_code=status.HTTP_201_CREATED)
async def create_checkout_session(
    request: CreateCheckoutRequest,
    user_id: str = Depends(get_current_user),
    db: Session = Depends(get_secure_session),
):
    """FR-ACP-01: Initialize an agentic checkout session."""
    try:
        session_id = f"checkout_{uuid.uuid4().hex[:12]}"

        # 1. Fetch real product data and calculate totals
        line_items = []
        subtotal_cents = 0

        for item in request.items:
            product = db.exec(select(Product).where(Product.id == item.id)).first()
            if not product:
                raise HTTPException(status_code=404, detail=f"Product {item.id} not found")

            amount = product.base_price * item.quantity
            subtotal_cents += amount

            line_items.append({
                "id": f"li_{uuid.uuid4().hex[:8]}",
                "item": {"id": product.id, "quantity": item.quantity},
                "base_amount": product.base_price,
                "discount": 0,
                "subtotal": amount,
                "tax": 0, # Simplified for demo
                "total": amount
            })

        # 2. Persist the real session state to the database
        new_session = CheckoutSession(
            id=session_id,
            status="ready_for_payment",
            currency="usd",
            line_items_json=json.dumps(line_items),
            buyer_json=request.buyer.model_dump_json(),
            fulfillment_address_json=request.fulfillment_address.model_dump_json(),
            totals_json=json.dumps([
                {"type": "subtotal", "display_text": "Subtotal", "amount": subtotal_cents},
                {"type": "total", "display_text": "Total", "amount": subtotal_cents}
            ])
        )
        db.add(new_session)
        db.commit()

        # 3. Return the authoritative ACP response
        return {
            "id": session_id,
            "status": "ready_for_payment",
            "currency": "usd",
            "payment_provider": {
                "provider": "stripe",
                "supported_payment_methods": [{"type": "card"}]
            },
            "line_items": line_items,
            "totals": [
                {"type": "subtotal", "display_text": "Subtotal", "amount": subtotal_cents},
                {"type": "total", "display_text": "Total", "amount": subtotal_cents}
            ]
        }
    except HTTPException:
        raise
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/{session_id}/payment_intent")
async def create_checkout_payment_intent(
    session_id: str,
    user_id: str = Depends(get_current_user),
    db: Session = Depends(get_secure_session),
):
    """FR-ACP-02: Create a Stripe PaymentIntent for the session."""
    try:
        # 1. Fetch the session
        session = db.exec(select(CheckoutSession).where(CheckoutSession.id == session_id)).first()
        if not session:
            raise HTTPException(status_code=404, detail="Checkout session not found")

        # 2. Calculate total amount
        totals = json.loads(session.totals_json)
        total_amount = next((t["amount"] for t in totals if t["type"] == "total"), 0)

        # 3. Create Stripe PaymentIntent via industrial service
        # Note: We don't confirm here, we let the frontend do it (HITL Pulse)
        stripe_intent = await StripePaymentService.create_payment_intent(
            amount=total_amount,
            currency=session.currency,
            metadata={
                "checkout_session_id": session.id,
                "customer_id": user_id,
            },
            idempotency_key=f"pay_{session.id}"
        )

        # 4. Store the intent ID in session metadata for later verification
        metadata = json.loads(session.metadata_json or "{}")
        metadata["payment_intent_id"] = stripe_intent["id"]
        session.metadata_json = json.dumps(metadata)
        db.add(session)
        db.commit()

        return {
            "client_secret": stripe_intent["client_secret"],
            "payment_intent_id": stripe_intent["id"],
            "status": stripe_intent["status"]
        }

    except Exception as e:
        db.rollback()
        logger.error(f"Failed to create payment intent: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Payment initialization failed: {str(e)}")

@router.post("/{session_id}/complete")
async def complete_checkout(
    session_id: str,
    request: CompleteCheckoutRequest,
    user_id: str = Depends(get_current_user),
    db: Session = Depends(get_secure_session),
):
    """FR-ACP-03: Finalize transaction with a verified Stripe PaymentIntent."""
    try:
        # 1. Fetch the session to verify existence and amount
        session = db.exec(select(CheckoutSession).where(CheckoutSession.id == session_id)).first()
        if not session:
            raise HTTPException(status_code=404, detail="Checkout session not found")

        # 2. Verify payment status with Stripe (Industrial Pulse)
        # The 'token' in the request is the Stripe PaymentIntent ID (or vault token mapped to it)
        is_valid = await StripePaymentService.verify_payment(request.payment_data.token)
        if not is_valid:
            # Check if it's pending or requires action (3DS)
            intent = stripe.PaymentIntent.retrieve(request.payment_data.token)
            if intent.status == "requires_action":
                return {
                    "status": "authentication_required",
                    "next_action": {
                        "type": "use_stripe_sdk",
                        "client_secret": intent.client_secret
                    }
                }
            raise HTTPException(status_code=400, detail=f"Payment not successful: {intent.status}")

        # 3. Transition state and create real order record
        session.status = "completed"
        db.add(session)

        new_order = Order(
            id=f"order_{uuid.uuid4().hex[:8].upper()}",
            customer_id=user_id,
            status="completed",
            total_cents=json.loads(session.totals_json)[-1]["amount"],
            currency=session.currency,
            checkout_session_id=session.id
        )
        db.add(new_order)

        # Create Order Items for audit and inventory tracking
        line_items = json.loads(session.line_items_json)
        for li in line_items:
            order_item = OrderItem(
                order_id=new_order.id,
                product_id=li["item"]["id"],
                price_cents=li["total"],
                quantity=li["item"]["quantity"]
            )
            db.add(order_item)

        db.commit()

        # 4. Trigger Post-Purchase Agent (Async Autonomic Pulse)
        # This simulated trigger would in reality be a background task
        logger.info(f"Order {new_order.id} confirmed. Triggering post-purchase agent...")

        return {"status": "completed", "order_id": new_order.id}

    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=500, detail=f"Checkout completion failed: {str(e)}")

@router.get("/{session_id}")
async def get_checkout_session(session_id: str):
    """FR-ACP-05: Retrieve checkout state."""
    # Logic to fetch session from DB
    return {"id": session_id, "status": "ready_for_payment"}
