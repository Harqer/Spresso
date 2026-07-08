package com.meta.wearable.retail.ui

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.style.rememberStyleState
import com.meta.wearable.retail.ui.theme.VaultierTheme
import com.meta.wearable.retail.BuildConfig
import com.meta.wearable.retail.RetailSessionManager
import com.meta.wearable.retail.util.GeminiNanoBanana2
import kotlinx.coroutines.launch
import java.io.InputStream

data class ChatUiMessage(
    val role: String,
    val text: String,
    val vtoImageUrl: String? = null,
    val vtoVideoUrl: String? = null,
    val grid: List<Product> = emptyList(),
    val compare: List<Product> = emptyList(),
    val filters: List<String> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RetailMobileApp(
    userToken: String,
    sessionManager: RetailSessionManager,
    initialImageUri: Uri? = null,
    onCompletePurchase: (List<Product>, () -> Unit) -> Unit,
    onRequestGalleryAccess: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val repository = remember { ProductRepository() }
    
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var cartItems by remember { mutableStateOf<List<Product>>(emptyList()) }
    var showCheckout by remember { mutableStateOf(false) }
    var isPurchasing by remember { mutableStateOf(false) }
    var chatMessages by remember { mutableStateOf(listOf(
        ChatUiMessage("Vaultier", "Welcome to Vaultier. I can identify garments from your screenshots or gallery. Try sharing a look!")
    )) }
    var isThinking by remember { mutableStateOf(false) }

    // 2026 Privacy Standard: Using Photo Picker (No Permissions Required)
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                isThinking = true
                processImageUri(it, context, userToken, repository) { msg ->
                    chatMessages = chatMessages + msg
                    isThinking = false
                }
            }
        }
    }

    // Handle Shared Screenshot or Image on Launch
    LaunchedEffect(initialImageUri) {
        initialImageUri?.let {
            chatMessages = chatMessages + ChatUiMessage("User", "[Analyzing Shared Screenshot]")
            isThinking = true
            processImageUri(it, context, userToken, repository) { msg ->
                chatMessages = chatMessages + msg
                isThinking = false
            }
        }
    }

    // 2026 industrial Standard: Gemini Nano (Banana 2) On-Device Reasoning
    val nanoEngine = remember { GeminiNanoBanana2(context) }
    
    // Initial Load: Fetch real products from the backend (Industrial Data)
    LaunchedEffect(Unit) {
        products = repository.getRecommendations(userToken, "featured")
        nanoEngine.warmUp()
    }

    // HARDENED: Subscribe to Glasses Hardware Callbacks
    DisposableEffect(sessionManager) {
        sessionManager.onAddToCartRequested = { productId ->
            scope.launch {
                val product = products.find { it.id == productId }
                if (product != null && !cartItems.contains(product)) {
                    cartItems = cartItems + product
                    chatMessages = chatMessages + ChatUiMessage("Vaultier", "I've added the ${product.name} to your cart as requested via your glasses.")
                }
            }
        }
        
        sessionManager.onDiscoverRequested = {
            chatMessages = chatMessages + ChatUiMessage("User", "[Initiating Discovery from Glasses]")
            // Logic to trigger real-time search
        }

        onDispose {
            sessionManager.onAddToCartRequested = null
            sessionManager.onDiscoverRequested = null
        }
    }

    if (showCheckout) {
        VaultierTheme {
            CheckoutScreen(
                items = cartItems,
                onBack = { if (!isPurchasing) showCheckout = false },
                isProcessing = isPurchasing,
                onComplete = {
                    onCompletePurchase(cartItems) {
                        scope.launch {
                            isPurchasing = true
                            // 1. Create ACP Checkout Session
                            val sessionId = repository.createCheckoutSession(cartItems, userToken)
                            
                            if (sessionId != null) {
                                // 2. Complete with payment token (verified by backend)
                                val success = repository.completeCheckout(sessionId, "pi_simulated_token", userToken)
                                if (success) {
                                    cartItems = emptyList()
                                    chatMessages = chatMessages + ChatUiMessage("Vaultier", "Purchase Verified. Your objects are being prepared.")
                                    showCheckout = false
                                    sessionManager.showPurchaseSuccess()
                                }
                            }
                            isPurchasing = false
                        }
                    }
                }
            )
        }
    } else {
        VaultierTheme {
            Scaffold(
                topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Vaultier", fontWeight = FontWeight.Light, letterSpacing = 2.sp) },
                    actions = {
                        BadgedBox(
                            badge = {
                                if (cartItems.isNotEmpty()) {
                                    Badge { Text(cartItems.size.toString()) }
                                }
                            }
                        ) {
                            IconButton(onClick = { if (cartItems.isNotEmpty()) showCheckout = true }) {
                                Icon(Icons.Default.ShoppingCart, contentDescription = "Cart")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color(0xFFF5F2EB)
                    )
                )
            },
            containerColor = Color(0xFFF5F2EB)
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                // 1. Chat Discovery Area
                Box(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        chatMessages.forEach { msg ->
                            ChatBubble(msg.role, msg.text)
                            
                            // 1. FILTER CHIPS (STITCH STYLE)
                            if (msg.filters.isNotEmpty()) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    items(msg.filters) { filter ->
                                        Surface(
                                            color = Color.White,
                                            border = BorderStroke(1.dp, Color(0xFFD6D1C7)),
                                            shape = CircleShape,
                                            modifier = Modifier.clickable { /* logic */ }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = filter,
                                                    style = VaultierTheme.typography.labelSmall,
                                                    color = Color(0xFF2C2A26)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            // 2. SAME VIBE GRID (STITCH STYLE)
                            if (msg.grid.isNotEmpty()) {
                                Text("🔥 Closest Matches (same vibe)", style = VaultierTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2C2A26))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(msg.grid) { product ->
                                        Card(
                                            modifier = Modifier.width(200.dp),
                                            shape = VaultierTheme.shapes.large, // 24px via theme
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                        ) {
                                            Column {
                                                Box(modifier = Modifier.height(150.dp)) {
                                                    AsyncImage(
                                                        model = product.imageUrl,
                                                        contentDescription = product.name,
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                    Surface(
                                                        modifier = Modifier.padding(8.dp).align(Alignment.TopStart),
                                                        color = Color(0xFF2C2A26).copy(alpha = 0.9f),
                                                        shape = VaultierTheme.shapes.small
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                                                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                                                            Spacer(Modifier.width(4.dp))
                                                            Text("WEB DISCOVERY", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
                                                        }
                                                    }
                                                }
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                                        Text(product.name, style = VaultierTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                                        Text("$${product.price}", style = VaultierTheme.typography.titleSmall)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 3. COMPARISON TABLE (STITCH STYLE)
                            if (msg.compare.isNotEmpty()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = VaultierTheme.shapes.large,
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    border = BorderStroke(1.dp, Color(0xFFEBE7DE))
                                ) {
                                    Column {
                                        Text("Feature Comparison", modifier = Modifier.padding(12.dp), style = VaultierTheme.typography.titleSmall, fontWeight = FontWeight.Light)
                                        HorizontalDivider(color = Color(0xFFF5F2EB))
                                        
                                        val attributeKeys = msg.compare.first().attributes.keys.toList()
                                        
                                        LazyRow(modifier = Modifier.fillMaxWidth()) {
                                            item {
                                                Column(modifier = Modifier.width(100.dp).background(Color(0xFFF5F2EB).copy(alpha = 0.3f))) {
                                                    attributeKeys.forEach { key ->
                                                        Text(key, modifier = Modifier.padding(12.dp), style = VaultierTheme.typography.labelSmall, color = Color.Gray)
                                                        HorizontalDivider(color = Color(0xFFF5F2EB))
                                                    }
                                                }
                                            }
                                            items(msg.compare) { p ->
                                                Column(modifier = Modifier.width(150.dp).border(width = 0.5.dp, color = Color(0xFFF5F2EB))) {
                                                    attributeKeys.forEach { key ->
                                                        Text(p.attributes[key] ?: "-", modifier = Modifier.padding(12.dp), style = VaultierTheme.typography.bodySmall)
                                                        HorizontalDivider(color = Color(0xFFF5F2EB))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (msg.vtoImageUrl != null || msg.vtoVideoUrl != null) {
                                VTOPreviewCard(msg.vtoImageUrl, msg.vtoVideoUrl)
                            }
                        }
                        
                        if (isThinking) {
                            Text("Vaultier is reasoning...", style = VaultierTheme.typography.labelSmall, color = Color.Gray)
                        }
                    }
                }

                // 2. High-Fidelity Input
                Surface(
                    color = Color.White,
                    shadowElevation = 8.dp,
                    shape = VaultierTheme.shapes.medium,
                    modifier = Modifier.padding(16.dp)
                ) {
                    var textInput by remember { mutableStateOf("") }
                    TextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { Text("Search or ask Vaultier...") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isThinking,
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent
                        ),
                        leadingIcon = {
                            var showMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.Add, contentDescription = "Expansion List")
                                }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Try-On from Gallery") },
                                        onClick = { 
                                            showMenu = false
                                            galleryLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Connect Full Gallery") },
                                        onClick = { showMenu = false; onRequestGalleryAccess() }
                                    )
                                }
                            }
                        },
                        trailingIcon = {
                            IconButton(
                                enabled = textInput.length > 0 && !isThinking,
                                onClick = {
                                    val userMsg = textInput
                                    chatMessages = chatMessages + ChatUiMessage("User", userMsg)
                                    textInput = ""
                                    isThinking = true
                                    
                                    scope.launch {
                                        // Expert Strategy: Local Multimodal Inference (Banana 2)
                                        val localIntent = nanoEngine.analyzeIntent(userMsg)
                                        
                                        val response = if (localIntent.isHighConfidence) {
                                            repository.discoveryChat(userMsg, cartItems, userToken, localContext = localIntent.context)
                                        } else {
                                            repository.discoveryChat(userMsg, cartItems, userToken)
                                        }

                                        chatMessages = chatMessages + ChatUiMessage(
                                            role = "Vaultier", 
                                            text = response.response,
                                            vtoImageUrl = response.vtoImageUrl,
                                            vtoVideoUrl = response.vtoVideoUrl,
                                            grid = response.grid,
                                            compare = response.compare,
                                            filters = response.filters
                                        )

                                        // 2026 industrial Standard: Agentic Purchasing with User Confirmation
                                        if (response.intent == "PURCHASE") {
                                            val pid = response.productId
                                            if (pid != null) {
                                                val p = response.grid.find { it.id == pid } ?: products.find { it.id == pid }
                                                if (p != null && !cartItems.contains(p)) {
                                                    cartItems = cartItems + p
                                                }
                                            }
                                            showCheckout = true
                                        }

                                        isThinking = false
                                        scrollState.animateScrollTo(scrollState.maxValue)
                                    }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun VTOPreviewCard(imageUrl: String?, videoUrl: String?) {
    Card(
        modifier = Modifier.fillMaxWidth().height(400.dp).padding(vertical = 8.dp),
        shape = VaultierTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (videoUrl != null) {
                // High-Fidelity Higgsfield-1 Motion Stream
                AsyncImage(
                    model = imageUrl ?: "",
                    contentDescription = "Vaultier Motion Fit",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                }
            } else if (imageUrl != null) {
                // 2026 Nano Banana 2 Generative Fit
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Vaultier Static Fit",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            
            Surface(
                modifier = Modifier.padding(12.dp).align(Alignment.TopStart),
                color = Color.Black.copy(alpha = 0.5f),
                shape = VaultierTheme.shapes.small
            ) {
                Text("VAULTIER VTO ACTIVE", color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private suspend fun processImageUri(
    uri: Uri,
    context: android.content.Context,
    userToken: String,
    repository: ProductRepository,
    onMessageAdded: (ChatUiMessage) -> Unit
) {
    try {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val imageBytes = inputStream?.readBytes()
        if (imageBytes != null) {
            val response = repository.discoveryChat(
                "Virtual Try-On requested for this item.",
                emptyList(),
                userToken,
                imageBytes
            )
            onMessageAdded(ChatUiMessage(
                "Vaultier", 
                response.response,
                response.vtoImageUrl,
                response.vtoVideoUrl
            ))
        }
    } catch (e: Exception) {
        Log.e("Vaultier", "Error processing image: ${e.message}")
        onMessageAdded(ChatUiMessage("Vaultier", "I couldn't process that image. Please try again."))
    }
}

@Composable
fun ChatBubble(role: String, text: String, style: Style = Style) {
    val isUser = role == "User"
    val baseStyle = if (isUser) VaultierTheme.styles.userChatBubbleStyle else VaultierTheme.styles.assistantChatBubbleStyle
    val styleState = rememberStyleState()
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .styleable(styleState, baseStyle, style)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(12.dp),
                color = if (isUser) Color.White else Color.Black,
                style = VaultierTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun ProductGridSection(products: List<Product>, onAddToCart: (Product) -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "Shop Collection",
            style = VaultierTheme.typography.headlineSmall,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        
        products.chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth()) {
                pair.forEach { product ->
                    ProductItem(
                        product = product, 
                        onAddToCart = { onAddToCart(product) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun ProductItem(
    product: Product, 
    onAddToCart: () -> Unit, 
    modifier: Modifier = Modifier,
    style: Style = Style
) {
    val styleState = rememberStyleState()
    
    Box(
        modifier = modifier
            .padding(8.dp)
            .styleable(styleState, VaultierTheme.styles.productCardStyle, style)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                AsyncImage(
                    model = product.imageUrl,
                    contentDescription = product.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // Luxury Badge
                Surface(
                    color = Color(0xFF2C2A26),
                    modifier = Modifier.padding(12.dp).align(Alignment.TopStart)
                ) {
                    Text(
                        "LIMITED", 
                        color = Color.White, 
                        fontSize = 8.sp, 
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        letterSpacing = 1.sp
                    )
                }
            }
            
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = product.category.uppercase(), 
                    style = VaultierTheme.typography.labelSmall,
                    color = Color.Gray,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = product.name, 
                        style = VaultierTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$${product.price}",
                        style = VaultierTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Light
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onAddToCart,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = VaultierTheme.shapes.extraSmall,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2A26)),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("ADD TO CART", fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    items: List<Product>, 
    onBack: () -> Unit, 
    onComplete: () -> Unit,
    isProcessing: Boolean = false
) {
    val total = items.map { it.price }.sum()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Review Order") },
                navigationIcon = {
                    IconButton(onClick = { if (!isProcessing) onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFFF5F2EB)
                )
            )
        },
        containerColor = Color(0xFFF5F2EB)
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Summary", style = VaultierTheme.typography.titleLarge)
            
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items.forEach { item ->
                    ListItem(
                        headlineContent = { Text(item.name) },
                        trailingContent = { Text("$${item.price}") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
            
            HorizontalDivider()
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Total", style = VaultierTheme.typography.titleLarge)
                Text("$${total}", style = VaultierTheme.typography.titleLarge)
            }
            
            if (isProcessing) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF2C2A26))
                }
            } else {
                Button(
                    onClick = onComplete,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = VaultierTheme.shapes.extraSmall,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2A26))
                ) {
                    Text("Confirm Purchase", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
            
@Preview(showBackground = true)
@Composable
fun CheckoutScreenPreview() {
    VaultierTheme {
        CheckoutScreen(
            items = listOf(
                Product("1", "SKU1", "Vaultier Horizon Glasses", 299.99, "", "Wearable"),
                Product("2", "SKU2", "Vaultier Biomorphic Chair", 1200.00, "", "Home")
            ),
            onBack = {},
            onComplete = {}
        )
    }
}
