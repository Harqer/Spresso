package com.meta.wearable.retail.ui

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.meta.wearable.retail.RetailSessionManager
import com.meta.wearable.retail.ui.theme.*
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
    repository: ProductRepository,
    initialImageUri: Uri = Uri.EMPTY,
    onExit: () -> Unit = {}
) {
    var textInput by remember { mutableStateOf("") }
    var chatMessages by remember { mutableStateOf(listOf(
        ChatUiMessage("Vaultier", "Welcome to Vaultier. I can help you discover products and perform virtual try-ons. Try sending a photo or asking about our collection!")
    )) }
    var isThinking by remember { mutableStateOf(false) }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var cartItems by remember { mutableStateOf<List<Product>>(emptyList()) }
    var showCheckout by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val nanoEngine = remember { GeminiNanoBanana2() }
    val context = LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            chatMessages = chatMessages + ChatUiMessage("User", "Analyzing photo...")
            isThinking = true
            processImageUri(uri, context, userToken, repository) { msg ->
                chatMessages = chatMessages + msg
                isThinking = false
            }
        }
    }

    LaunchedEffect(Unit) {
        products = repository.getRecommendations(userToken)
        if (initialImageUri != Uri.EMPTY) {
            chatMessages = chatMessages + ChatUiMessage("User", "Processing shared image...")
            isThinking = true
            processImageUri(initialImageUri, context, userToken, repository) { msg ->
                chatMessages = chatMessages + msg
                isThinking = false
            }
        }
    }

    if (showCheckout) {
        CheckoutScreen(
            items = cartItems,
            onBack = { showCheckout = false },
            onPurchaseComplete = { 
                cartItems = emptyList()
                showCheckout = false
                sessionManager.showPurchaseSuccess()
            }
        )
    } else {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("VAULTIER", fontWeight = FontWeight.Black, letterSpacing = 4.sp) },
                    navigationIcon = {
                        IconButton(onClick = onExit) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit")
                        }
                    },
                    actions = {
                        val cartSize = cartItems.size
                        BadgedBox(badge = { if(cartSize > 0) Badge { Text(cartSize.toString()) } }) {
                            IconButton(onClick = { if(cartSize > 0) showCheckout = true }) {
                                Icon(Icons.Default.ShoppingCart, contentDescription = "Cart")
                            }
                        }
                    },
                    colors = CenterAlignedTopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.White
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFFF8F8F8))
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(16.dp)
                ) {
                    chatMessages.forEach { message ->
                        ChatBubble(message.role, message.text)
                        
                        if (message.vtoImageUrl != null || message.vtoVideoUrl != null) {
                            VTOPreviewCard(message.vtoImageUrl, message.vtoVideoUrl)
                        }

                        if (message.grid.isNotEmpty()) {
                            ProductGridSection(message.grid) { p ->
                                if (!cartItems.contains(p)) cartItems = cartItems + p
                                sessionManager.showCart(cartItems)
                            }
                        }

                        if (message.compare.isNotEmpty()) {
                            Text("Comparison View", style = VaultierTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(message.compare) { p ->
                                    ProductItem(p, onAddToCart = { 
                                        if (!cartItems.contains(p)) cartItems = cartItems + p 
                                    }, modifier = Modifier.width(200.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    if (isThinking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                    }
                }

                Surface(
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    color = Color.White
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (chatMessages.isNotEmpty() && chatMessages.last().filters.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                                items(chatMessages.last().filters) { filter ->
                                    FilterChip(
                                        selected = false,
                                        onClick = { textInput = "Show me $filter options" },
                                        label = { Text(filter) }
                                    )
                                }
                            }
                        }

                        TextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Ask Vaultier...") },
                            shape = CircleShape,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFF0F0F0),
                                unfocusedContainerColor = Color(0xFFF0F0F0),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
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
                                    }
                                }
                            },
                            trailingIcon = {
                                IconButton(
                                    enabled = textInput.isNotEmpty() && !isThinking,
                                    onClick = {
                                        val userMsg = textInput
                                        chatMessages = chatMessages + ChatUiMessage("User", userMsg)
                                        textInput = ""
                                        isThinking = true
                                        
                                        scope.launch {
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
                                    }
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VTOPreviewCard(imageUrl: String?, videoUrl: String?) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(400.dp).background(Color.Black)) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "VTO Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = CircleShape,
                    modifier = Modifier.padding(16.dp).align(Alignment.TopEnd)
                ) {
                    Text(
                        "AI GENERATED",
                        color = Color.White,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Virtual Try-On", fontWeight = FontWeight.Bold)
                    Text("Higgsfield-1 Video Engine", style = VaultierTheme.typography.bodySmall, color = Color.Gray)
                }
                IconButton(onClick = { /* Share */ }) {
                    Icon(Icons.Default.Share, contentDescription = "Share")
                }
            }
        }
    }
}

fun processImageUri(
    uri: Uri,
    context: android.content.Context,
    userToken: String,
    repository: ProductRepository,
    onMessageAdded: (ChatUiMessage) -> Unit
) {
    // Industrial Logic: Image Analysis Implementation
    val contentResolver = context.contentResolver
    try {
        val inputStream: InputStream? = contentResolver.openInputStream(uri)
        val bytes = inputStream?.readBytes()
        if (bytes != null) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                val response = repository.discoveryChat(
                    message = "Please analyze this product from my glasses camera.",
                    cartItems = emptyList(),
                    userToken = userToken,
                    imageBytes = bytes
                )
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onMessageAdded(ChatUiMessage(
                        role = "Vaultier",
                        text = response.response,
                        vtoImageUrl = response.vtoImageUrl,
                        vtoVideoUrl = response.vtoVideoUrl,
                        grid = response.grid,
                        compare = response.compare,
                        filters = response.filters
                    ))
                }
            }
        }
    } catch (e: Exception) {
        Log.e("Vaultier", "Image processing failed: ${e.message}")
    }
}

@Composable
fun ChatBubble(role: String, text: String) {
    val isUser = role == "User"
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .then(if (isUser) Modifier.userChatBubble() else Modifier.assistantChatBubble())
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
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(8.dp)
            .productCard()
            .clickable { onAddToCart() }
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                AsyncImage(
                    model = product.imageUrl,
                    contentDescription = product.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
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
                    shape = RoundedCornerShape(4.dp),
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
    onPurchaseComplete: () -> Unit,
    isProcessing: Boolean = false
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CHECKOUT", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            items.forEach { item ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(model = item.imageUrl, contentDescription = null, modifier = Modifier.size(64.dp))
                    Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                        Text(item.name, fontWeight = FontWeight.Bold)
                        Text("$${item.price}", color = Color.Gray)
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            val total = items.sumOf { it.price }
            Text("Total: $${String.format("%.2f", total)}", style = VaultierTheme.typography.headlineMedium)
            
            Button(
                onClick = onPurchaseComplete,
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 16.dp),
                enabled = !isProcessing,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
            ) {
                if (isProcessing) CircularProgressIndicator(color = Color.White)
                else Text("CONFIRM PURCHASE")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CheckoutScreenPreview() {
    VaultierTheme {
        CheckoutScreen(
            items = listOf(Product("1", "SKU", "Classic Shirt", 89.0, "", "Apparel")),
            onBack = {},
            onPurchaseComplete = {}
        )
    }
}
