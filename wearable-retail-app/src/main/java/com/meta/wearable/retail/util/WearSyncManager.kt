package com.meta.wearable.retail.util

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.meta.wearable.retail.ui.Product
import kotlinx.coroutines.tasks.await

class WearSyncManager(
    private val context: Context,
) {
    suspend fun syncProducts(products: List<Product>) {
        try {
            val dataClient = Wearable.getDataClient(context)
            val putDataMapReq = PutDataMapRequest.create("/retail/products")

            // Generate lists for primitive types since DataMap supports String lists but not object lists natively
            val names = ArrayList<String>()
            val prices = ArrayList<String>()
            val ids = ArrayList<String>()

            for (product in products) {
                names.add(product.name)
                prices.add("$${String.format("%.2f", product.price)}")
                ids.add(product.id)
            }

            putDataMapReq.dataMap.putStringArrayList("names", names)
            putDataMapReq.dataMap.putStringArrayList("prices", prices)
            putDataMapReq.dataMap.putStringArrayList("ids", ids)
            putDataMapReq.dataMap.putLong("timestamp", System.currentTimeMillis()) // Ensure update triggers event

            val putDataReq = putDataMapReq.asPutDataRequest()
            putDataReq.setUrgent()

            val result = dataClient.putDataItem(putDataReq).await()
            Log.d("WearSync", "Successfully synced products to watch: ${result.uri}")
        } catch (e: Exception) {
            Log.e("WearSync", "Failed to sync products to watch: ${e.message}", e)
        }
    }
}
