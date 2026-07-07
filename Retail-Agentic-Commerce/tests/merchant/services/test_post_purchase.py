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

"""Tests for post-purchase messaging helpers."""

from src.merchant.services.post_purchase import (
    format_order_items,
)


def test_format_order_items_includes_name_and_quantity() -> None:
    items = [
        {"name": "Classic Tee", "quantity": 1},
        {"name": "Logo Hoodie", "quantity": 2},
    ]

    result = format_order_items(items)

    assert "Classic Tee (x1)" in result
    assert "Logo Hoodie (x2)" in result
