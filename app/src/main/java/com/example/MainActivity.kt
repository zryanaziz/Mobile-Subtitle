package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// Private package-wide constant for default user web portal target application.
private const val DEFAULT_PORTAL_URL = "https://aistudio.google.com/apps/297912a7-35fc-4bd7-9863-c982e788d247?showPreview=true&showAssistant=true"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    WebPortalApp(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

/**
 * Robust ViewModel holding our Web Portal configurations and ephemeral state properties.
 */
class WebPortalViewModel(context: Context) : ViewModel() {
    private val sharedPrefs = context.getSharedPreferences("WebPortalPreferences", Context.MODE_PRIVATE)

    private val _currentUrl = MutableStateFlow(
        sharedPrefs.getString("target_portal_url", DEFAULT_PORTAL_URL) ?: DEFAULT_PORTAL_URL
    )
    val currentUrl = _currentUrl.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _progress = MutableStateFlow(0.0f)
    val progress = _progress.asStateFlow()

    private val _isOffline = MutableStateFlow(false)
    val isOffline = _isOffline.asStateFlow()

    private val _canGoBack = MutableStateFlow(false)
    val canGoBack = _canGoBack.asStateFlow()

    private val _canGoForward = MutableStateFlow(false)
    val canGoForward = _canGoForward.asStateFlow()

    private val _showSplash = MutableStateFlow(true)
    val showSplash = _showSplash.asStateFlow()

    private val _showSettingsDialog = MutableStateFlow(false)
    val showSettingsDialog = _showSettingsDialog.asStateFlow()

    private val _showInfoDialog = MutableStateFlow(false)
    val showInfoDialog = _showInfoDialog.asStateFlow()

    private val _webTitle = MutableStateFlow("Loading Web App...")
    val webTitle = _webTitle.asStateFlow()

    init {
        _isOffline.value = !verifyNetworkConnectivity(context)
    }

    fun updatePortalUrl(newUrl: String, context: Context) {
        var trimmed = newUrl.trim()
        if (trimmed.isNotEmpty()) {
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                trimmed = "https://$trimmed"
            }
            _currentUrl.value = trimmed
            sharedPrefs.edit().putString("target_portal_url", trimmed).apply()
            _isOffline.value = !verifyNetworkConnectivity(context)
        }
    }

    fun restoreDefaultUrl(context: Context) {
        _currentUrl.value = DEFAULT_PORTAL_URL
        sharedPrefs.edit().putString("target_portal_url", DEFAULT_PORTAL_URL).apply()
        _isOffline.value = !verifyNetworkConnectivity(context)
    }

    fun updateLoadingState(loading: Boolean) {
        _isLoading.value = loading
    }

    fun updateLoadingProgress(newProgress: Float) {
        _progress.value = newProgress
    }

    fun updateOfflineState(offline: Boolean) {
        _isOffline.value = offline
    }

    fun updateNavigationState(back: Boolean, forward: Boolean) {
        _canGoBack.value = back
        _canGoForward.value = forward
    }

    fun updateWebTitle(newTitle: String) {
        val trimmedTitle = newTitle.trim()
        if (trimmedTitle.isNotEmpty() && !trimmedTitle.startsWith("http") && trimmedTitle != "about:blank") {
            _webTitle.value = trimmedTitle
        }
    }

    fun toggleSplashState(show: Boolean) {
        _showSplash.value = show
    }

    fun toggleSettingsDialog(show: Boolean) {
        _showSettingsDialog.value = show
    }

    fun toggleInfoDialog(show: Boolean) {
        _showInfoDialog.value = show
    }

    fun verifyNetworkConnectivity(context: Context): Boolean {
        val mgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager? ?: return false
        val network = mgr.activeNetwork ?: return false
        val caps = mgr.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
               caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
               caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}

/**
 * Main application composable containing fully layout configurations, transitions and interactions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebPortalApp(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    
    // Core Compose ViewModel initialization using a universally compatible Factory object template
    val viewModel: WebPortalViewModel = viewModel(
        factory = remember {
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return WebPortalViewModel(context) as T
                }
            }
        }
    )

    val urlState by viewModel.currentUrl.collectAsState()
    val isLoadingState by viewModel.isLoading.collectAsState()
    val progressState by viewModel.progress.collectAsState()
    val isOfflineState by viewModel.isOffline.collectAsState()
    val canGoBackState by viewModel.canGoBack.collectAsState()
    val canGoForwardState by viewModel.canGoForward.collectAsState()
    val showSplashState by viewModel.showSplash.collectAsState()
    val showSettingsDialogState by viewModel.showSettingsDialog.collectAsState()
    val showInfoDialogState by viewModel.showInfoDialog.collectAsState()
    val webTitleState by viewModel.webTitle.collectAsState()

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    // Intercept back actions for fluid system gesture back support
    BackHandler(enabled = canGoBackState && !isOfflineState) {
        webViewInstance?.goBack()
    }

    // Splash timer handler
    LaunchedEffect(Unit) {
        delay(2300)
        viewModel.toggleSplashState(false)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Elegant linear glass bar showing active page title / status and options
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(1.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Portal Navigation",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                            Text(
                                text = if (isOfflineState) "Connection Offline" else webTitleState,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = { viewModel.toggleSettingsDialog(true) },
                            modifier = Modifier.testTag("app_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Portal Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { viewModel.toggleInfoDialog(true) },
                            modifier = Modifier.testTag("app_info_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Portal Information",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Precise linear progress indicator directly nested beneath titles
                    if (isLoadingState && !isOfflineState) {
                        LinearProgressIndicator(
                            progress = { progressState },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.5.dp)
                                .testTag("page_progress_bar"),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(2.5.dp))
                    }
                }
            }

            // Centralized web rendering viewport
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.White)
            ) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("portal_web_view"),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )

                            // Apply fully responsive and advanced engine settings to web elements
                            @SuppressLint("SetJavaScriptEnabled")
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                javaScriptCanOpenWindowsAutomatically = true
                                allowContentAccess = true
                                allowFileAccess = true
                                builtInZoomControls = true
                                displayZoomControls = false
                                cacheMode = WebSettings.LOAD_DEFAULT
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                
                                // Standard bypass string representing fully featured desktop Chrome browser on Pixel 7 mobile
                                // bypassing generic WebView login policies
                                userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, run.app; like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    viewModel.updateLoadingState(true)
                                    viewModel.updateOfflineState(false)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    viewModel.updateLoadingState(false)
                                    viewModel.updateNavigationState(
                                        back = view?.canGoBack() ?: false,
                                        forward = view?.canGoForward() ?: false
                                    )
                                    url?.let {
                                        viewModel.updateWebTitle(view?.title ?: "")
                                    }
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    super.onReceivedError(view, request, error)
                                    if (request?.isForMainFrame == true) {
                                        viewModel.updateOfflineState(true)
                                        viewModel.updateLoadingState(false)
                                        // Load comfortable blank page to swallow default browser grey screens
                                        view?.loadUrl("about:blank")
                                    }
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    super.onProgressChanged(view, newProgress)
                                    viewModel.updateLoadingProgress(newProgress / 100.0f)
                                }

                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    super.onReceivedTitle(view, title)
                                    title?.let { viewModel.updateWebTitle(it) }
                                }

                                override fun onPermissionRequest(request: PermissionRequest?) {
                                    // Grant requested web permissions (camera, location, audio context)
                                    request?.grant(request.resources)
                                }
                            }
                        }.also {
                            webViewInstance = it
                        }
                    },
                    update = { view ->
                        // Re-trigger updates smoothly if current flow target updates
                        if (view.url != urlState && view.url != "about:blank") {
                            view.loadUrl(urlState)
                        }
                    }
                )

                // High fidelity responsive Native Offline overlay screen matching the dynamic theme
                if (isOfflineState) {
                    OfflineScreen(
                        onRetry = {
                            viewModel.verifyNetworkConnectivity(context)
                            webViewInstance?.loadUrl(urlState)
                        },
                        onConfigure = { viewModel.toggleSettingsDialog(true) }
                    )
                }
            }
        }

        // Floating pill control capsule for standard thumb reaching navigation
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            if (!showSplashState) {
                ControlCapsule(
                    canGoBack = canGoBackState && !isOfflineState,
                    canGoForward = canGoForwardState && !isOfflineState,
                    isLoading = isLoadingState && !isOfflineState,
                    onBack = { webViewInstance?.goBack() },
                    onForward = { webViewInstance?.goForward() },
                    onRefresh = {
                        if (isLoadingState) {
                            webViewInstance?.stopLoading()
                        } else {
                            viewModel.verifyNetworkConnectivity(context)
                            webViewInstance?.loadUrl(urlState)
                        }
                    },
                    onHome = {
                        webViewInstance?.loadUrl(urlState)
                    }
                )
            }
        }

        // Premium Fade Splash Layer
        if (showSplashState) {
            SplashOverlay()
        }

        // Settings Dialog Composable
        if (showSettingsDialogState) {
            SettingsDialog(
                currentUrl = urlState,
                onDismiss = { viewModel.toggleSettingsDialog(false) },
                onSave = { newUrl ->
                    viewModel.updatePortalUrl(newUrl, context)
                    webViewInstance?.loadUrl(newUrl)
                    viewModel.toggleSettingsDialog(false)
                },
                onReset = {
                    viewModel.restoreDefaultUrl(context)
                    webViewInstance?.loadUrl(DEFAULT_PORTAL_URL)
                    viewModel.toggleSettingsDialog(false)
                }
            )
        }

        // Info Portal metadata info sheet dialog
        if (showInfoDialogState) {
            InfoDialog(
                currentUrl = urlState,
                onDismiss = { viewModel.toggleInfoDialog(false) }
            )
        }
    }
}

/**
 * Controller Capsule Composable holding fluid touch interactions and responsive actions.
 */
@Composable
fun ControlCapsule(
    canGoBack: Boolean,
    canGoForward: Boolean,
    isLoading: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefresh: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .shadow(12.dp, shape = RoundedCornerShape(28.dp))
            .background(Color.Transparent)
            .testTag("app_navigation_capsule"),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onBack,
                enabled = canGoBack,
                modifier = Modifier.testTag("nav_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Navigate Back",
                    tint = if (canGoBack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }

            IconButton(
                onClick = onHome,
                modifier = Modifier.testTag("nav_home_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Portal Home",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(
                onClick = onRefresh,
                modifier = Modifier.testTag("nav_refresh_button")
            ) {
                Icon(
                    imageVector = if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
                    contentDescription = if (isLoading) "Stop Loading" else "Reload Web Application",
                    tint = MaterialTheme.colorScheme.secondary
                )
            }

            IconButton(
                onClick = onForward,
                enabled = canGoForward,
                modifier = Modifier.testTag("nav_forward_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Navigate Forward",
                    tint = if (canGoForward) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        }
    }
}

/**
 * Modern High-Contrast Splash Screen Overlay. Will display our generated app logo.
 */
@Composable
fun SplashOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F0B1E),
                        Color(0xFF14122D)
                    )
                )
            )
            .testTag("app_splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Surface(
                modifier = Modifier
                    .size(110.dp)
                    .scale(alphaAnim)
                    .shadow(16.dp, CircleShape),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.2f)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "AI App Logo",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .clip(CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "AI APP PORTAL",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.5.sp,
                    color = Color.White
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Syncing Secure Web Sessions...",
                style = MaterialTheme.typography.bodyMedium.copy(
                    letterSpacing = 0.5.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            )

            Spacer(modifier = Modifier.height(48.dp))

            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = Color(0xFFD0BCFF),
                strokeWidth = 3.dp
            )
        }
    }
}

