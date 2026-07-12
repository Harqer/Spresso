package com.meta.wearable.retail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.xr.glimmer.Card
import androidx.xr.glimmer.GlimmerTheme
import androidx.xr.glimmer.Icon
import androidx.xr.glimmer.Text

@Composable
fun RetailGlimmerApp(
    products: List<Product>,
    onBuy: (Product) -> Unit,
) {
    GlimmerTheme {
        // Mandatory black background for additive display on glasses
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
        ) {
            Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)) {
                Text(text = "Vaultier Agentic Commerce")
            }

            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 40.dp)
                        .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                products.firstOrNull()?.let { product ->
                    ProductCard(product, onBuy)
                }
            }
        }
    }
}

@Composable
fun ProductCard(
    product: Product,
    onBuy: (Product) -> Unit,
) {
    Card(
        onClick = { onBuy(product) },
        modifier = Modifier.width(320.dp),
        title = { Text(text = product.name) },
        subtitle = { Text(text = "$\${product.price}") },
    ) {
        // Main content of the card
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.ShoppingCart,
                contentDescription = null,
            )
            Text(text = "Tap to Purchase")
        }
    }
}
