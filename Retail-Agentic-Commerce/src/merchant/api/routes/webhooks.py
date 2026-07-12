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

@router.post("/github")
async def github_webhook(request: Request):
    """GitHub Webhook Handler for automated Agentic remediation.

    Receives push and Dependabot events to trigger autonomous AI agents.
    """
    payload = await request.body()
    sig_header = request.headers.get("x-hub-signature-256")
    webhook_secret = os.getenv("GITHUB_WEBHOOK_SECRET")

    if not webhook_secret:
        logger.warning("No GITHUB_WEBHOOK_SECRET configured, skipping signature validation.")
    elif not sig_header:
        logger.error("Security Alert: GitHub webhook triggered without signature.")
        raise HTTPException(status_code=400, detail="Missing signature")

    # In a real scenario, validate HMAC SHA256 signature here using webhook_secret and payload

    data = await request.json()
    event_type = request.headers.get("x-github-event", "unknown")

    logger.info(f"Received GitHub Webhook Event: {event_type}")

    if event_type == "dependabot_alert":
        action = data.get("action", "unknown")
        alert = data.get("alert", {})
        summary = alert.get("security_advisory", {}).get("summary", "Unknown vulnerability")
        package = alert.get("dependency", {}).get("package", {}).get("name", "Unknown package")
        severity = alert.get("security_vulnerability", {}).get("severity", "unknown")
        logger.warning(
            f"DEPENDABOT ALERT [{severity.upper()}] | Action: {action} | "
            f"Package: {package} | Issue: {summary}"
        )

    elif event_type == "code_scanning_alert":
        action = data.get("action", "unknown")
        alert = data.get("alert", {})
        rule = alert.get("rule", {}).get("description", "Unknown rule")
        logger.warning(f"CODE SCANNING ALERT | Action: {action} | Rule: {rule}")

    return {"status": "received", "event": event_type}
