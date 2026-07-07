import logging
import os

import httpx

logger = logging.getLogger(__name__)


class HiggsfieldEngine:
    """Industrial Higgsfield-1 Motion Engine for Vaultier VTO."""

    def __init__(self):
        self.api_key = os.getenv("HIGGSFIELD_API_KEY")
        self.base_url = "https://api.higgsfield.ai/v1"
        self.client = httpx.AsyncClient(timeout=30.0)

    async def generate_motion_fit(self, image_url: str, prompt: str) -> str | None:
        """
        Generates a 2026-standard Motion VTO using Higgsfield-1.
        Converts a static product shot into a walking/turning motion video.
        """
        if not self.api_key:
            logger.warning("HIGGSFIELD_API_KEY missing. Falling back to static VTO.")
            return None

        try:
            motion_desc = (
                f"Professional fashion model walking in {prompt}, "
                "4k, cinematic lighting"
            )
            payload = {
                "model": "higgsfield-1-motion",
                "input_image": image_url,
                "motion_prompt": motion_desc,
                "duration": 5,
                "aspect_ratio": "9:16",
            }

            response = await self.client.post(
                f"{self.base_url}/generate",
                headers={"Authorization": f"Bearer {self.api_key}"},
                json=payload,
            )

            if response.status_code == 202:
                data = response.json()
                return data.get("video_url")
        except Exception as e:
            logger.error(f"Higgsfield Generation Failed: {e}")
        return None
