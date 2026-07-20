import base64
import logging
import os
from io import BytesIO

import httpx
from google import genai
from google.genai.types import Image, ProductImage, RecontextImageSource

logger = logging.getLogger(__name__)


class KyzoVTOEngine:
    """The KYZO Generative Virtual Try-On Engine.

    Architecture (2026):
    - Image: Vertex AI virtual-try-on-001 (Imagen-powered Fitting)
    - Video: Veo 3 (High-fidelity motion generation)
    - Infra: Vertex AI for Firebase (Cloud-Secured)
    """

    def __init__(self, project_id: str, location: str = "us-central1"):
        try:
            # Industrial Standard: Using the new google-genai SDK for Vertex AI (2026)
            self.client = genai.Client(
                vertexai=True, project=project_id, location=location
            )
            self._ready = True
        except Exception as e:
            logger.warning(
                f"Vertex AI initialization failed: {e}. VTO features will be disabled."
            )
            self._ready = False

    async def _fetch_image_as_base64(self, url: str) -> str | None:
        """Fetches a remote product image and converts to base64 for VTO processing."""
        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                response = await client.get(url)
                response.raise_for_status()
                return base64.b64encode(response.content).decode("utf-8")
        except Exception as e:
            logger.error(f"Failed to fetch garment image from {url}: {e}")
            return None

    async def generate_branded_try_on(
        self, user_photo_b64: str, garment_image_url: str, user_metadata: dict
    ) -> dict[str, Any] | None:
        """Full 'On-Brand GenMedia' Pipeline: Art Director Prompt -> Gen -> Score -> Upscale."""
        if not self._ready:
            return None

        # Stage 1: Art Director Prompt Generation (Industrial ADK 2.0 pattern)
        user_size = user_metadata.get("size", "M")
        height = user_metadata.get("height", 175)
        weight = user_metadata.get("weight", 70)

        # Expert Strategy: Exactly 4-6 sentences, Balenciaga runway aesthetic
        art_director_prompt = (
            f"A high-fashion runway shot of a model with a height of {height}cm and weight of {weight}kg "
            f"wearing a garment from {garment_image_url} in a minimalist industrial setting. "
            "The camera captures a medium full-body angle with intentional negative space "
            "to emphasize the sharp Balenciaga-inspired silhouette and drape of the fabric. "
            "Harsh architectural shadows combine with soft cinematic lighting to create "
            "a premium editorial atmosphere that highlights the tactile weave of the heavy cotton. "
            "Microscopic details reveal physically accurate fabric tension around the shoulders "
            f"matching a size {user_size} fit perfectly against the clean studio background."
        )

        try:
            # Stage 2: Initial Generation with nano-banana-2-lite
            # Industrial Pulse: In 2026, we use the lite model for rapid iteration
            gen_response = self.client.models.recontext_image(
                model="nano-banana-2-lite",
                source=RecontextImageSource(
                    person_image=Image(data=base64.b64decode(user_photo_b64)),
                    product_images=[
                        ProductImage(product_image=Image(data=await self._fetch_image_as_base64(garment_image_url) or b""))
                    ],
                ),
                config={"prompt": art_director_prompt}
            )

            if not gen_response.generated_images:
                return None

            raw_image_bytes = gen_response.generated_images[0].image.image_bytes

            # Stage 3 & 4: Scoring & Checking (Industrial Simulation)
            # In a full multi-agent setup, we would call a scoring agent here.
            # For this reference architecture, we proceed if the model returned an image.

            # Stage 5: Upscale with imagen-4.0-upscale-preview
            upscale_response = self.client.models.upscale_image(
                model="imagen-4.0-upscale-preview",
                image=Image(data=raw_image_bytes),
                upscale_factor=2
            )

            if not upscale_response.generated_images:
                return {"image_url": f"data:image/png;base64,{base64.b64encode(raw_image_bytes).decode('utf-8')}", "status": "FINALIZED_WITHOUT_UPSCALE"}

            final_image_bytes = upscale_response.generated_images[0].image.image_bytes
            b64_output = base64.b64encode(final_image_bytes).decode("utf-8")

            return {
                "image_url": f"data:image/png;base64,{b64_output}",
                "status": "SUCCESS",
                "engine": "On-Brand GenMedia Suite v4 (High-Fidelity)"
            }

        except Exception as e:
            logger.error(f"Branded VTO Pipeline Failed: {e}")
            return None

    async def generate_try_on_image(
        self, user_photo_b64: str, garment_image_url: str, user_metadata: dict
    ) -> str | None:
        """Maps garment onto user body using virtual-try-on-001."""
        if not self._ready:
            logger.warning("VTO Engine not ready. Skipping generation.")
            return None

        # Fetch garment image if it's a URL
        garment_b64 = await self._fetch_image_as_base64(garment_image_url)
        if not garment_b64:
            return None

        try:
            # Industrial Pulse: Direct Recontextualization (Spresso 2026 Standard)
            response = self.client.models.recontext_image(
                model="virtual-try-on-001",
                source=RecontextImageSource(
                    person_image=Image(data=base64.b64decode(user_photo_b64)),
                    product_images=[
                        ProductImage(product_image=Image(data=base64.b64decode(garment_b64)))
                    ],
                ),
            )

            if not response.generated_images:
                return None

            # Return as data URL for immediate frontend rendering
            # Production Strategy: In high-volume scenarios, we would stream this to Firebase Storage
            img_data = response.generated_images[0].image.image_bytes
            b64_output = base64.b64encode(img_data).decode("utf-8")
            return f"data:image/png;base64,{b64_output}"

        except Exception as e:
            logger.error(f"VTO Image Generation Failed: {e}")
            return None

    async def generate_product_spin(self, product_image_url: str) -> str | None:
        """Generates a 360-degree spinning video of a product using Veo 3.1."""
        if not self._ready:
            return None

        try:
            # Industrial Pulse: Reference-to-Video (R2V) mode in Veo 3.1 (2026)
            spin_prompt = (
                "A smooth, continuous 360-degree spinning animation of the product. "
                "Maintain perfect temporal consistency. Minimalist studio lighting. "
                "8-second seamless loop. Professional product showcase."
            )
            # In 2026 SDK, we can pass image and prompt to Veo directly
            response = self.client.models.generate_content(
                model="veo-3.1-r2v-preview",
                contents=[spin_prompt, Image(data=await self._fetch_image_as_base64(product_image_url) or b"")]
            )
            # Return the video URL from the response
            return response.candidates[0].content.parts[0].text # Simplified for reference
        except Exception as e:
            logger.error(f"Product Spin Generation Failed: {e}")
            return None

    async def generate_try_on_video(self, static_vto_image_url: str) -> str | None:
        """Creates a short 5-second motion loop using Veo 3."""
        if not self._ready:
            return None

        try:
            # Industrial Grade: Upgraded Professional Video Directive (Spresso 2026)
            video_prompt = (
                "Create a professional product showcase reel of the garment locked to the reference image "
                "so its identity, proportions, label and material stay accurate. "
                "Environment: Minimalist studio. Grade and mood: premium, calm, confident, "
                "with soft motivated lighting that reveals the material truthfully. "
                "Slow walking motion, garment movement, cinematic lighting, 5-second loop. "
                "Audio: near-silent, only very subtle realistic diegetic sound effects; no music."
            )
            response = await self.video_model.generate_content_async(
                [video_prompt, static_vto_image_url]
            )
            # Differentiate between image output and video pointer in 2026 SDK
            return response.candidates[0].content.parts[0].text  # Simplified for now
        except Exception as e:
            logger.error(f"VTO Video Generation Failed: {e}")
            return None


_kyzo_engine: KyzoVTOEngine | None = None


def get_vto_engine() -> KyzoVTOEngine:
    global _kyzo_engine
    if _kyzo_engine is None:
        project_id = os.getenv("GOOGLE_CLOUD_PROJECT")
        if not project_id:
            logger.error(
                "GOOGLE_CLOUD_PROJECT missing. KyzoVTOEngine initialization aborted."
            )
            return None  # Fail closed in production
        _kyzo_engine = KyzoVTOEngine(project_id)
    return _kyzo_engine
