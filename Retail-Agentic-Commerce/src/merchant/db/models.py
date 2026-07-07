from datetime import UTC, datetime
from enum import StrEnum
from typing import ClassVar

from sqlmodel import Field, Relationship, SQLModel


def _utc_now() -> datetime:
    return datetime.now(UTC)


class CheckoutStatus(StrEnum):
    """Checkout session status values."""

    NOT_READY_FOR_PAYMENT = "not_ready_for_payment"
    READY_FOR_PAYMENT = "ready_for_payment"
    COMPLETED = "completed"
    CANCELED = "canceled"


class AgentInvocationChannel(StrEnum):
    """Channels through which an agent can be invoked."""

    ACP = "acp"
    APPS_SDK = "apps_sdk"
    UCP = "ucp"


class AgentInvocationStatus(StrEnum):
    """Outcome status of an agent invocation."""

    SUCCESS = "success"
    ERROR_INTERNAL = "error_internal"
    ERROR_UPSTREAM = "error_upstream"
    ERROR_TIMEOUT = "error_timeout"


class RecommendationAttributionEventType(StrEnum):
    """Types of recommendation attribution events."""

    IMPRESSION = "impression"
    CLICK = "click"
    PURCHASE = "purchase"


class Customer(SQLModel, table=True):
    id: str = Field(primary_key=True)  # Clerk User ID
    email: str = Field(unique=True, index=True)
    name: str
    tier: str = Field(default="free")  # Administered via Clerk
    created_at: datetime = Field(default_factory=_utc_now)

    browse_history: list["BrowseHistory"] = Relationship(
        back_populates="customer",
        sa_relationship_kwargs={"cascade": "all, delete-orphan"},
    )
    orders: list["Order"] = Relationship(back_populates="customer")


class Product(SQLModel, table=True):
    id: str = Field(primary_key=True)
    sku: str = Field(unique=True, index=True)
    name: str
    base_price: int
    stock_count: int
    min_margin: float
    image_url: str
    category: str = Field(index=True)
    tagline: str = Field(default="")
    features_json: str = Field(default="[]")
    lifecycle: str = Field(default="mature")
    demand_velocity: str = Field(default="flat")
    description: str = Field(default="")

    competitor_prices: list["CompetitorPrice"] = Relationship(
        back_populates="product",
        sa_relationship_kwargs={"cascade": "all, delete-orphan"},
    )
    browse_views: list["BrowseHistory"] = Relationship(back_populates="product")


class CheckoutSession(SQLModel, table=True):
    """Checkout session database model."""

    __tablename__: ClassVar[str] = "checkout_session"

    id: str = Field(primary_key=True)
    protocol: str = Field(default="acp")
    status: CheckoutStatus = Field(default=CheckoutStatus.NOT_READY_FOR_PAYMENT)
    currency: str = Field(default="USD")
    locale: str = Field(default="en-US")
    line_items_json: str = Field(default="[]")
    buyer_json: str | None = Field(default=None)
    fulfillment_address_json: str | None = Field(default=None)
    fulfillment_options_json: str = Field(default="[]")
    selected_fulfillment_option_id: str | None = Field(default=None)
    totals_json: str = Field(default="{}")
    order_json: str | None = Field(default=None)
    messages_json: str = Field(default="[]")
    links_json: str = Field(default="[]")
    metadata_json: str = Field(default="{}")
    created_at: datetime = Field(default_factory=_utc_now)
    updated_at: datetime = Field(default_factory=_utc_now)


class Order(SQLModel, table=True):
    id: str = Field(primary_key=True)
    customer_id: str = Field(foreign_key="customer.id", index=True)
    total_cents: int
    status: str = Field(default="pending")
    created_at: datetime = Field(default_factory=_utc_now)

    customer: Customer = Relationship(back_populates="orders")
    items: list["OrderItem"] = Relationship(back_populates="order")


class OrderItem(SQLModel, table=True):
    __tablename__: ClassVar[str] = "order_item"
    id: int | None = Field(default=None, primary_key=True)
    order_id: str = Field(foreign_key="order.id", index=True)
    product_id: str = Field(foreign_key="product.id")
    price_cents: int
    quantity: int = Field(default=1)

    order: Order = Relationship(back_populates="items")


class CompetitorPrice(SQLModel, table=True):
    __tablename__: ClassVar[str] = "competitor_price"
    id: int | None = Field(default=None, primary_key=True)
    product_id: str = Field(foreign_key="product.id", index=True)
    retailer_name: str
    price: int
    updated_at: datetime = Field(default_factory=_utc_now)

    product: Product = Relationship(back_populates="competitor_prices")


class BrowseHistory(SQLModel, table=True):
    __tablename__: ClassVar[str] = "browse_history"
    id: int | None = Field(default=None, primary_key=True)
    customer_id: str = Field(foreign_key="customer.id", index=True)
    category: str = Field(index=True)
    search_term: str | None = Field(default=None)
    product_id: str | None = Field(default=None, foreign_key="product.id")
    price_viewed: int = Field(default=0)
    viewed_at: datetime = Field(default_factory=_utc_now)

    customer: Customer = Relationship(back_populates="browse_history")
    product: Product = Relationship(back_populates="browse_views")


class AgentInvocationOutcome(SQLModel, table=True):
    """Model for recording the outcome of an agent invocation."""

    __tablename__: ClassVar[str] = "agent_invocation_outcome"

    id: int | None = Field(default=None, primary_key=True)
    timestamp: datetime = Field(default_factory=_utc_now, index=True)
    agent_type: str = Field(index=True)
    channel: AgentInvocationChannel
    status: AgentInvocationStatus
    latency_ms: int
    request_id: str | None = Field(default=None, index=True)
    session_id: str | None = Field(default=None, index=True)
    error_code: str | None = Field(default=None)


class RecommendationAttributionEvent(SQLModel, table=True):
    """Model for recording recommendation attribution events."""

    __tablename__: ClassVar[str] = "recommendation_attribution_event"

    id: int | None = Field(default=None, primary_key=True)
    timestamp: datetime = Field(default_factory=_utc_now, index=True)
    event_type: RecommendationAttributionEventType
    session_id: str | None = Field(default=None, index=True)
    recommendation_request_id: str | None = Field(default=None, index=True)
    product_id: str = Field(foreign_key="product.id", index=True)
    position: int | None = Field(default=None)
    order_id: str | None = Field(default=None, index=True)
    quantity: int | None = Field(default=None)
    revenue_cents: int | None = Field(default=None)
