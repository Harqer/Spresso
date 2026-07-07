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

"""Payment intent service for processing payments."""

import uuid
from datetime import UTC, datetime

from sqlmodel import Session

from src.payment.api.schemas import (
    CreatePaymentIntentRequest,
    PaymentIntentResponse,
    PaymentIntentStatusEnum,
)
from src.payment.db.models import PaymentIntent, PaymentIntentStatus, VaultTokenStatus
from src.payment.services.vault_token import (
    get_allowance,
    get_vault_token,
    is_token_expired,
)


class VaultTokenNotFoundError(Exception):
    """Raised when a vault token is not found."""

    def __init__(self, token_id: str):
        self.token_id = token_id
        super().__init__(f"Vault token '{token_id}' not found")


class VaultTokenConsumedError(Exception):
    """Raised when a vault token has already been consumed."""

    def __init__(self, token_id: str):
        self.token_id = token_id
        super().__init__(f"Vault token '{token_id}' has already been consumed")


class VaultTokenExpiredError(Exception):
    """Raised when a vault token has expired."""

    def __init__(self, token_id: str):
        self.token_id = token_id
        super().__init__(f"Vault token '{token_id}' has expired")


class AmountExceedsAllowanceError(Exception):
    """Raised when the payment amount exceeds the allowance."""

    def __init__(self, amount: int, max_amount: int):
        self.amount = amount
        self.max_amount = max_amount
        super().__init__(
            f"Payment amount {amount} exceeds maximum allowance of {max_amount}"
        )


class CurrencyMismatchError(Exception):
    """Raised when the currency doesn't match the allowance."""

    def __init__(self, requested: str, allowed: str):
        self.requested = requested
        self.allowed = allowed
        super().__init__(
            f"Currency '{requested}' does not match allowance currency '{allowed}'"
        )


def generate_payment_intent_id() -> str:
    """Generate a unique payment intent ID.

    Returns:
        A unique payment intent ID in the format 'pi_{uuid12}'
    """
    return f"pi_{uuid.uuid4().hex[:12]}"


from src.merchant.services.stripe_service import StripePaymentService

async def create_and_process_payment_intent(
    db: Session,
    request: CreatePaymentIntentRequest,
) -> PaymentIntentResponse:
    """Create and process a payment intent using real Stripe integration."""
    # Get the vault token
    vault_token = get_vault_token(db, request.vault_token)

    if vault_token is None:
        raise VaultTokenNotFoundError(request.vault_token)

    # Check if token is already consumed
    if vault_token.status == VaultTokenStatus.CONSUMED:
        raise VaultTokenConsumedError(request.vault_token)

    # Check if token is expired
    if is_token_expired(vault_token):
        raise VaultTokenExpiredError(request.vault_token)

    # Get allowance constraints
    allowance = get_allowance(vault_token)

    # Validate amount
    max_amount: int = allowance.get("max_amount", 0)
    if request.amount > max_amount:
        raise AmountExceedsAllowanceError(request.amount, max_amount)

    # Validate currency
    allowed_currency: str = str(allowance.get("currency", "")).lower()
    if request.currency.lower() != allowed_currency:
        raise CurrencyMismatchError(request.currency.lower(), allowed_currency)

    # Industrial Strategy: Create real Stripe PaymentIntent
    # This replaces the simulated completion with a live financial pulse
    stripe_intent = await StripePaymentService.create_payment_intent(
        amount=request.amount,
        currency=request.currency.lower(),
        metadata={
            "vault_token": vault_token.id,
            "checkout_session_id": allowance.get("checkout_session_id", ""),
            "merchant_id": allowance.get("merchant_id", ""),
        },
        idempotency_key=f"payment_{vault_token.id}"
    )

    now = datetime.now(UTC)

    # Create payment intent record mapped to Stripe ID
    payment_status = PaymentIntentStatus.PENDING
    if stripe_intent["status"] == "succeeded":
        payment_status = PaymentIntentStatus.COMPLETED
    elif stripe_intent["status"] == "requires_action":
        payment_status = PaymentIntentStatus.PENDING # In DB we keep it as pending until confirmed

    payment_intent = PaymentIntent(
        id=stripe_intent["id"],
        vault_token_id=vault_token.id,
        amount=request.amount,
        currency=request.currency.lower(),
        status=payment_status,
        created_at=now,
        completed_at=now if stripe_intent["status"] == "succeeded" else None,
    )

    # Mark vault token as consumed (single-use)
    vault_token.status = VaultTokenStatus.CONSUMED

    db.add(payment_intent)
    db.commit()
    db.refresh(payment_intent)

    # Map to response enum
    response_status = PaymentIntentStatusEnum.PENDING
    if stripe_intent["status"] == "succeeded":
        response_status = PaymentIntentStatusEnum.COMPLETED
    elif stripe_intent["status"] == "requires_action":
        response_status = PaymentIntentStatusEnum.REQUIRES_ACTION

    return PaymentIntentResponse(
        id=payment_intent.id,
        vault_token_id=payment_intent.vault_token_id,
        amount=payment_intent.amount,
        currency=payment_intent.currency,
        status=response_status,
        client_secret=stripe_intent.get("client_secret"),
        created_at=payment_intent.created_at,
        completed_at=payment_intent.completed_at,
    )
