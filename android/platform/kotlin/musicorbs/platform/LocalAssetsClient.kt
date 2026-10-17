package musicorbs.platform

import android.content.res.AssetManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.io.IOException
import musicorbs.webassets.LocalAssetPolicy

/** Единственный origin обслуживается из APK; остальные запросы закрыты. */
open class LocalAssetsClient(private val assets: AssetManager) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        !LocalAssetPolicy.accepts(request.url.toString())

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
        if (request.method != "GET") return rejected(403, "Forbidden")
        val path = LocalAssetPolicy.path(request.url.toString()) ?: return rejected(403, "Forbidden")
        return try {
            WebResourceResponse(mime(path), "UTF-8", 200, "OK", mapOf(
                "Content-Security-Policy" to "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; connect-src 'none'; object-src 'none'; frame-src 'none'; base-uri 'none'",
                "X-Content-Type-Options" to "nosniff"
            ), assets.open(path))
        } catch (_: IOException) {
            rejected(404, "Not Found")
        }
    }

    private fun rejected(status: Int, reason: String) = WebResourceResponse(
        "text/plain", "UTF-8", status, reason, emptyMap(), ByteArrayInputStream(ByteArray(0))
    )

    private fun mime(path: String): String = when (path.substringAfterLast('.', "")) {
        "html" -> "text/html"
        "js", "mjs" -> "application/javascript"
        "css" -> "text/css"
        "json" -> "application/json"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "svg" -> "image/svg+xml"
        "woff2" -> "font/woff2"
        "wasm" -> "application/wasm"
        else -> "application/octet-stream"
    }

    companion object {
        const val INDEX_URL = LocalAssetPolicy.INDEX_URL
    }
}
