package com.meta.wearable.retail.ui

import android.util.Log
import com.meta.wearable.retail.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class ChatResponse(
    val intent: String,
    val productId: String?,
    val response: String,
    val vtoImageUrl: String? = null,
    val vtoVideoUrl: String? = null,
    val grid: List<Product> = emptyList(),
    val compare: List<Product> = emptyList(),
    val filters: List<String> = emptyList(),
)

class ProductRepository {
    private val backendUrl = BuildConfig.SPRESSO_BACKEND_URL

    suspend fun discoveryChat(
        message: String,
        cartItems: List<Product>,
        userToken: String,
        imageBytes: ByteArray? = null,
        localContext: String? = null,
    ): ChatResponse =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("$backendUrl/discovery/chat")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $userToken")
                if (localContext != null) {
                    conn.setRequestProperty("X-Spresso-Local-Context", localContext)
                }
                conn.doOutput = true

                val requestBody =
                    JSONObject().apply {
                        put("message", message)
                        val itemsArray = JSONArray()
                        cartItems.forEach {
                            itemsArray.put(
                                JSONObject().apply {
                                    put("product_id", it.id)
                                    put("name", it.name)
                                    put("price", (it.price * 100).toInt())
                                },
                            )
                        }
                        put("cart_items", itemsArray)

                        if (imageBytes != null) {
                            val base64Image = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
                            put("image_base64", base64Image)
                        }
                    }

                OutputStreamWriter(conn.outputStream).use { it.write(requestBody.toString()) }

                if (conn.responseCode == 201 || conn.responseCode == 200) {
                    val responseText = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(responseText)

                    val gridProducts = mutableListOf<Product>()
                    if (json.has("grid")) {
                        val gridArray = json.getJSONArray("grid")
                        for (i in 0 until gridArray.length()) {
                            gridProducts.add(parseProduct(gridArray.getJSONObject(i)))
                        }
                    }

                    val compareProducts = mutableListOf<Product>()
                    if (json.has("compare")) {
                        val compareArray = json.getJSONArray("compare")
                        for (i in 0 until compareArray.length()) {
                            compareProducts.add(parseProduct(compareArray.getJSONObject(i)))
                        }
                    }

                    val filtersList = mutableListOf<String>()
                    if (json.has("filters")) {
                        val filtersArray = json.getJSONArray("filters")
                        for (i in 0 until filtersArray.length()) {
                            filtersList.add(filtersArray.getString(i))
                        }
                    }

                    ChatResponse(
                        intent = json.getString("intent"),
                        productId = json.optString("product_id", null),
                        response = json.getString("response"),
                        vtoImageUrl = json.optString("vto_image_url", null),
                        vtoVideoUrl = json.optString("vto_video_url", null),
                        grid = gridProducts,
                        compare = compareProducts,
                        filters = filtersList,
                    )
                } else {
                    ChatResponse("CHAT", null, "I'm having trouble connecting to Spresso.")
                }
            } catch (e: Exception) {
                ChatResponse("CHAT", null, "Connection error: ${e.message}")
            }
        }

    private fun parseProduct(p: JSONObject): Product {
        val attrMap = mutableMapOf<String, String>()
        val keys = p.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key !in listOf("id", "sku", "name", "price", "image_url", "imageUrl", "category", "tagline", "matchScore")) {
                attrMap[key] = p.optString(key, "")
            }
        }

        return Product(
            id = p.optString("id", "web_" + System.currentTimeMillis()),
            sku = p.optString("sku", "VAULT-DISCOVER"),
            name = p.optString("name", p.optString("imageUrl", "Grounded Discovery")),
            price = p.optDouble("price", 0.0),
            imageUrl = p.optString("imageUrl", p.optString("image_url", "")),
            category = p.optString("category", "unclassified"),
            tagline = p.optString("tagline", ""),
            matchScore = p.optInt("matchScore", p.optInt("match_score", 98)),
            attributes = attrMap,
        )
    }

    suspend fun getRecommendations(
        userToken: String,
        query: String = "featured",
    ): List<Product> =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("$backendUrl/ai/search")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $userToken")
                conn.doOutput = true

                val requestBody =
                    JSONObject().apply {
                        put("query", query)
                    }

                OutputStreamWriter(conn.outputStream).use { it.write(requestBody.toString()) }

                if (conn.responseCode == 200) {
                    val responseText = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(responseText)
                    val results = json.getJSONArray("results")
                    val productsList = mutableListOf<Product>()
                    for (i in 0 until results.length()) {
                        val p = results.getJSONObject(i)
                        productsList.add(
                            Product(
                                id = p.getString("id"),
                                sku = p.getString("sku"),
                                name = p.getString("name"),
                                price = p.getInt("base_price").toDouble() / 100.0,
                                imageUrl = p.getString("image_url"),
                                category = p.getString("category"),
                                tagline = p.optString("tagline", ""),
                            ),
                        )
                    }
                    productsList
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e("Spresso", "Search Error: ${e.message}")
                emptyList()
            }
        }

    suspend fun createCheckoutSession(
        items: List<Product>,
        userToken: String,
        userName: String,
        userEmail: String,
    ): String? =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("$backendUrl/checkout_sessions")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $userToken")
                conn.doOutput = true

                val requestBody =
                    JSONObject().apply {
                        val itemsArray = JSONArray()
                        items.forEach {
                            itemsArray.put(
                                JSONObject().apply {
                                    put("id", it.id)
                                    put("quantity", 1)
                                },
                            )
                        }
                        put("items", itemsArray)
                        val names = userName.split(" ", limit = 2)
                        put(
                            "buyer",
                            JSONObject().apply {
                                put("first_name", names.firstOrNull() ?: "")
                                put("last_name", names.drop(1).firstOrNull() ?: "")
                                put("email", userEmail)
                            },
                        )
                        // Removing hardcoded fulfillment_address to allow Stripe to naturally collect it during the checkout flow
                    }

                OutputStreamWriter(conn.outputStream).use { it.write(requestBody.toString()) }

                if (conn.responseCode == 201 || conn.responseCode == 200) {
                    val responseText = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(responseText)
                    json.getString("id")
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("Spresso", "ACP Create Session Error: ${e.message}")
                null
            }
        }

    suspend fun completeCheckout(
        sessionId: String,
        paymentToken: String,
        userToken: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("$backendUrl/checkout_sessions/$sessionId/complete")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $userToken")
                conn.doOutput = true

                val requestBody =
                    JSONObject().apply {
                        put(
                            "payment_data",
                            JSONObject().apply {
                                put("token", paymentToken)
                                put("provider", "stripe")
                            },
                        )
                    }

                OutputStreamWriter(conn.outputStream).use { it.write(requestBody.toString()) }
                conn.responseCode == 200 || conn.responseCode == 201
            } catch (e: Exception) {
                Log.e("Spresso", "ACP Complete Error: ${e.message}")
                false
            }
        }
}