/**
 * Native Offline Error Overlay Composable. Replaces standard webview errors.
 */
@Composable
fun OfflineScreen(
    onRetry: () -> Unit,
    onConfigure: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(24.dp))
                .testTag("offline_error_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning icon",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Connection Offline",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "We're unable to load the web application. Please make sure cellular data or Wi-Fi is active and try reloading, or re-configure your target address.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("offline_retry_button"),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry Connection", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onConfigure,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Customize Target URL",
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Configure target Web View address dialog page.
 */
@Composable
fun SettingsDialog(
    currentUrl: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onReset: () -> Unit
) {
    var textState by remember { mutableStateOf(currentUrl) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(2.dp)
                .testTag("url_settings_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Configure Target URL",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Paste or enter the address of your AI Studio application or any web app to wrap it inside the local container.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                OutlinedTextField(
                    value = textState,
                    onValueChange = { textState = it },
                    label = { Text("App URL (https://...)") },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_url_input"),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    ),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onReset,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset Default", fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", color = MaterialTheme.colorScheme.secondary)
                    }
                }

                Button(
                    onClick = { onSave(textState) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .testTag("settings_save_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Save Workspace", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Information metadata details modal card.
 */
@Composable
fun InfoDialog(
    currentUrl: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "About AI App Portal",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "This application serves as a high-fidelity container and secure browser portal for your Google AI Studio web apps. It features edge-to-edge system styling, bottom navigation pills, a secure mobile user agent to enable simple Google account logins, and automatic offline verification.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "ACTIVE PORTAL REFERENCE:",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentUrl,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Acknowledge", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Standard placeholder Composable to keep Roborazzi Greeting screenshots working.
 */
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Hello $name!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "AI App Portal is ready to launch.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}
