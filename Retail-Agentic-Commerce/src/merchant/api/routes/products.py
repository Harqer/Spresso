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

"""Product lookup endpoint for the Agentic Commerce middleware."""

from typing import TypedDict

from fastapi import APIRouter, Depends, HTTPException
from sqlmodel import Session, select

from src.merchant.db.database import get_session
from src.merchant.db.models import Product

router = APIRouter(tags=["products"])


class ProductResponse(TypedDict):
    """Product response schema."""

    id: str
    sku: str
    name: str
    base_price: int
    stock_count: int
    min_margin: float
    image_url: str
    category: str
    tagline: str
    features: list[str]
    description: str


@router.get("/products", response_model=list[ProductResponse])
def get_products(
    db: Session = Depends(get_session),
) -> list[ProductResponse]:
    """Retrieve all products.

    Args:
        db: Database session (injected).

    Returns:
        List[ProductResponse]: List of product details.
    """
    products = db.exec(select(Product)).all()
    import json

    return [
        ProductResponse(
            id=p.id,
            sku=p.sku,
            name=p.name,
            base_price=p.base_price,
            stock_count=p.stock_count,
            min_margin=p.min_margin,
            image_url=p.image_url,
            category=p.category if hasattr(p, "category") else "General",
            tagline=p.tagline if hasattr(p, "tagline") else "",
            features=json.loads(p.features_json) if hasattr(p, "features_json") else [],
            description=p.description if hasattr(p, "description") else "",
        )
        for p in products
    ]


@router.get("/products/{product_id}", response_model=ProductResponse)
def get_product(
    product_id: str,
    db: Session = Depends(get_session),
) -> ProductResponse:
    """Retrieve a product by its ID.

    Args:
        product_id: The unique product identifier (e.g., "prod_1").
        db: Database session (injected).

    Returns:
        ProductResponse: Product details including price, stock, and image.

    Raises:
        HTTPException: 404 if product not found.
    """
    product = db.get(Product, product_id)
    if not product:
        raise HTTPException(status_code=404, detail=f"Product {product_id} not found")

    import json

    return ProductResponse(
        id=product.id,
        sku=product.sku,
        name=product.name,
        base_price=product.base_price,
        stock_count=product.stock_count,
        min_margin=product.min_margin,
        image_url=product.image_url,
        category=product.category if hasattr(product, "category") else "General",
        tagline=product.tagline if hasattr(product, "tagline") else "",
        features=json.loads(product.features_json)
        if hasattr(product, "features_json")
        else [],
        description=product.description if hasattr(product, "description") else "",
    )
