import logging
import os
from typing import Any

import sentry_sdk
import stripe

logger = logging.getLogger(__name__)

# Load Stripe keys from environment (injected by Infisical)
STRIPE_SECRET_KEY = os.getenv("STRIPE_SECRET_KEY", "")
stripe.api_key = STRIPE_SECRET_KEY


class StripePaymentService:
    """Industrial-grade Stripe integration with Idempotency and PCI compliance."""

    @staticmethod
    async def create_payment_intent(
        amount: int,
        currency: str = "usd",
        metadata: dict[str, Any] = None,
        idempotency_key: str = None
    ) -> dict[str, Any]:
        """Creates a Stripe PaymentIntent with Idempotency Guard."""
        try:
            # Expert Strategy: Idempotency keys prevent double-charging in high-volume agentic commerce
            intent = stripe.PaymentIntent.create(
                amount=amount,
                currency=currency,
                metadata=metadata or {},
                automatic_payment_methods={"enabled": True},
                idempotency_key=idempotency_key
            )
            logger.info(f"PaymentIntent Created: {intent.id} (Idempotency={idempotency_key})")
            return {
                "client_secret": intent.client_secret,
                "id": intent.id,
                "status": intent.status,
            }
        except stripe.error.IdempotencyError as e:
            logger.error(f"Idempotency Conflict: {e.user_message}")
            raise Exception("Duplicate transaction detected.") from e
        except stripe.StripeError as e:
            # Industrial Logging: Capturing specific Stripe error codes for audit trails
            logger.error(f"Stripe Error [{e.code}]: {e.user_message}")
            sentry_sdk.capture_exception(e)
            raise Exception(f"Payment failed: {e.user_message}") from e

    @staticmethod
    async def verify_payment(intent_id: str) -> bool:
        """Stateless status verification pulse for PaymentIntents."""
        try:
            intent = stripe.PaymentIntent.retrieve(intent_id)
            return intent.status == "succeeded"
        except stripe.StripeError as e:
            logger.error(f"Stripe Pulse Failure: {e.user_message}")
            return False
