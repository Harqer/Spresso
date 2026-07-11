package com.meta.wearable.retail

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
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
import androidx.compose.material.icons.filled.ShoppingCart
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
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.core.types.RegistrationState
import com.meta.wearable.retail.ui.Product
import com.meta.wearable.retail.ui.ProductRepository
import com.meta.wearable.retail.ui.RetailMobileApp
import com.meta.wearable.retail.ui.theme.VaultierTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import javax.inject.Inject

enum class AppScreen {
    Support, About, Launch, Identity, Hardware, Permissions, Welcome, ChooseVoice, Shop
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

                LaunchedEffect(regState) {
                    if (regState == RegistrationState.REGISTERED && currentScreen == AppScreen.Hardware) {
                        currentScreen = AppScreen.Permissions
                    }
                }

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (currentScreen) {
                        AppScreen.Launch -> {
                            FullScreenAsset(
                                imageRes = R.drawable.launch_scre,
                                tagline = "THE FUTURE OF RETAIL",
                                onContinue = { currentScreen = AppScreen.Welcome }
                            )
                        }
                        AppScreen.Welcome -> {
                            OnboardingScreen(
                                bgColor = Color(0xFF2C2A26),
                                tagline = "AGENTIC COMMERCE\nFOR THE WEARABLE ERA",
                                onContinue = {
                                    if (auth.currentUser != null) {
                                        currentScreen = if (regState == RegistrationState.REGISTERED) AppScreen.Shop else AppScreen.Hardware
                                    } else {
                                        currentScreen = AppScreen.Identity
                                    }
                                }
                            )
                        }
                        AppScreen.Identity -> {
                            var email by remember { mutableStateOf("") }
                            var pass by remember { mutableStateOf("") }
                            Column(modifier = Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
                                Text("VAULTIER IDENTITY", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                                Spacer(modifier = Modifier.height(32.dp))
                                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(
                                    onClick = { 
                                        auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener { 
                                            if (it.isSuccessful) {
                                                currentScreen = if (regState == RegistrationState.REGISTERED) AppScreen.Shop else AppScreen.Hardware
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(56.dp)
                                ) { Text("SIGN IN") }
                            }
                        }
                        AppScreen.Hardware -> {
                            HardwareScreen(regState) {
                                Wearables.startRegistration(this@MainActivity)
                            }
                        }
                        AppScreen.Permissions -> {
                            PermissionsScreen {
                                currentScreen = AppScreen.Shop
                            }
                        }
                        AppScreen.Shop -> {
                            var isAuthenticated by remember { mutableStateOf(false) }
                            var userToken by remember { mutableStateOf("") }

                            LaunchedEffect(Unit) {
                                val currentUser = auth.currentUser
                                if (currentUser != null) {
                                    currentUser.getIdToken(false).addOnSuccessListener { result ->
                                        userToken = result.token ?: ""
                                        isAuthenticated = true
                                    }
                                } else {
                                    // Fallback for simulation if needed, but should be authenticated
                                    isAuthenticated = true
                                    userToken = "SIMULATED_USER_TOKEN"
                                }
                            }

                            if (isAuthenticated && userToken.isNotEmpty()) {
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
                                                userToken = userToken,
                                                sessionManager = sessionManager,
                                                repository = repository,
                                                initialImageUri = sharedImageUri,
                                                onExit = { currentScreen = AppScreen.Welcome }
                                            )
                                            Box(modifier = Modifier.fillMaxSize()) {
                                                ExtendedFloatingActionButton(onClick = { showMobileApp = false }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                                                    Icon(Icons.Default.Build, contentDescription = null)
                                                    Text("CONTROLLER")
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
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            uri?.let { sharedImageUri = it }
        }
    }

    @Composable
    fun FullScreenAsset(imageRes: Int, tagline: String?, onContinue: () -> Unit) {
        Box(modifier = Modifier.fillMaxSize().clickable { onContinue() }) {
            Image(painter = painterResource(imageRes), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            tagline?.let {
                Text(it, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp), color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
        }
    }

    @Composable
    fun OnboardingScreen(bgColor: Color, tagline: String, onContinue: () -> Unit) {
        Column(modifier = Modifier.fillMaxSize().background(bgColor).clickable { onContinue() }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(tagline, color = Color.White, textAlign = TextAlign.Center, fontWeight = FontWeight.Black, fontSize = 24.sp, letterSpacing = 4.sp)
            Spacer(modifier = Modifier.height(48.dp))
            Text("TAP TO START", color = Color.Gray, fontSize = 12.sp, letterSpacing = 2.sp)
        }
    }

    @Composable
    fun HardwareScreen(state: RegistrationState, onRegister: () -> Unit) {
        Column(modifier = Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("CONNECT HARDWARE", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Vaultier requires Ray-Ban Meta glasses to enable agentic shopping flows.", textAlign = TextAlign.Center, color = Color.Gray)
            Spacer(modifier = Modifier.height(48.dp))
            if (state == RegistrationState.REGISTERING) {
                CircularProgressIndicator(color = Color.Black)
            } else {
                Button(onClick = onRegister, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text("PAIR GLASSES")
                }
            }
        }
    }

    @Composable
    fun PermissionsScreen(onComplete: () -> Unit) {
        val scope = rememberCoroutineScope()
        Column(modifier = Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
            Text("DEVICE ACCESS", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Grant camera access to enable visual product search and VTO.")
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { scope.launch { onComplete() } }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("GRANT ACCESS")
            }
        }
    }

    fun showBiometricPrompt(title: String, subtitle: String, onSuccess: () -> Unit) {
        val info = BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle(subtitle).setNegativeButtonText("Cancel").build()
        val bp = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }
        })
        bp.authenticate(info)
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionManager.destroy()
    }
}

@Composable
fun GlassesController(sessionManager: RetailSessionManager, onBack: () -> Unit, activity: ComponentActivity) {
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
                Text(if (session != null) "CONNECTED" else "DISCONNECTED", color = if (session != null) Color.Green else Color.Red, fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = { 
                isStreaming = !isStreaming
                sessionManager.updateStreamingStatus(isStreaming)
            },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (isStreaming) Color.Red else Color.White)
        ) {
            Text(if (isStreaming) "STOP STREAM" else "START STREAM", color = if (isStreaming) Color.White else Color.Black)
        }
    }
}
