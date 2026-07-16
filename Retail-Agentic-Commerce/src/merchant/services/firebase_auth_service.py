import logging
from typing import Any

import firebase_admin
from firebase_admin import auth

logger = logging.getLogger(__name__)

# Initialize Firebase Admin SDK
# In production, this expects GOOGLE_APPLICATION_CREDENTIALS env var pointing to the service account JSON
try:
    if not firebase_admin._apps:
        # If credentials are not provided via env var, it will attempt to use default credentials
        firebase_admin.initialize_app()
except Exception as e:
    logger.warning(
        f"Firebase Admin initialization warning: {e}. Ensure GOOGLE_APPLICATION_CREDENTIALS is set for production."
    )


class FirebaseAuthService:
    """Production-grade Firebase identity verification service."""

    @classmethod
    async def get_user_from_token(cls, id_token: str) -> str | None:
        """Verifies the ID token and returns the user's UID."""
        try:
            decoded_token = auth.verify_id_token(id_token)
            return decoded_token.get("uid")
        except Exception as e:
            logger.error(f"Firebase Token Verification Failure: {str(e)}")
            return None

    @classmethod
    async def verify_session(cls, id_token: str) -> bool:
        """Verifies if the provided token is a valid Firebase session."""
        uid = await cls.get_user_from_token(id_token)
        return uid is not None

    @classmethod
    async def get_user_data_from_token(cls, id_token: str) -> dict[str, Any] | None:
        """Verifies the ID token and returns the full user data claims."""
        try:
            return auth.verify_id_token(id_token)
        except Exception as e:
            logger.error(f"Firebase Data Retrieval Failure: {str(e)}")
            return None
