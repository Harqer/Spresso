package com.meta.wearable.retail.ui

data class Product(
    val id: String,
    val sku: String,
    val name: String,
    val price: Double,
    val imageUrl: String,
    val category: String,
    val tagline: String = "",
    val matchScore: Int = 98,
    val attributes: Map<String, String> = emptyMap(),
    // The Web Origin (Agentic Acquisition)
    val sourceUrl: String? = null,
)
