package musicorbs.bootstrap

import android.app.Activity
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import musicorbs.platform.LocalAssetsClient

/** Создание платформенных зависимостей; аудио не запускается при открытии. */
class MainActivity : Activity() {
    private lateinit var scene: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scene = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = false
                allowFileAccess = false
                allowContentAccess = false
                blockNetworkLoads = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                mediaPlaybackRequiresUserGesture = true
                setSupportMultipleWindows(false)
            }
            webViewClient = LocalAssetsClient(assets)
        }
        setContentView(scene)
        scene.loadUrl(LocalAssetsClient.INDEX_URL)
    }

    override fun onPause() {
        scene.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::scene.isInitialized) scene.onResume()
    }

    override fun onDestroy() {
        scene.stopLoading()
        scene.removeAllViews()
        scene.destroy()
        super.onDestroy()
    }
}
