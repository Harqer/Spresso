# SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
# SPDX-License-Identifier: Apache-2.0

from collections.abc import Generator
from typing import Annotated

from fastapi import Depends, Header, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlmodel import Session, text

from src.merchant.config import Settings, get_settings
from src.merchant.db.database import get_engine
from src.merchant.services.firebase_auth_service import FirebaseAuthService

# Optional bearer token scheme (auto_error=False allows X-API-Key fallback)
bearer_scheme = HTTPBearer(auto_error=False)


def verify_api_key(
    settings: Annotated[Settings, Depends(get_settings)],
    bearer_credentials: Annotated[
        HTTPAuthorizationCredentials | None, Depends(bearer_scheme)
    ] = None,
    x_api_key: Annotated[str | None, Header(alias="X-API-Key")] = None,
) -> str:
    """Verify API key from Authorization header or X-API-Key header."""
    api_key: str | None = None
    if bearer_credentials is not None:
        api_key = bearer_credentials.credentials
    elif x_api_key is not None:
        api_key = x_api_key

    if api_key is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="API key is required",
            headers={"WWW-Authenticate": "Bearer"},
        )

    if api_key != settings.merchant_api_key:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Invalid API key",
        )
    return api_key


async def get_current_user(
    bearer_credentials: Annotated[
        HTTPAuthorizationCredentials | None, Depends(bearer_scheme)
    ] = None,
) -> str:
    """Enterprise Auth Dependency: Validates Firebase ID Token and returns UID."""
    if not bearer_credentials:
        raise HTTPException(status_code=401, detail="Missing Authentication Token")

    token = bearer_credentials.credentials
    user_id = await FirebaseAuthService.get_user_from_token(token)

    if not user_id:
        raise HTTPException(status_code=401, detail="Invalid or Expired Token")

    return user_id


def get_secure_session(
    user_id: Annotated[str, Depends(get_current_user)],
) -> Generator[Session, None, None]:
    """Secure DB Session: Injects verified User ID into Postgres session for RLS enforcement."""
    engine = get_engine()
    with Session(engine) as session:
        # Set the JWT claims for RLS (requesting_user_id() in setup_neon.sql)
        session.execute(
            text(f'SET LOCAL request.jwt.claims = \'{{"sub": "{user_id}"}}\'')
        )
        yield session
