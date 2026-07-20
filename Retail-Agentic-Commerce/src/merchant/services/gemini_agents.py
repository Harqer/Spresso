from __future__ import annotations

import asyncio
import base64
import hashlib
import json
import logging
import os
from typing import Any

import genkit
from genkit import Part
from genkit.evaluator import (
    BaseDataPoint,
    Details,
    EvalFnResponse,
    EvalStatusEnum,
    Score,
)
from genkit.plugins.google_genai import GoogleAI
from pydantic import BaseModel, Field
from sqlmodel import select
from upstash_redis import Redis

from src.merchant.config import get_settings
import re
from src.merchant.db.database import get_session
from src.merchant.db.models import Customer, Product
from src.merchant.services.ai.guardrails.engine import (
    SecurityViolationError,
    SpressoGuardrail,
)
from src.merchant.services.ai.vto_service import get_vto_engine

logger = logging.getLogger(__name__)
settings = get_settings()

# --- GENKIT INDUSTRIAL SCHEMAS ---


class ProductDiscovery(BaseModel):
    id: str = Field(description="Unique product ID")
    name: str = Field(description="Human-readable product name")
    price: float = Field(description="Price in USD")
    imageUrl: str = Field(description="URL to product image")
    description: str = Field(description="Concise procurement context")


class IntentResult(BaseModel):
    intent: str = Field(description="Action intent: SEARCH, VTO, ADD_TO_CART, CHAT")
    response: str = Field(description="Natural language response to the shopper")
    product_id: str | None = None
    price_cents: int | None = None
    style: str | None = None
    filters: list[str] = Field(default_factory=list)
    grid: list[ProductDiscovery] = Field(default_factory=list)
    compare: list[dict[str, Any]] = Field(default_factory=list)
    vto_image_url: str | None = None
    vto_video_url: str | None = None


# --- INITIALIZE GENKIT PULSE ---
# Industrial Standard: Secrets injected from Infisical into GEMINI_API_KEY
ai = genkit.Genkit(plugins=[GoogleAI(api_key=os.getenv("GEMINI_API_KEY"))])

# --- INDUSTRIAL EVALUATORS ---


async def intent_match_eval(
    datapoint: BaseDataPoint, _options: dict | None = None
) -> EvalFnResponse:
    """Industrial Evaluator: Verifies intent alignment with reference expectation."""
    output = datapoint.output if isinstance(datapoint.output, dict) else {}
    reference = datapoint.reference if isinstance(datapoint.reference, dict) else {}

    expected_intent = reference.get("intent")
    actual_intent = output.get("intent")

    match = expected_intent == actual_intent
    return EvalFnResponse(
        test_case_id=datapoint.test_case_id or "",
        evaluation=Score(
            score=1.0 if match else 0.0,
            status=EvalStatusEnum.PASS if match else EvalStatusEnum.FAIL,
            details=Details(
                reasoning=f"Expected {expected_intent}, got {actual_intent}"
            ),
        ),
    )


async def semantic_vibe_eval(
    datapoint: BaseDataPoint, _options: dict | None = None
) -> EvalFnResponse:
    """Industrial Evaluator: Uses text-embedding-004 to audit response 'vibe' consistency."""
    output_text = ""
    if isinstance(datapoint.output, dict):
        output_text = str(datapoint.output.get("response", ""))
    else:
        output_text = str(datapoint.output)

    reference_text = str(datapoint.reference or "")
    if not reference_text or not output_text:
        return EvalFnResponse(
            test_case_id=datapoint.test_case_id or "",
            evaluation=Score(
                score=0.0,
                status=EvalStatusEnum.FAIL,
                details=Details(reasoning="Missing output or reference text"),
            ),
        )

    # 1. Embed both texts
    # We use ai.embed for each to get vectors
    res1 = await ai.embed(embedder="googleai/text-embedding-004", content=output_text)
    res2 = await ai.embed(
        embedder="googleai/text-embedding-004", content=reference_text
    )

    v1 = res1[0].embedding
    v2 = res2[0].embedding

    # 2. Cosine Similarity (Pure Python Pulse)
    dot_product = sum(a * b for a, b in zip(v1, v2, strict=False))
    norm_v1 = sum(a * a for a in v1) ** 0.5
    norm_v2 = sum(b * b for b in v2) ** 0.5
    similarity = (
        dot_product / (norm_v1 * norm_v2) if norm_v1 > 0 and norm_v2 > 0 else 0.0
    )

    return EvalFnResponse(
        test_case_id=datapoint.test_case_id or "",
        evaluation=Score(
            score=similarity,
            status=EvalStatusEnum.PASS if similarity >= 0.85 else EvalStatusEnum.FAIL,
            details=Details(
                reasoning=f"Semantic similarity: {similarity:.4f}. Reference: '{reference_text[:50]}...'"
            ),
        ),
    )


