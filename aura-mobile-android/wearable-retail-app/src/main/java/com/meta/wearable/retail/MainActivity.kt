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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.RegistrationState
import com.meta.wearable.retail.data.SettingsRepository
import com.meta.wearable.retail.data.ThemeMode
import com.meta.wearable.retail.ui.ProductRepository
import com.meta.wearable.retail.ui.RetailMobileApp
import com.meta.wearable.retail.ui.SpressoOnboarding
import com.meta.wearable.retail.ui.theme.SpressoTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import javax.inject.Inject

enum class AppScreen {
    Support, About, Launch, Identity, Welcome, ChooseVoice, Shop, Settings,
}

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var sessionManager: RetailSessionManager
    @Inject lateinit var repository: ProductRepository
    @Inject lateinit var settingsRepository: SettingsRepository

    private lateinit var executor: Executor
    private var sharedImageUri by mutableStateOf(Uri.EMPTY)
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = Firebase.auth
        executor = ContextCompat.getMainExecutor(this)
        handleIntent(intent)

        setContent {
            val themeMode by settingsRepository.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
            val currentLanguage by settingsRepository.languageFlow.collectAsState(initial = "en")

            LaunchedEffect(currentLanguage) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val localeManager = getSystemService(android.app.LocaleManager::class.java)
                    localeManager.applicationLocales = android.os.LocaleList.forLanguageTags(currentLanguage)
                }
            }

            SpressoTheme(themeMode = themeMode) {
                var currentScreen by remember { mutableStateOf(AppScreen.Launch) }
                val regState by Wearables.registrationState.collectAsState()
                var showRationale by remember { mutableStateOf(false) }

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
                            SpressoOnboarding(
                                onContinue = {
                                    if (auth.currentUser != null) {
                                        currentScreen = AppScreen.Shop
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
                                Text(stringResource(R.string.spresso_identity), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                                Spacer(modifier = Modifier.height(32.dp))
                                OutlinedTextField(
                                    value = email,
                                    onValueChange = { email = it },
                                    label = { Text(stringResource(R.string.email_label)) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = pass,
                                    onValueChange = { pass = it },
                                    label = { Text(stringResource(R.string.password_label)) },
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
                                ) { Text(stringResource(R.string.sign_in)) }
                            }
                        }
                        AppScreen.Settings -> {
                            SettingsScreen(
                                regState = regState,
                                themeMode = themeMode,
                                currentLanguage = currentLanguage,
                                settingsRepository = settingsRepository,
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
                            var userName by remember { mutableStateOf("Spresso User") }
                            var userEmail by remember { mutableStateOf("user@spresso.ai") }

                            LaunchedEffect(Unit) {
                                val currentUser = auth.currentUser
                                if (currentUser != null) {
                                    userName = currentUser.displayName ?: "Spresso User"
                                    userEmail = currentUser.email ?: "user@spresso.ai"
                                    currentUser.getIdToken(false).addOnSuccessListener { result ->
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
                                                        Icons.Default.Settings,
                                                        contentDescription = stringResource(R.string.cd_settings),
                                                        tint = MaterialTheme.colorScheme.onSurface,
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
                                                        Text(stringResource(R.string.controller))
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
        val haptic = LocalHapticFeedback.current
        Box(modifier = Modifier.fillMaxSize().clickable { 
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onContinue() 
        }) {
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
        val haptic = LocalHapticFeedback.current
        Column(
            modifier = Modifier.fillMaxSize().background(bgColor).clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
            Text(stringResource(R.string.tap_to_start), color = Color.Gray, fontSize = 12.sp, letterSpacing = 2.sp)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SettingsScreen(
        regState: RegistrationState,
        themeMode: ThemeMode,
        currentLanguage: String,
        settingsRepository: SettingsRepository,
        onBack: () -> Unit,
        onConnect: () -> Unit,
        onDisconnect: () -> Unit,
        onSignOut: () -> Unit,
    ) {
        val haptic = LocalHapticFeedback.current
        val coroutineScope = rememberCoroutineScope()
        var expandedLanguage by remember { mutableStateOf(false) }

        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onBack() 
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back)) }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = themeMode == ThemeMode.SYSTEM,
                        onClick = { 
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            coroutineScope.launch { settingsRepository.setThemeMode(ThemeMode.SYSTEM) } 
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                    ) { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SettingsBrightness, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.theme_system)) 
                        }
                    }
                    SegmentedButton(
                        selected = themeMode == ThemeMode.LIGHT,
                        onClick = { 
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            coroutineScope.launch { settingsRepository.setThemeMode(ThemeMode.LIGHT) } 
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                    ) { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LightMode, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.theme_light))
                        }
                    }
                    SegmentedButton(
                        selected = themeMode == ThemeMode.DARK,
                        onClick = { 
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            coroutineScope.launch { settingsRepository.setThemeMode(ThemeMode.DARK) } 
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                    ) { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.theme_dark)) 
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))

                Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = expandedLanguage,
                    onExpandedChange = { expandedLanguage = it },
                ) {
                    val languages = listOf("en" to "English", "es" to "Español", "fr" to "Français", "de" to "Deutsch", "ja" to "日本語", "zh" to "中文")
                    val currentLangName = languages.find { it.first == currentLanguage }?.second ?: "English"

                    OutlinedTextField(
                        value = currentLangName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedLanguage) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedLanguage,
                        onDismissRequest = { expandedLanguage = false }
                    ) {
                        languages.forEach { (code, name) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    coroutineScope.launch { settingsRepository.setLanguage(code) }
                                    expandedLanguage = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(stringResource(R.string.account), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSignOut()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface),
                ) {
                    Text(stringResource(R.string.sign_out), color = MaterialTheme.colorScheme.surface)
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(stringResource(R.string.hardware), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.rayban_meta_glasses), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text =
                                if (regState == RegistrationState.REGISTERED) stringResource(R.string.connected)
                                else if (regState == RegistrationState.REGISTERING) stringResource(R.string.connecting)
                                else stringResource(R.string.disconnected),
                            color = if (regState == RegistrationState.REGISTERED) Color(0xFF4CAF50) else Color.Gray,
                            fontSize = 14.sp,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (regState == RegistrationState.REGISTERED) {
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onDisconnect()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            ) {
                                Text(stringResource(R.string.disconnect_glasses))
                            }
                        } else {
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onConnect()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = regState != RegistrationState.REGISTERING,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface),
                            ) {
                                Text(if (regState == RegistrationState.REGISTERING) stringResource(R.string.connecting) else stringResource(R.string.connect_glasses), color = MaterialTheme.colorScheme.surface)
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
                .setNegativeButtonText(getString(R.string.cancel))
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
    val haptic = LocalHapticFeedback.current
    val session by sessionManager.currentSession.collectAsState()
    var isStreaming by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black).padding(24.dp)) {
        IconButton(onClick = { 
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onBack() 
        }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White) }
        Spacer(modifier = Modifier.height(24.dp))
        Text(stringResource(R.string.spresso_pulse), color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
        Spacer(modifier = Modifier.height(48.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(stringResource(R.string.hardware_status), color = Color.Gray, fontSize = 10.sp)
                Text(
                    if (session != null) stringResource(R.string.connected).uppercase() else stringResource(R.string.disconnected).uppercase(),
                    color = if (session != null) Color.Green else Color.Red,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
