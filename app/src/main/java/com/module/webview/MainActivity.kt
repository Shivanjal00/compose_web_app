package com.module.webview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.module.webview.ui.theme.WebviewTheme

class MainActivity : ComponentActivity() {

    companion object {
        private var webViewState: Bundle? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WebviewTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    WebViewScreen(
                        url = "https://www.google.com", // Replace with your website URL
                        savedState = webViewState,
                        onSaveState = { webViewState = it },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    url: String,
    savedState: Bundle? = null,
    onSaveState: (Bundle) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isLoading by remember { mutableStateOf(true) }
    var loadProgress by remember { mutableStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var webView: WebView? by remember { mutableStateOf(null) }
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    val context = LocalContext.current
    val activity = context as? ComponentActivity

    // Save WebView state on disposal
    DisposableEffect(Unit) {
        onDispose {
            webView?.let { wv ->
                val bundle = Bundle()
                wv.saveState(bundle)
                onSaveState(bundle)
            }
        }
    }

    // Handle back press
    BackHandler(enabled = customView != null || canGoBack) {
        when {
            customView != null -> {
                // Exit fullscreen if in fullscreen mode
                customViewCallback?.onCustomViewHidden()
            }
            canGoBack -> {
                // Navigate back in WebView
                webView?.goBack()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Fullscreen container
        if (customView != null) {
            AndroidView(
                factory = { customView!! },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                canGoBack = view?.canGoBack() ?: false
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                super.onProgressChanged(view, newProgress)
                                loadProgress = newProgress
                            }

                            // Handle fullscreen for videos
                            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                if (customView != null) {
                                    callback?.onCustomViewHidden()
                                    return
                                }

                                customView = view
                                customViewCallback = callback

                                // Hide system UI for fullscreen
                                activity?.window?.decorView?.systemUiVisibility = (
                                        View.SYSTEM_UI_FLAG_FULLSCREEN
                                                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                        )
                            }

                            override fun onHideCustomView() {
                                customView = null
                                customViewCallback?.onCustomViewHidden()
                                customViewCallback = null

                                // Restore system UI
                                activity?.window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                            }
                        }

                        settings.apply {
                            // Enable JavaScript
                            javaScriptEnabled = true

                            // Enable DOM storage
                            domStorageEnabled = true

                            // Enable database
                            databaseEnabled = true

                            // Enable zoom controls
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false

                            // Enable responsive design
                            useWideViewPort = true
                            loadWithOverviewMode = true

                            // Enable caching
                            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT

                            // Enable media playback without user gesture
                            mediaPlaybackRequiresUserGesture = false

                            // Enable safe browsing
                            safeBrowsingEnabled = true

                            // Allow file access
                            allowFileAccess = true
                            allowContentAccess = true

                            // Mixed content mode (for HTTPS sites loading HTTP resources)
                            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                            // Support multiple windows
                            setSupportMultipleWindows(true)

                            // Enable plugins (for video playback)
                            javaScriptCanOpenWindowsAutomatically = true
                        }

                        // Restore state if available, otherwise load URL
                        if (savedState != null) {
                            restoreState(savedState)
                        } else {
                            loadUrl(url)
                        }

                        webView = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Loading indicator (only show when not in fullscreen)
            if (isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(
                        progress = { loadProgress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Back button (only show when not in fullscreen)
            if (canGoBack) {
                FloatingActionButton(
                    onClick = { webView?.goBack() },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text("←", style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
    }
}