ai.define_evaluator(
    name="spresso/intent_match",
    display_name="Intent Alignment",
    definition="Checks if the predicted intent matches the ground truth.",
    fn=intent_match_eval,
)

ai.define_evaluator(
    name="spresso/semantic_vibe",
    display_name="Semantic Vibe Audit",
    definition="Uses embeddings to verify brand alignment and response consistency.",
    fn=semantic_vibe_eval,
)

# --- GENKIT NATIVE TOOLS ---


@ai.tool(
    name="search_products",
    description="Search the live product catalog for fashion, home, and tech items.",
)
async def search_products(query: str) -> list[ProductDiscovery]:
    """Industrial Standard: Fetching real data from the Neon Backend."""

    async def do_db_query() -> list[ProductDiscovery]:
        with next(get_session()) as session:
            statement = select(Product).where(
                Product.name.contains(query)
                | Product.category.contains(query)
                | Product.description.contains(query)
            )
            products = session.exec(statement).all()

            return [
                ProductDiscovery(
                    id=p.id,
                    name=p.name,
                    price=float(p.base_price) / 100.0,
                    imageUrl=p.image_url,
                    description=p.description
                    or p.tagline
                    or "Premium quality selection.",
                )
                for p in products
            ]

    # Traceable Step: DB Search
    return await ai.run(f"db-search-{query}", do_db_query)


def map_dimensions_to_size(height: float, weight: float) -> str:
    """Maps user height (cm) and weight (kg) to a standard apparel size."""
    # Simplified industrial sizing logic for Spresso 2026
    bmi = weight / ((height / 100) ** 2)
    if height < 165:
        return "S" if bmi < 22 else "M"
    elif height < 180:
        if bmi < 20: return "S"
        if bmi < 25: return "M"
        return "L"
    else:
        return "L" if bmi < 24 else "XL"


