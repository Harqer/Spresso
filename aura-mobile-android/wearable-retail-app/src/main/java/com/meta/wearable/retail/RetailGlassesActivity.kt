package com.meta.wearable.retail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.meta.wearable.retail.ui.Product
import com.meta.wearable.retail.ui.RetailGlimmerApp
import android.util.Log

class RetailGlassesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // In a real app, products should be passed via Intent or shared ViewModel/Repository
        val products = listOf(
            Product("1", "SKU-TEE", "Classic Blue Tee", 25.0, "", "Apparel"),
            Product("2", "SKU-JKT", "Denim Jacket", 85.0, "", "Apparel"),
            Product("3", "SKU-SNK", "Canvas Sneakers", 45.0, "", "Footwear")
        )
        
        setContent {
            RetailGlimmerApp(
                products = products,
                onBuy = { product ->
                    Log.d("GlassesUI", "Buying product: ${product.name}")
                }
            )
        }
    }
}
