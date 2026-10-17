package musicorbs.webassets

import java.net.URI

/** Политика адресов APK assets, независимая от Android/WebView. */
object LocalAssetPolicy {
    const val HOST = "appassets.androidplatform.net"
    const val INDEX_URL = "https://$HOST/index.html"

    fun accepts(url: String): Boolean = path(url) != null

    fun path(url: String): String? {
        val uri = try { URI(url) } catch (_: IllegalArgumentException) { return null }
        catch (_: java.net.URISyntaxException) { return null }
        if (uri.scheme != "https" || uri.host != HOST || uri.port != -1 || uri.userInfo != null) return null
        val path = uri.path ?: return null
        if (!path.startsWith('/') || path.startsWith("//") || '\\' in path || '\u0000' in path) return null
        val relative = path.removePrefix("/")
        if (relative.isBlank() || relative.split('/').any { it == ".." || it == "." }) return null
        return relative
    }
}