class GeminiAgentService:
    """Industrial Genkit Flow-based AI Fashion Cortex."""

    def __init__(self):
        self.model = ai.model("googleai/gemini-2.0-flash")
        self.vto_engine = get_vto_engine()
        self.redis = Redis(
            url=os.getenv("UPSTASH_REDIS_REST_URL", ""),
            token=os.getenv("UPSTASH_REDIS_REST_TOKEN", ""),
        )
        self.guardrail = SpressoGuardrail(settings.spresso_policy_path)
        self.ingestion_queue = asyncio.Queue()
        self._ingestion_task = None

    @ai.flow(name="discovery_flow")
    async def discovery_flow(self, params: dict[str, Any]) -> IntentResult:
        """Production Discovery Flow: Hardened with Dotprompt, real Guardrails, and Grounding."""
        user_message = params["user_message"]
        user_metadata = params.get("user_metadata", {})
        user_id = user_metadata.get("user_id", "anon")
        user_tier = user_metadata.get("user_tier", "free")
        current_cart = params.get("current_cart", [])
        image_data = params.get("image_data")

        # Industrial Strategy: Active Profiling (Height/Weight parsing)
        profile_match = re.search(r"(\d+)\s*cm.*(\d+)\s*kg|(\d+)\s*kg.*(\d+)\s*cm", user_message, re.IGNORECASE)
        if profile_match:
            h = float(profile_match.group(1) or profile_match.group(4))
            w = float(profile_match.group(2) or profile_match.group(3))
            with next(get_session()) as session:
                customer = session.get(Customer, user_id)
                if customer:
                    customer.height = h
                    customer.weight = w
                    session.add(customer)
                    session.commit()
                    logger.info(f"Saved persistent profile for user {user_id}: {h}cm, {w}kg")

        # --- AI FIREWALL CHECK (Semantic Security) ---
        try:
            await self.guardrail.check_prompt(user_message)
        except SecurityViolationError as e:
            logger.warning(f"AI Firewall BLOCKED request: {e}")
            return IntentResult(
                intent="CHAT",
                response="Spresso Secure: Your request was flagged as a potential security risk. Please keep queries focused on fashion discovery.",
            )

        # --- CACHE CHECK (Industrial Optimization) ---
        cache_key = None
        if self.redis:
            # Hash message + image + cart for uniqueness
            payload_str = f"{user_message}:{image_data}:{json.dumps(current_cart)}"
            payload_hash = hashlib.sha256(payload_str.encode()).hexdigest()
            cache_key = f"spresso:inference_cache:{payload_hash}"
            try:
                cached = self.redis.get(cache_key)
                if cached:
                    logger.debug(f"Inference Cache HIT: {cache_key}")
                    return IntentResult.model_validate(json.loads(cached))
            except Exception as e:
                logger.error(f"Cache Retrieval Error: {e}")

        # 1. Load Dotprompt (Architectural Separation of Concerns)
        discovery_prompt = ai.prompt("discovery")

        # 2. Multimodal Payload (Real Visual Heartbeat)
        prompt_parts = []
        if image_data:
            try:
                img_bytes = (
                    base64.b64decode(image_data)
                    if isinstance(image_data, str)
                    else image_data
                )
                prompt_parts.append(
                    Part.from_media(data=img_bytes, content_type="image/jpeg")
                )
            except Exception:
                logger.warning("Visual Heartbeat data corruption detected.")

        # 3. Industrial Model Pulse
        response = await discovery_prompt(
            input={
                "user_message": user_message,
                "user_tier": user_tier,
                "current_cart": json.dumps(current_cart) if current_cart else "Empty",
            },
            prompt_parts=prompt_parts if prompt_parts else None,
            tools=[search_products],
            config={
                "google_search_retrieval": True,
            },
        )

        result_data = response.output
        if not result_data:
            raise ValueError(
                "Agentic Reasoning Failed: Model output was empty or invalid."
            )

        result = IntentResult.model_validate(result_data)

        # 4. Commercial Pulse: Real Generative VTO Pipeline (Spresso 2026)
        if result.intent == "VTO":
            if user_tier == "free":
                result.intent = "CHAT"
                result.response = "Motion Virtual Try-On is a premium feature. Join the Creator tier to see this in action."
            elif result.product_id:
                # Production Strategy: Profile-Aware VTO Orchestration
                with next(get_session()) as session:
                    product = session.get(Product, result.product_id)
                    if not product:
                        result.response = "I couldn't find details for that specific product."
                        return result

                    # Branch logic based on category (Apparel VTO vs Product Spin)
                    is_apparel = product.category.lower() in ["apparel", "clothing", "t-shirt", "shirt", "outerwear"]

                    if is_apparel:
                        customer = session.get(Customer, user_id)
                        if not customer or customer.height is None or customer.weight is None:
                            result.intent = "CHAT"
                            result.response = "I'd love to show you how that looks! To ensure a perfect fit, could you please tell me your height (cm) and weight (kg)?"
                            return result

                        logger.info(f"Triggering Art Director VTO for product {product.id}")
                        user_size = map_dimensions_to_size(customer.height, customer.weight)
                        vto_metadata = {
                            **user_metadata,
                            "size": user_size,
                            "height": customer.height,
                            "weight": customer.weight,
                            "avatar_url": customer.avatar_url
                        }

                        vto_result = await self.vto_engine.generate_branded_try_on(
                            user_photo_b64=image_data if isinstance(image_data, str) else base64.b64encode(image_data).decode("utf-8"),
                            garment_image_url=product.image_url,
                            user_metadata=vto_metadata
                        )

                        if vto_result and vto_result.get("image_url"):
                            result.vto_image_url = vto_result["image_url"]
                            result.response = f"I've orchestrated a professional runway photoshoot with you wearing the {product.name} (Size {user_size}). It captures the Balenciaga aesthetic perfectly. How do you like the fit?"
                        else:
                            result.response = "I tried to generate a branded try-on for you, but the Art Director engine is currently calibrating. I can still help you with product details!"
                    else:
                        # For non-apparel (shoes, tech, home), generate a 360 spin
                        logger.info(f"Triggering 360 Product Spin for {product.id}")
                        spin_url = await self.vto_engine.generate_product_spin(product.image_url)
                        if spin_url:
                            result.vto_video_url = spin_url
                            result.response = f"I've generated a 360° cinematic spin for the {product.name} so you can see it from every angle. How does it look?"
                        else:
                            result.response = f"I've gathered all the specs for the {product.name}. Would you like to see the customer reviews as well?"

            elif not image_data:
                result.response = "I can definitely show you how that looks! Please share or upload a photo of yourself first so I can perform the virtual try-on."

        # --- CACHE SET ---
        if cache_key and self.redis:
            try:
                # TTL 1 hour for inference results
                self.redis.setex(cache_key, 3600, json.dumps(result.model_dump()))
            except Exception as e:
                logger.error(f"Cache SET Error: {e}")

        # 5. Runtime Audit Pulse (Industrial Standard)
        # Embedding the response for live trace auditing
        async def audit_vibe():
            await ai.embed(
                embedder="googleai/text-embedding-004", content=result.response
            )

        await ai.run("runtime-vibe-audit", audit_vibe)

        return result

    async def detect_intent(
        self,
        user_message: str,
        current_cart: list[dict[str, Any]],
        user_metadata: dict[str, Any] | None = None,
        image_data: str | bytes | None = None,
    ) -> dict[str, Any]:
        """Orchestrated reasoning entry point."""

        user_metadata = user_metadata or {}
        user_id = user_metadata.get("user_id", "anonymous")

        # Rate Limiting
        if self.redis:
            user_limit_key = f"spresso:llm_limit:{user_id}"
            try:
                user_requests = self.redis.get(user_limit_key)
                if user_requests and int(user_requests) > 50:
                    return {
                        "intent": "CHAT",
                        "response": "Spresso is calibrating. Please wait.",
                    }

                self.redis.incr(user_limit_key)
                self.redis.expire(user_limit_key, 3600)
            except Exception as e:
                logger.error(f"Redis Failure: {e}")

        # Execute Flow
        params = {
            "user_message": user_message,
            "user_metadata": user_metadata,
            "current_cart": current_cart,
            "image_data": image_data,
        }
        result = await self.discovery_flow(params)

        # Expert Strategy: Silent Ingestion
        if result.grid:
            for item in result.grid:
                await self._silent_ingest(item.model_dump())

        return result.model_dump()

    async def _process_ingestion_queue(self):
        """Expert Strategy: Bulk UPSERT to Neon Postgres."""
        while True:
            batch = []
            item = await self.ingestion_queue.get()
            batch.append(item)
            while not self.ingestion_queue.empty() and len(batch) < 10:
                batch.append(await self.ingestion_queue.get())

            try:
                with next(get_session()) as session:
                    for product_data in batch:
                        product_id = (
                            product_data.get("id")
                            or f"web_{hashlib.md5(str(product_data).encode()).hexdigest()[:8]}"
                        )
                        existing = session.get(Product, product_id)
                        if not existing:
                            new_product = Product(
                                id=product_id,
                                sku=f"VAULT-{product_id[:8].upper()}",
                                name=product_data.get("name", "Grounded Discovery"),
                                base_price=int(
                                    float(product_data.get("price", 0)) * 100
                                ),
                                stock_count=99,
                                image_url=product_data.get("imageUrl", ""),
                                category=product_data.get("category", "unclassified"),
                                description=product_data.get(
                                    "description", "Discovered via Spresso Stitch Pulse"
                                ),
                            )
                            session.add(new_product)
                    session.commit()
            except Exception as e:
                logger.error(f"Ingestion Failed: {e}")
            finally:
                for _ in range(len(batch)):
                    self.ingestion_queue.task_done()

    async def _silent_ingest(self, product_data: dict[str, Any]):
        if self._ingestion_task is None:
            self._ingestion_task = asyncio.create_task(self._process_ingestion_queue())
        await self.ingestion_queue.put(product_data)

    async def describe_product(self, image_url: str) -> str:
        """Industrial Grade: Generates ultra-concise, premium product descriptions."""
        instruction = "You write ultra-concise product descriptions for a premium product-film tool. Match the tone of elite fashion brands."
        try:
            response = await ai.generate(
                model=self.model,
                prompt=f"Describe this product: {image_url}",
                system=instruction,
            )
            return response.text.strip()
        except Exception:
            return "Premium fashion discovery."

    async def generate_shipping_message(
        self, request: dict[str, Any]
    ) -> dict[str, Any]:
        """Uses Gemini to generate personalized, multilingual shipping messages."""
        try:
            response = await ai.generate(
                model=self.model,
                prompt=f"Generate a shipping update message for this request: {request}",
            )
            return (
                json.loads(response.text)
                if response.text
                else {"message": "Order is moving."}
            )
        except Exception:
            return {"message": "Spresso is tracking your order."}


_gemini_service: GeminiAgentService | None = None


def get_gemini_service() -> GeminiAgentService:
    global _gemini_service
    if _gemini_service is None:
        _gemini_service = GeminiAgentService()
    return _gemini_service
