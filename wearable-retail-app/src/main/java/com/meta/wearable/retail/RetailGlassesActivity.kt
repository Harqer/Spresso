package com.meta.wearable.retail

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.meta.wearable.retail.ui.RetailGlimmerApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class RetailGlassesActivity : ComponentActivity() {
    @Inject lateinit var sessionManager: RetailSessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val products by sessionManager.activeProducts.collectAsState()

            RetailGlimmerApp(
                products = products,
                onBuy = { product ->
                    Log.d("GlassesUI", "Buying product: ${product.name}")
                    sessionManager.onAddToCartRequested?.invoke(product.id)
                },
            )
        }
    }
}
