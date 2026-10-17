package musicorbs.platform.audio

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

/** Только debug: второй настоящий focus client, не открывающий окно или аудио. */
class AudioFocusProbeService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var request: AudioFocusRequest? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        main.removeCallbacksAndMessages(null)
        abandon()
        val holdMs = (intent?.getIntExtra("holdMs", 1500) ?: 1500).coerceIn(500, 5000)
        val manager = getSystemService(AudioManager::class.java)
        val next = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener { }
            .setAcceptsDelayedFocusGain(false)
            .build()
        try {
            if (manager.requestAudioFocus(next) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                request = next
                Log.i(TAG, "Transient focus granted; release in ${holdMs}ms")
                main.postDelayed({ abandon(); stopSelf() }, holdMs.toLong())
            } else {
                Log.i(TAG, "Transient focus denied")
                stopSelf()
            }
        } catch (failure: RuntimeException) {
            Log.w(TAG, "Focus probe failed", failure)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun abandon() {
        val previous = request
        request = null
        previous?.let { runCatching { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) } }
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        abandon()
        Log.i(TAG, "Transient focus released")
        super.onDestroy()
    }

    companion object { private const val TAG = "MusicOrbsFocusProbe" }
}
