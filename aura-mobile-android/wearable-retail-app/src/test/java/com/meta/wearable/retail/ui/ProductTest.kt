package com.meta.wearable.retail.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ProductTest {
    @Test
    fun testProductCreation() {
        val product =
            Product(
                id = "prod_1",
                sku = "VAULT-001",
                name = "Spresso Glasses",
                price = 299.99,
                imageUrl = "https://example.com/img.jpg",
                category = "Wearable",
                tagline = "The future of vision",
            )

        assertEquals("prod_1", product.id)
        assertEquals(299.99, product.price, 0.0)
        assertEquals("Spresso Glasses", product.name)
    }
}
