package ar.truco.app.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import androidx.core.graphics.toColorInt
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * La mesa del anfitrión es la misma página web que ven los invitados, servida desde
 * `127.0.0.1`, dentro de un WebView: una sola UI para todos.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PantallaMesa(url: String, alVolver: () -> Unit) {
    BackHandler(onBack = alVolver)

    val contexto = LocalContext.current
    DisposableEffect(Unit) {
        val actividad = contexto.actividad()
        actividad?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            actividad?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor("#0F2A20".toColorInt())
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                // `chrome://inspect` en builds de debug para revisar la mesa desde la PC.
                if (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                    WebView.setWebContentsDebuggingEnabled(true)
                }
                webViewClient = WebViewClient()
                loadUrl(url)
            }
        },
    )
}

private fun Context.actividad(): Activity? {
    var actual: Context = this
    while (actual is ContextWrapper) {
        if (actual is Activity) return actual
        actual = actual.baseContext
    }
    return null
}
