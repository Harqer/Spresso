package com.meta.wearable.retail

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.RegistrationState
import com.meta.wearable.retail.ui.ProductRepository
import com.meta.wearable.retail.ui.RetailMobileApp
import com.meta.wearable.retail.ui.theme.VaultierTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.Executor
import javax.inject.Inject

enum class AppScreen {
    Support,
    About,
    Launch,
    Identity,
    Welcome,
    ChooseVoice,
    Shop,
    Settings,
}

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var sessionManager: RetailSessionManager

    @Inject lateinit var repository: ProductRepository
    private lateinit var executor: Executor
    private var sharedImageUri by mutableStateOf(Uri.EMPTY)
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = Firebase.auth
        executor = ContextCompat.getMainExecutor(this)
        handleIntent(intent)

        setContent {
            VaultierTheme {
                var currentScreen by remember { mutableStateOf(AppScreen.Launch) }
                val regState by Wearables.registrationState.collectAsState()
                var showRationale by remember { mutableStateOf(false) }

                val regState by Wearables.registrationState.collectAsState()

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (currentScreen) {
                        AppScreen.Launch -> {
                            FullScreenAsset(
                                imageRes = R.drawable.launch_scre,
                                tagline = "THE FUTURE OF RETAIL",
                                onContinue = { currentScreen = AppScreen.Welcome },
                            )
                        }
                        AppScreen.Welcome -> {
                            OnboardingScreen(
                                bgColor = Color(0xFF2C2A26),
                                tagline = "AGENTIC COMMERCE\nFOR THE WEARABLE ERA",
                                onContinue = {
                                    if (auth.currentUser != null) {
                                        currentScreen = AppScreen.Shop
                                    } else {
                                        currentScreen = AppScreen.Identity
                                    }
                                },
                            )
                        }
                        AppScreen.Identity -> {
                            var email by remember { mutableStateOf("") }
                            var pass by remember { mutableStateOf("") }
                            Column(modifier = Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
                                Text("VAULTIER IDENTITY", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                                Spacer(modifier = Modifier.height(32.dp))
                                OutlinedTextField(
                                    value = email,
                                    onValueChange = { email = it },
                                    label = { Text("Email") },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = pass,
                                    onValueChange = { pass = it },
                                    label = { Text("Password") },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(
                                    onClick = {
                                        auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener {
                                            if (it.isSuccessful) {
                                                currentScreen = AppScreen.Shop
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                ) { Text("SIGN IN") }
                            }
                        }
                        AppScreen.Settings -> {
                            SettingsScreen(
                                regState = regState,
                                onBack = { currentScreen = AppScreen.Shop },
                                onConnect = { Wearables.startRegistration(this@MainActivity) },
                                onDisconnect = { Wearables.startUnregistration(this@MainActivity) },
                                onSignOut = {
                                    auth.signOut()
                                    currentScreen = AppScreen.Identity
                                },
                            )
                        }
                        AppScreen.Shop -> {
                            var isAuthenticated by remember { mutableStateOf(false) }
                            var userToken by remember { mutableStateOf("") }
                            var userTier by remember { mutableStateOf("free") }
                            var userName by remember { mutableStateOf("Vaultier User") }
                            var userEmail by remember { mutableStateOf("user@vaultier.ai") }

                            LaunchedEffect(Unit) {
                                val currentUser = auth.currentUser
                                if (currentUser != null) {
                                    userName = currentUser.displayName ?: "Vaultier User"
                                    userEmail = currentUser.email ?: "user@vaultier.ai"
                                    currentUser
                                        .getIdToken(false)
                                        .addOnSuccessListener { result ->
                                            userToken = result.token ?: ""
                                            userTier = result.claims["stripeRole"] as? String ?: "free"
                                            isAuthenticated = true
                                        }.addOnFailureListener {
                                            currentScreen = AppScreen.Identity
                                        }
                                } else {
                                    currentScreen = AppScreen.Identity
                                }
                            }

                            if (isAuthenticated && userToken.isNotEmpty()) {
                                var showMobileApp by remember { mutableStateOf(true) }
                                Scaffold(
                                    topBar = {
                                        if (showMobileApp) {
                                            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                                                IconButton(onClick = { currentScreen = AppScreen.Settings }) {
                                                    Icon(
                                                        androidx.compose.material.icons.Icons.Default.Settings,
                                                        contentDescription = "Settings",
                                                        tint = Color.Black,
                                                    )
                                                }
                                            }
                                        }
                                    },
                                ) { padding ->
                                    Box(modifier = Modifier.padding(padding)) {
                                        if (showMobileApp) {
                                            RetailMobileApp(
                                                userToken = userToken,
                                                userTier = userTier,
                                                userName = userName,
                                                userEmail = userEmail,
                                                isWearableConnected = (regState == RegistrationState.REGISTERED),
                                                sessionManager = sessionManager,
                                                repository = repository,
                                                initialImageUri = sharedImageUri,
                                                onExit = { currentScreen = AppScreen.Welcome },
                                            )
                                            if (regState == RegistrationState.REGISTERED) {
                                                Box(modifier = Modifier.fillMaxSize()) {
                                                    ExtendedFloatingActionButton(
                                                        onClick = { showMobileApp = false },
                                                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                                                    ) {
                                                        Icon(Icons.Default.Build, contentDescription = null)
                                                        Text("CONTROLLER")
                                                    }
                                                }
                                            }
                                        } else {
                                            GlassesController(sessionManager, onBack = { showMobileApp = true }, this@MainActivity)
                                        }
                                    }
                                }
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val uri =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            uri?.let { sharedImageUri = it }
        }
    }

    @Composable
    fun FullScreenAsset(
        imageRes: Int,
        tagline: String?,
        onContinue: () -> Unit,
    ) {
        Box(modifier = Modifier.fillMaxSize().clickable { onContinue() }) {
            Image(
                painter = painterResource(imageRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            tagline?.let {
                Text(
                    it,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
            }
        }
    }

    @Composable
    fun OnboardingScreen(
        bgColor: Color,
        tagline: String,
        onContinue: () -> Unit,
    ) {
        Column(
            modifier =
                Modifier.fillMaxSize().background(bgColor).clickable {
                    onContinue()
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                tagline,
                color = Color.White,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Black,
                fontSize = 24.sp,
                letterSpacing = 4.sp,
            )
            Spacer(modifier = Modifier.height(48.dp))
            Text("TAP TO START", color = Color.Gray, fontSize = 12.sp, letterSpacing = 2.sp)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SettingsScreen(
        regState: RegistrationState,
        onBack: () -> Unit,
        onConnect: () -> Unit,
        onDisconnect: () -> Unit,
        onSignOut: () -> Unit,
    ) {
        Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
            CenterAlignedTopAppBar(
                title = { Text("SETTINGS", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                ) {
                    Text("Sign Out")
                }
                Spacer(modifier = Modifier.height(32.dp))
                Text("Hardware", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F0F0))) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Ray-Ban Meta Glasses", fontWeight = FontWeight.Bold, color = Color.Black)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text =
                                if (regState ==
                                    RegistrationState.REGISTERED
                                ) {
                                    "Connected"
                                } else if (regState ==
                                    RegistrationState.REGISTERING
                                ) {
                                    "Connecting..."
                                } else {
                                    "Disconnected"
                                },
                            color = if (regState == RegistrationState.REGISTERED) Color(0xFF4CAF50) else Color.Gray,
                            fontSize = 14.sp,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (regState == RegistrationState.REGISTERED) {
                            Button(
                                onClick = onDisconnect,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            ) {
                                Text("Disconnect Glasses")
                            }
                        } else {
                            Button(
                                onClick = onConnect,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = regState != RegistrationState.REGISTERING,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                            ) {
                                Text(if (regState == RegistrationState.REGISTERING) "Connecting..." else "Connect Glasses")
                            }
                        }
                    }
                }
            }
        }
    }

    fun showBiometricPrompt(
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
    ) {
        val info =
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText("Cancel")
                .build()
        val bp =
            BiometricPrompt(
                this,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        onSuccess()
                    }
                },
            )
        bp.authenticate(info)
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionManager.destroy()
    }
}

@Composable
fun GlassesController(
    sessionManager: RetailSessionManager,
    onBack: () -> Unit,
    activity: ComponentActivity,
) {
    val session by sessionManager.currentSession.collectAsState()
    var isStreaming by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black).padding(24.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White) }
        Spacer(modifier = Modifier.height(24.dp))
        Text("VAULTIER PULSE", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
        Spacer(modifier = Modifier.height(48.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("HARDWARE STATUS", color = Color.Gray, fontSize = 10.sp)
                Text(
                    if (session !=
                        null
                    ) {
                        "CONNECTED"
                    } else {
                        "DISCONNECTED"
                    },
                    color =
                        if (session !=
                            null
                        ) {
                            Color.Green
                        } else {
                            Color.Red
                        },
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                isStreaming = !isStreaming
                sessionManager.updateStreamingStatus(isStreaming)
            },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (isStreaming) Color.Red else Color.White),
        ) {
            Text(if (isStreaming) "STOP STREAM" else "START STREAM", color = if (isStreaming) Color.White else Color.Black)
        }
    }
}
