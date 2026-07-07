package com.meta.wearable.retail

import android.content.Context
import android.util.Log
import com.meta.wearable.dat.camera.Stream
import com.meta.wearable.dat.camera.addStream
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.SpecificDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.display.addDisplay
import com.meta.wearable.dat.display.views.*
import com.meta.wearable.retail.glimmer.*
import com.meta.wearable.retail.ui.Product
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

class RetailSessionManager(private val context: Context) {
    private var job = Job()
    private var scope = CoroutineScope(Dispatchers.Main + job)
    private val client = OkHttpClient()
    
    private val BACKEND_WS_URL = BuildConfig.VAULTIER_BACKEND_URL.replace("https://", "wss://") + "/discovery/live"

    private var userToken: String? = null

    private var _currentSession = MutableStateFlow<DeviceSession?>(null)
    val currentSession: StateFlow<DeviceSession?> = _currentSession

    private var _display = MutableStateFlow<com.meta.wearable.dat.display.Display?>(null)
    
    private var videoStreamJob: Job? = null
    private var socket: WebSocket? = null
    
    var onAddToCartRequested: ((String) -> Unit)? = null
    var onDiscoverRequested: (() -> Unit)? = null

    fun startSession(deviceId: DeviceIdentifier, token: String) {
        userToken = token
        Wearables.createSession(SpecificDeviceSelector(deviceId)).fold(
            onSuccess = { session ->
                _currentSession.value = session
                scope.launch {
                    session.state.collectLatest { state ->
                        if (state == DeviceSessionState.STARTED) {
                            attachDisplay(session)
                            attachMultimodalStream(session)
                        }
                    }
                }
                session.start()
            },
            onFailure = { error, _ ->
                Log.e("RetailSession", "Session Creation FAILED: ${error.description}")
            }
        )
    }

    private fun attachMultimodalStream(session: DeviceSession) {
        session.addStream(StreamConfiguration(VideoQuality.MEDIUM, 24)).fold(
            onSuccess = { stream ->
                stream.start().onFailure { error, _ ->
                    Log.e("RetailSession", "Stream Start FAILED: ${error.description}")
                }
                startMultimodalBridge(stream)
            },
            onFailure = { error, _ ->
                Log.e("RetailSession", "Stream Addition FAILED: ${error.description}")
            }
        )
    }

    private fun startMultimodalBridge(stream: Stream) {
        val request = Request.Builder()
            .url("$BACKEND_WS_URL?token=$userToken")
            .addHeader("x-vaultier-internal-key", BuildConfig.VAULTIER_INTERNAL_SECRET)
            .build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val data = JSONObject(text)
                    if (data.getString("type") == "agentic_action") {
                        onAddToCartRequested?.invoke(data.getString("product_id"))
                    }
                } catch (e: Exception) { Log.e("RetailSession", "Bridge Error: ${e.message}") }
            }
        })

        videoStreamJob = scope.launch {
            stream.videoStream.collect { frame ->
                // Industrial Pulse: Real-time Base64 Encoding
                val base64Data = android.util.Base64.encodeToString(frame.data, android.util.Base64.NO_WRAP)
                
                val message = JSONObject().apply {
                    put("type", "client_content")
                    put("content", JSONObject().apply {
                        put("mime_type", "image/jpeg")
                        put("data", base64Data) // Fixed: Real frame data transmitted
                    })
                }
                socket?.send(message.toString())
            }
        }
    }

    private fun attachDisplay(session: DeviceSession) {
        session.addDisplay().fold(
            onSuccess = { display ->
                _display.value = display
                showWelcome()
            },
            onFailure = { error, _ ->
                Log.e("RetailSession", "Display Addition FAILED: ${error.description}")
            }
        )
    }

    fun stopSession() {
        videoStreamJob?.cancel()
        socket?.close(1000, "User quit")
        
        val session = _currentSession.value
        if (session != null) {
            // Industrial Handshake: Detach capabilities before terminal stop
            session.removeStream()
            session.removeDisplay()
            session.stop()
        }

        _currentSession.value = null
        _display.value = null
    }

    fun destroy() { stopSession(); job.cancel() }

    fun showWelcome() {
        val display = _display.value ?: return
        scope.launch { display.sendContent { buildWelcome(onStart = { onDiscoverRequested?.invoke() }) } }
    }

    fun showCart(products: List<Product>) {
        val display = _display.value ?: return
        scope.launch { display.sendContent { buildCart(products) } }
    }

    fun showPurchaseSuccess() {
        val display = _display.value ?: return
        scope.launch { display.sendContent { buildSuccess() } }
    }

    fun updateStreamingStatus(isOn: Boolean) {
        val display = _display.value ?: return
        scope.launch { display.sendContent { buildStreaming(isOn) } }
    }
}
