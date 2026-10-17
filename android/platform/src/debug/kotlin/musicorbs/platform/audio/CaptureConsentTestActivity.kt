package musicorbs.platform.audio

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import kotlinx.coroutines.CompletableDeferred

/** Только debug APK: системное согласие для instrumentation; token не сохраняется. */
class CaptureConsentTestActivity : Activity() {
    fun requestConsent() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        val intent = if (Build.VERSION.SDK_INT >= 34) manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
            else manager.createScreenCaptureIntent()
        @Suppress("DEPRECATION")
        startActivityForResult(intent, 41)
    }

    @Deprecated("Используется только debug instrumentation")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 41) result?.complete(resultCode to data)
    }

    companion object {
        @Volatile var result: CompletableDeferred<Pair<Int, Intent?>>? = null
    }
}
