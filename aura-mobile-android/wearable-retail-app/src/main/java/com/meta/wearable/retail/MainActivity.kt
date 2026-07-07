package com.meta.wearable.retail

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.meta.wearable.retail.ui.theme.VaultierTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.core.types.RegistrationState
import com.meta.wearable.retail.ui.theme.VaultierTheme
import com.meta.wearable.retail.ui.Product
import com.meta.wearable.retail.ui.RetailMobileApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

enum class AppScreen {
    Support, About, Launch, Identity, Hardware, Permissions, Welcome, ChooseVoice, Shop
}

class MainActivity : FragmentActivity() {
    private lateinit var sessionManager: RetailSessionManager
    private lateinit var executor: Executor
    private var sharedImageUri by mutableStateOf<Uri?>(null)
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = Firebase.auth
        sessionManager = RetailSessionManager(this)
        executor = ContextCompat.getMainExecutor(this)
        handleIntent(intent)
        
        setContent {
            val user by remember { mutableStateOf(auth.currentUser) }
            val galleryPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
            var showRationale by remember { mutableStateOf(false) }
            var currentScreen by remember { mutableStateOf(AppScreen.Launch) }
            
            VaultierTheme {
                val isAuthenticated = user != null
                val registrationState by Wearables.registrationState.collectAsState()
                val permissionLauncher = rememberLauncherForActivityResult(Wearables.RequestPermissionContract()) { result ->
                    result.onSuccess { status ->
                        if (status is PermissionStatus.Granted) {
                            currentScreen = AppScreen.Welcome
                        }
                    }
                }

                if (showRationale) {
                    AlertDialog(
                        onDismissRequest = { showRationale = false },
                        title = { Text("Vaultier Gallery Access") },
                        text = { Text("To analyze looks from your photos, we need gallery access.") },
                        confirmButton = {
                            TextButton(onClick = {
                                showRationale = false
                                val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                    arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES, android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                                } else arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                                galleryPermissionLauncher.launch(permissions)
                            }) { Text("Continue") }
                        },
                        dismissButton = { TextButton(onClick = { showRationale = false }) { Text("Not Now") } }
                    )
                }
                
                when (currentScreen) {
                    AppScreen.Launch -> FullScreenAsset(R.drawable.launch_scre) { 
                        currentScreen = if (isAuthenticated) {
                            if (registrationState == RegistrationState.REGISTERED) AppScreen.Welcome else AppScreen.Hardware
                        } else AppScreen.Identity 
                    }
                    AppScreen.Identity -> {
                        if (isAuthenticated) { 
                            currentScreen = if (registrationState == RegistrationState.REGISTERED) AppScreen.Welcome else AppScreen.Hardware 
                        }
                        else { 
                            // Firebase Identity Bridge
                            Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F2EB)), contentAlignment = Alignment.Center) { 
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Vaultier Identity", style = VaultierTheme.typography.headlineLarge)
                                    Spacer(Modifier.height(32.dp))
                                    Button(onClick = { 
                                        // Placeholder for Google Sign In / Email Auth
                                        // auth.signInAnonymously().addOnSuccessListener { currentScreen = AppScreen.Hardware }
                                    }) { Text("Sign In with Google") }
                                }
                            } 
                        }
                    }
                    AppScreen.Hardware -> HardwareScreen(registrationState) { 
                        lifecycleScope.launch {
                            Wearables.checkPermissionStatus(Permission.CAMERA).onSuccess { status ->
                                currentScreen = if (status is PermissionStatus.Granted) AppScreen.Welcome else AppScreen.Permissions
                            }
                        }
                    }
                    AppScreen.Permissions -> PermissionsScreen { permissionLauncher.launch(Permission.CAMERA) }
                    AppScreen.Welcome -> FullScreenAsset(R.drawable.welcome, "Welcome to Vaultier") { currentScreen = AppScreen.ChooseVoice }
                    AppScreen.ChooseVoice -> OnboardingScreen(Color.Black, "Visionary Voice") { currentScreen = AppScreen.Shop }
                    AppScreen.Shop -> {
                        // Retrieve JWT for Agentic Pulse
                        var token by remember { mutableStateOf("") }
                        LaunchedEffect(user) {
                            user?.getIdToken(true)?.addOnSuccessListener { result ->
                                token = result.token ?: ""
                            }
                        }

                        if (isAuthenticated) {
                            var showMobileApp by remember { mutableStateOf(true) }
                            Scaffold(
                                topBar = { 
                                    if (showMobileApp) { 
                                        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) { 
                                            TextButton(onClick = { auth.signOut(); currentScreen = AppScreen.Identity }) {
                                                Text("Sign Out")
                                            }
                                        } 
                                    } 
                                }
                            ) { padding ->
                                Box(modifier = Modifier.padding(padding)) {
                                    if (showMobileApp) {
                                        RetailMobileApp(
                                            userToken = token,
                                            sessionManager = sessionManager,
                                            initialImageUri = sharedImageUri,
                                            onRequestGalleryAccess = { showRationale = true },
                                            onCompletePurchase = { items, onAuthenticated ->
                                                showBiometricPrompt(
                                                    title = "Confirm Purchase",
                                                    subtitle = "Finalize order",
                                                    onSuccess = onAuthenticated
                                                )
                                            }
                                        )
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            ExtendedFloatingActionButton(onClick = { showMobileApp = false }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                                                Icon(Icons.Default.Build, contentDescription = null)
                                                Spacer(Modifier.width(8.dp))
                                                Text("Glasses Mode")
                                            }
                                        }
                                    } else {
                                        GlassesController(sessionManager, onBack = { showMobileApp = true }, context = LocalActivity.current as ComponentActivity)
                                    }
                                }
                            }
                        } else { currentScreen = AppScreen.Identity }
                    }
                    else -> Box(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) { 
        super.onNewIntent(intent)
        // Industrial Standard: Update the activity intent reference to prevent stale data pulses
        setIntent(intent)
        handleIntent(intent) 
    }
    private fun handleIntent(intent: Intent?) { if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) { (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let { uri -> sharedImageUri = uri } } }

    @Composable
    fun FullScreenAsset(resId: Int, label: String? = null, onNext: () -> Unit) {
        Box(modifier = Modifier.fillMaxSize().clickable { onNext() }) {
            Image(painter = painterResource(id = resId), contentDescription = null, modifier = Modifier.fillMaxSize())
            if (label != null) { Text(text = label, modifier = Modifier.align(Alignment.Center), style = VaultierTheme.typography.headlineMedium, color = Color.White) }
        }
    }

    @Composable
    fun OnboardingScreen(bgColor: Color, label: String, onNext: () -> Unit) {
        Box(modifier = Modifier.fillMaxSize().background(bgColor).clickable { onNext() }, contentAlignment = Alignment.Center) {
            Text(label, style = VaultierTheme.typography.headlineMedium, color = Color.White)
        }
    }

    @Composable
    fun HardwareScreen(state: RegistrationState, onNext: () -> Unit) {
        val activity = LocalActivity.current as ComponentActivity
        Box(modifier = Modifier.fillMaxSize().background(Color.Black).padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Text("Link your Glasses", color = Color.White, style = VaultierTheme.typography.headlineLarge)
                Text("Registration Status: $state", color = Color.LightGray)
                if (state == RegistrationState.REGISTERED) {
                    Button(onClick = onNext) { Text("Continue") }
                } else {
                    Button(onClick = { Wearables.startRegistration(activity) }) { Text("Start Registration") }
                }
            }
        }
    }

    @Composable
    fun PermissionsScreen(onRequest: () -> Unit) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black).padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Text("Vision Access", color = Color.White, style = VaultierTheme.typography.headlineLarge)
                Text("Vaultier needs camera access to see and suggest products.", color = Color.LightGray, textAlign = TextAlign.Center)
                Button(onClick = onRequest) { Text("Grant Access") }
            }
        }
    }

    private fun showBiometricPrompt(title: String, subtitle: String, onSuccess: () -> Unit) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle(subtitle).setNegativeButtonText("Cancel").setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).build()
        val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { super.onAuthenticationSucceeded(result); onSuccess() }
        })
        biometricPrompt.authenticate(promptInfo)
    }

    override fun onDestroy() { super.onDestroy(); sessionManager.destroy() }
}

@Composable
fun GlassesController(sessionManager: RetailSessionManager, onBack: () -> Unit, context: ComponentActivity) {
    val deviceIds by Wearables.devices.collectAsState(initial = emptySet())
    val session by sessionManager.currentSession.collectAsState()
    val sessionState by (session?.state ?: MutableStateFlow(DeviceSessionState.STOPPED)).collectAsState(DeviceSessionState.STOPPED)
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Text(text = "Glasses Controller", style = VaultierTheme.typography.headlineSmall)
            }
            Button(onClick = { Wearables.startRegistration(context) }, modifier = Modifier.fillMaxWidth()) { Text("Update Registration") }
            deviceIds.forEach { deviceId -> Button(onClick = { sessionManager.startSession(deviceId, "") }, modifier = Modifier.fillMaxWidth()) { Text("Connect: $deviceId") } }
            if (sessionState == DeviceSessionState.STARTED) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Button(onClick = { sessionManager.showWelcome() }, modifier = Modifier.fillMaxWidth()) { Text("Push UI") }
                        Button(onClick = { sessionManager.stopSession() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Stop") }
                    }
                }
            }
        }
    }
}
