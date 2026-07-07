import random
import uuid

from locust import HttpUser, between, events, task


class VaultierScaleUser(HttpUser):
    """
    Industrial Load Tester for Vaultier Global Commerce.
    Simulates high-fidelity user journeys at a scale of billions.
    """

    wait_time = between(1, 5)

    def on_start(self):
        """Initialize session with industrial identity pulse."""
        self.user_id = f"scale_user_{uuid.uuid4().hex[:8]}"
        self.auth_header = {
            "Authorization": "Bearer ***REDACTED_TEST_TOKEN***"
        }  # Hydrated via Infisical in CI
        self.cart = []

    @task(3)
    def discovery_chat(self):
        """Simulates AI-intensive visual and text reasoning."""
        queries = [
            "Find me a biomorphic chrome jacket.",
            "Complete this look with high-grain leather boots.",
            "Price arbitrage for the Vaultier Horizon glasses.",
            "Show me the match score for biomorphic silhouettes.",
        ]
        payload = {
            "message": random.choice(queries),
            "cart_items": self.cart,
            "metadata": {"style": "chrome_glassium", "device": "rayban_meta"},
        }
        with self.client.post(
            "/discovery/chat",
            json=payload,
            headers=self.auth_header,
            catch_response=True,
        ) as response:
            if response.status_code == 200:
                data = response.json()
                if "product_id" in data and data["product_id"]:
                    # Simulate user interest in discovered item
                    self.cart.append({"id": data["product_id"], "price": 299.99})
                response.success()
            else:
                response.failure(f"Discovery Failed: {response.status_code}")

    @task(1)
    def complete_transaction(self):
        """Simulates the final high-trust financial gate."""
        if not self.cart:
            return

        # 1. Create Stripe Intent (Agentic)
        total_cents = int(sum(item["price"] for item in self.cart) * 100)
        intent_payload = {"amount_cents": total_cents, "product_id": self.cart[0]["id"]}

        with self.client.post(
            "/orders/create-payment-intent",
            json=intent_payload,
            headers=self.auth_header,
            catch_response=True,
        ) as response:
            if response.status_code != 200:
                response.failure("Stripe Intent Creation Failed")
                return

            intent_id = response.json().get("id")

            # 2. Finalize Order (Industrial Persistence)
            order_payload = {
                "items": self.cart,
                "total": sum(item["price"] for item in self.cart),
                "payment_intent_id": intent_id,
            }

            with self.client.post(
                "/orders/complete",
                json=order_payload,
                headers=self.auth_header,
                catch_response=True,
            ) as order_response:
                if order_response.status_code in [200, 201]:
                    self.cart = []  # Transaction complete
                    order_response.success()
                else:
                    order_response.failure("Order Completion Failed")

    @task(5)
    def health_pulse(self):
        """Simulates background heartbeat monitoring."""
        self.client.get("/health", headers=self.auth_header)


@events.init_command_line_parser.add_listener
def _(parser):
    parser.add_argument(
        "--vibe",
        type=str,
        env_var="VAULTIER_VIBE",
        default="industrial",
        help="The design vibe for the load test logs.",
    )


@events.test_start.add_listener
def on_test_start(environment, **_kwargs):
    print("--- VAULTIER GLOBAL SCALE: COMMENCING INDUSTRIAL LOAD TEST ---")
    print(f"Targeting: {environment.host}")
    print("Zero-Mock Policy: ACTIVE")


@events.test_stop.add_listener
def on_test_stop(_environment, **_kwargs):
    print("--- VAULTIER GLOBAL SCALE: LOAD TEST COMPLETE ---")
