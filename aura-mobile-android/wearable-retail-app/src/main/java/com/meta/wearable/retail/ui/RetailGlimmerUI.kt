package com.meta.wearable.retail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*

@Composable
fun RetailGlimmerApp(
    products: List<Product>,
    onBuy: (Product) -> Unit
) {
    // Mandatory black background for additive display on glasses
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)) {
            Text(text = "Agentic Commerce", color = Color.White)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            products.firstOrNull()?.let { product ->
                ProductCard(product, onBuy)
            }
        }
    }
}

@Composable
fun ProductCard(product: Product, onBuy: (Product) -> Unit) {
    Card(
        modifier = Modifier.width(320.dp),
        colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = product.name,
                maxLines = 1,
                color = Color.White
            )
            
            Text(
                text = "$\${product.price}",
                color = Color.LightGray
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Button(
                onClick = { onBuy(product) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.ShoppingCart,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Buy Now")
            }
        }
    }
}
