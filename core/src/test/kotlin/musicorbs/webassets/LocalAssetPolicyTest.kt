package musicorbs.webassets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalAssetPolicyTest {
    @Test fun localBundlePathsRemainRelative() {
        assertEquals("index.html", LocalAssetPolicy.path(LocalAssetPolicy.INDEX_URL))
        assertEquals("assets/scene.js", LocalAssetPolicy.path("https://${LocalAssetPolicy.HOST}/assets/scene.js?v=1"))
    }

    @Test fun remoteAndPrivilegedOriginsAreRejected() {
        listOf(
            "https://example.com/index.html",
            "https://${LocalAssetPolicy.HOST}.example.com/index.html",
            "https://${LocalAssetPolicy.HOST}@example.com/index.html",
            "https://user@${LocalAssetPolicy.HOST}/index.html",
            "https://${LocalAssetPolicy.HOST}:8443/index.html",
            "http://${LocalAssetPolicy.HOST}/index.html",
            "file:///android_asset/index.html",
            "content://media/external/audio/1",
            "javascript:alert(1)", "data:text/html,test", "not a URI"
        ).forEach { assertNull(LocalAssetPolicy.path(it), it) }
    }

    @Test fun traversalAndInvalidPathsAreRejected() {
        listOf("../index.html", "assets/../../index.html", "%2e%2e/index.html", "assets/%2e%2e/index.html", "%5cindex.html", "%00index.html", "/index.html", "")
            .forEach { assertNull(LocalAssetPolicy.path("https://${LocalAssetPolicy.HOST}/$it"), it) }
    }
}
