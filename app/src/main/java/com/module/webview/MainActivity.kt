package com.module.webview

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.module.webview.ui.theme.WebviewTheme
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            WebviewTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    WebViewScreen("https://eminentfliex.com")
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun WebViewScreen(url: String) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    var nativeVideoUrl by remember { mutableStateOf<String?>(null) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isPlayerLocked by remember { mutableStateOf(false) }

    val jsInterface = remember {
        object {
            @JavascriptInterface
            fun playNative(videoUrl: String) {
                if (!isPlayerLocked && videoUrl.isNotEmpty() && nativeVideoUrl == null) {
                    activity.runOnUiThread {
                        // Pause all videos in WebView before opening native player
                        webViewInstance?.evaluateJavascript("""
                            (function() {
                                var videos = document.querySelectorAll('video');
                                videos.forEach(function(v) {
                                    v.pause();
                                });
                            })();
                        """, null)

                        nativeVideoUrl = videoUrl
                        isPlayerLocked = true
                    }
                }
            }
        }
    }

    val closePlayer = {
        nativeVideoUrl = null
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        toggleSystemUI(activity, true)

        // Stay locked for 3 seconds after closing to prevent immediate re-triggering
        activity.runOnUiThread {
            webViewInstance?.evaluateJavascript("if(window.scanner) clearInterval(window.scanner);", null)
            Thread {
                Thread.sleep(3000)
                isPlayerLocked = false
            }.start()
        }
    }

    BackHandler {
        if (nativeVideoUrl != null) closePlayer()
        else if (webViewInstance?.canGoBack() == true) webViewInstance?.goBack()
        else activity.finish()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewInstance = this
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                    }
                    addJavascriptInterface(jsInterface, "AppBridge")

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            view?.evaluateJavascript("""
                                (function() {
                                    if (window.scanner) clearInterval(window.scanner);
                                    window.scanner = setInterval(function() {
                                        var v = document.querySelector('video');
                                        if (v && v.src && v.src.length > 5) {
                                            window.AppBridge.playNative(v.src);
                                        }
                                    }, 1500);
                                })();
                            """, null)
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onShowCustomView(view: android.view.View?, callback: CustomViewCallback?) {
                            // Prevent WebView's default fullscreen - use native player instead
                            callback?.onCustomViewHidden()
                        }
                    }

                    loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (nativeVideoUrl != null) {
            NetflixPlayer(url = nativeVideoUrl!!) { closePlayer() }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetflixPlayer(url: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val exoPlayer = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }

    var isVisible by remember { mutableStateOf(true) }
    var currentPos by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) exoPlayer.play()
            }
            override fun onIsPlayingChanged(p: Boolean) { isPlaying = p }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPos = exoPlayer.currentPosition
            totalDuration = exoPlayer.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    LaunchedEffect(Unit) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        toggleSystemUI(activity, false)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)
        .pointerInput(Unit) { detectTapGestures(onTap = { isVisible = !isVisible }) }
    ) {
        AndroidView(
            factory = { PlayerView(it).apply {
                player = exoPlayer
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            } },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(visible = isVisible, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(
                listOf(Color.Black.copy(0.7f), Color.Transparent, Color.Black.copy(0.8f))
            ))) {

                IconButton(onClick = onClose, modifier = Modifier.padding(16.dp).align(Alignment.TopStart)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(28.dp))
                }

                Row(modifier = Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { exoPlayer.seekTo(currentPos - 10000) }, modifier = Modifier.size(80.dp)) {
                        Icon(Icons.Default.KeyboardArrowLeft, null, tint = Color.White, modifier = Modifier.size(45.dp))
                    }
                    Spacer(Modifier.width(30.dp))

                    IconButton(onClick = { if(isPlaying) exoPlayer.pause() else exoPlayer.play() }, modifier = Modifier.size(100.dp)) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(85.dp)
                        )
                    }
                    Spacer(Modifier.width(30.dp))

                    IconButton(onClick = { exoPlayer.seekTo(currentPos + 10000) }, modifier = Modifier.size(80.dp)) {
                        Icon(Icons.Default.KeyboardArrowRight, null, tint = Color.White, modifier = Modifier.size(45.dp))
                    }
                }

                Column(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp, start = 40.dp, end = 40.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTime(currentPos), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text(formatTime(totalDuration), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Slider(
                        value = if (totalDuration > 0) currentPos.toFloat() / totalDuration.toFloat() else 0f,
                        onValueChange = { exoPlayer.seekTo((it * totalDuration).toLong()) },
                        modifier = Modifier.fillMaxWidth().height(12.dp),
                        thumb = { Box(modifier = Modifier.size(10.dp).background(Color.Red, CircleShape)) },
                        track = { sliderState ->
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                modifier = Modifier.height(2.dp),
                                thumbTrackGapSize = 0.dp,
                                colors = SliderDefaults.colors(activeTrackColor = Color.Red, inactiveTrackColor = Color.White.copy(0.2f))
                            )
                        }
                    )
                }
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val h = TimeUnit.MILLISECONDS.toHours(ms)
    val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}

fun toggleSystemUI(activity: ComponentActivity, show: Boolean) {
    val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
    if (show) controller.show(WindowInsetsCompat.Type.systemBars())
    else controller.hide(WindowInsetsCompat.Type.systemBars())
}