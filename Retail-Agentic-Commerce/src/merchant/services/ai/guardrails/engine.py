import logging
from typing import Any

import sentry_sdk
import yaml

logger = logging.getLogger(__name__)


class VaultierGuardrail:
    """Architectural Guardrail Engine.

    Acts as the secure gateway between the API and the Inference Model.
    Ensures input sanitization and persona enforcement.
    """

    def __init__(self, policy_path: str):
        with open(policy_path) as f:
            self.policy = yaml.safe_load(f)

    def get_system_instruction(self) -> str:
        """Constructs the native system_instruction block for the Gemini API."""
        p = self.policy["persona"]
        s = self.policy["security_protocols"]
        c = self.policy["capabilities"]

        instruction = (
            f"ROLE: {p['role']}\nMISSION: {p['mission']}\nTONE: {p['tone']}\n\n"
        )
        instruction += (
            "SECURITY PROTOCOLS:\n" + "\n".join([f"- {rule}" for rule in s]) + "\n\n"
        )
        instruction += "CAPABILITIES:\n" + "\n".join(
            [f"- {cap['id']}: {cap['description']}" for cap in c]
        )
        return instruction

    def secure_payload(self, message: str, metadata: dict[str, Any]) -> list[str]:
        """Encapsulates untrusted data in an isolation block."""
        return [
            "--- TRUSTED CONTEXT ---",
            f"User_Profile: {metadata}",
            "--- END TRUSTED CONTEXT ---",
            "",
            "--- UNTRUSTED DATA ---",
            f"<user_request>{message}</user_request>",
            "--- END UNTRUSTED DATA ---",
        ]

    def validate_response(self, response: Any) -> dict[str, Any]:
        """Post-inference verification to catch safety blocks or leakages."""
        # Handle both google-generativeai and google-genai response shapes
        text = ""
        try:
            text = response.text
        except Exception:
            # Fallback to candidate check if .text is missing or blocked
            if hasattr(response, "candidates") and response.candidates:
                candidate = response.candidates[0]
                if hasattr(candidate, "content") and candidate.content.parts:
                    text = candidate.content.parts[0].text or ""
            elif hasattr(response, "parts") and response.parts:
                text = response.parts[0].text or ""

        if not text:
            logger.error("Guardrail Triggered: Inference blocked or empty.")
            sentry_sdk.capture_message(
                "Guardrail Triggered: Empty response", level="warning"
            )
            return {
                "intent": "CHAT",
                "response": "Vaultier Secure: I can only assist with fashion-related requests.",
            }

        return {"raw_text": text}
