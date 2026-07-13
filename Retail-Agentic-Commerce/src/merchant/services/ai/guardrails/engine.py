import logging
import os
from typing import Any

import httpx
import sentry_sdk
import yaml

logger = logging.getLogger(__name__)


class SecurityViolationError(Exception):
    """Exception raised when a prompt violates AI security policies."""

    def __init__(self, reason: str, details: dict[str, Any] | None = None):
        self.reason = reason
        self.details = details or {}
        super().__init__(f"Security Violation: {reason}")


class VaultierGuardrail:
    """Architectural Guardrail Engine.

    Acts as the secure gateway between the API and the Inference Model.
    Ensures input sanitization, persona enforcement, and semantic security.
    """

    def __init__(self, policy_path: str):
        with open(policy_path) as f:
            self.policy = yaml.safe_load(f)
        self.lakera_api_key = os.getenv("LAKERA_GUARD_API_KEY")
        self.lakera_url = "https://api.lakera.ai/v1/guard"

    async def check_prompt(self, prompt: str) -> None:
        """Audits the prompt for semantic injection and jailbreaks.

        Uses Lakera Guard as a specialized classifier firewall.
        Falls back to local heuristic validation if API is unavailable.

        Args:
            prompt: The untrusted user input.

        Raises:
            SecurityViolationError: If a malicious pattern is detected.
        """
        if not self.lakera_api_key:
            logger.warning("LAKERA_GUARD_API_KEY missing. Falling back to local heuristics.")
            self._local_heuristic_audit(prompt)
            return

        try:
            async with httpx.AsyncClient(timeout=2.0) as client:
                response = await client.post(
                    self.lakera_url,
                    headers={"Authorization": f"Bearer {self.lakera_api_key}"},
                    json={"input": prompt},
                )
                response.raise_for_status()
                result = response.json()

                if result.get("flagged", False):
                    # Extract specific violation categories
                    results = result.get("results", [{}])[0]
                    categories = [k for k, v in results.get("categories", {}).items() if v]
                    reason = f"Malicious intent detected: {', '.join(categories)}"
                    logger.error(f"AI Firewall BLOCKED prompt: {reason}")
                    raise SecurityViolationError(reason, details=result)

        except httpx.HTTPError as e:
            logger.error(f"AI Firewall API Failure: {e}. Executing fail-safe audit.")
            self._local_heuristic_audit(prompt)
        except SecurityViolationError:
            raise
        except Exception as e:
            logger.error(f"Unexpected Firewall Error: {e}")
            self._local_heuristic_audit(prompt)

    def _local_heuristic_audit(self, prompt: str) -> None:
        """Local pattern-based audit for immediate fallback defense."""
        malicious_patterns = [
            "ignore all previous",
            "ignore previous instructions",
            "system prompt",
            "you are now",
            "developer mode",
            "sql injection",
            "select * from",
        ]
        prompt_lower = prompt.lower()
        for pattern in malicious_patterns:
            if pattern in prompt_lower:
                logger.error(f"Local Guardrail BLOCKED prompt pattern: {pattern}")
                raise SecurityViolationError(f"Heuristic violation: {pattern}")


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
