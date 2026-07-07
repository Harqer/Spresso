import logging
import os
import stripe

from fastapi import APIRouter, Header, HTTPException, Request
from svix.webhooks import Webhook, WebhookVerificationError

from src.merchant.db import get_session
from src.merchant.db.models import Customer, Order

logger = logging.getLogger(__name__)

router = APIRouter(
    prefix="/webhooks",
    tags=["webhooks"],
)


@router.post("/firebase")
async def firebase_webhook(
    request: Request,
):
    """Industrial Firebase Identity Sync.

    Synchronizes the Firebase User UID and tier into the local Neon DB.
    """
    try:
        data = await request.json()
        user_id = data.get("uid")
        email = data.get("email")
        name = data.get("displayName", "Vaultier Member")

        if not user_id or not email:
            raise HTTPException(status_code=400, detail="Incomplete user data")

        with next(get_session()) as session:
            existing = session.get(Customer, user_id)
            if not existing:
                new_customer = Customer(
                    id=user_id,
                    email=email,
                    name=name,
                    tier="free"
                )
                session.add(new_customer)
                session.commit()
                logger.info(f"Identity Synced: {user_id} added to Neon DB.")

        return {"status": "success", "synced": True}
    except Exception as e:
        logger.error(f"Identity Sync Failure: {e}")
        return {"status": "error", "message": str(e)}


@router.post("/stripe")
async def stripe_webhook(request: Request):
    """Industrial Stripe Webhook Handler with Signature Verification.

    Synchronizes payment success events with Neon Postgres order status.
    """
    payload = await request.body()
    sig_header = request.headers.get("stripe-signature")
    webhook_secret = os.getenv("STRIPE_WEBHOOK_SECRET")

    if not sig_header or not webhook_secret:
        logger.error("Security Alert: Stripe webhook triggered without signature or secret.")
        raise HTTPException(status_code=400, detail="Missing signature or secret")

    try:
        event = stripe.Webhook.construct_event(
            payload, sig_header, webhook_secret
        )
    except ValueError as e:
        logger.error(f"Invalid Payload: {e}")
        raise HTTPException(status_code=400, detail="Invalid payload")
    except stripe.error.SignatureVerificationError as e:
        logger.warning(f"Security Alert: Stripe Signature Verification Failed: {e}")
        raise HTTPException(status_code=400, detail="Invalid signature")

    # Handle the event
    if event['type'] == 'payment_intent.succeeded':
        intent = event['data']['object']
        order_id = intent.get('metadata', {}).get('order_id')

        if order_id:
            with next(get_session()) as session:
                order = session.get(Order, order_id)
                if order:
                    order.status = "completed"
                    session.commit()
                    logger.info(f"Order Synchronized: {order_id} marked as COMPLETED via Webhook.")

    elif event['type'] == 'payment_intent.payment_failed':
        intent = event['data']['object']
        order_id = intent.get('metadata', {}).get('order_id')
        error_message = intent.get('last_payment_error', {}).get('message', 'Unknown error')
        logger.warning(f"Payment Pulse FAILED for Order {order_id}: {error_message}")

    return {"status": "success"}
