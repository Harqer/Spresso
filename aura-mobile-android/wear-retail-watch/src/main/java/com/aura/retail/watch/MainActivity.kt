package com.aura.retail.watch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.*
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable

class MainActivity :
    ComponentActivity(),
    DataClient.OnDataChangedListener {
    private val syncedProducts = androidx.compose.runtime.mutableStateOf<List<Triple<String, String, String>>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearApp(syncedProducts.value)
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
    }

    override fun onPause() {
        super.onPause()
        Wearable.getDataClient(this).removeListener(this)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == "/retail/products") {
                val dataMapItem = DataMapItem.fromDataItem(event.dataItem)
                val dataMap = dataMapItem.dataMap
                val names = dataMap.getStringArrayList("names") ?: emptyList<String>()
                val prices = dataMap.getStringArrayList("prices") ?: emptyList<String>()
                val ids = dataMap.getStringArrayList("ids") ?: emptyList<String>()

                val products = mutableListOf<Triple<String, String, String>>()
                for (i in names.indices) {
                    val price = if (i < prices.size) prices[i] else ""
                    val id = if (i < ids.size) ids[i] else ""
                    products.add(Triple(id, names[i], price))
                }

                runOnUiThread {
                    syncedProducts.value = products
                }
            }
        }
    }
}

@Composable
fun WearApp(products: List<Triple<String, String, String>>) {
    MaterialTheme {
        AppScaffold {
            RetailScreen(products)
        }
    }
}

@Composable
fun RetailScreen(products: List<Triple<String, String, String>>) {
    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(
        scrollState = columnState,
    ) { contentPadding ->
        TransformingLazyColumn(
            state = columnState,
            contentPadding = contentPadding,
        ) {
            item {
                ListHeader(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(ListHeaderDefaults.minimumTopListContentPadding),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Text(text = "Aura Retail")
                }
            }

            val displayProducts =
                if (products.isEmpty()) {
                    listOf(Triple("", "Empty Cart", ""))
                } else {
                    products
                }

            displayProducts.forEach { (id, name, price) ->
                item {
                    Button(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
                        transformation = SurfaceTransformation(transformationSpec),
                        onClick = {
                            if (id.isNotEmpty()) {
                                // Add click handler for the product
                            }
                        },
                    ) {
                        Text(
                            text = "$name - $price",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
