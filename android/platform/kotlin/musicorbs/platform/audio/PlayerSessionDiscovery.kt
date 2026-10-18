package musicorbs.platform.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.audiofx.AudioEffect
import android.os.SystemClock
import androidx.core.content.ContextCompat
import musicorbs.audio.application.ports.PlayerSessionDiscoveryPort
import musicorbs.audio.domain.PlayerSessionCandidate
import java.util.UUID

/** Только RAM-подсказки. Ни manifest receiver, ни discovery не открывают аудио. */
internal object PlayerSessionAnnouncements {
    private data class Entry(val nativeId: Int, val candidate: PlayerSessionCandidate)
    private val entries = LinkedHashMap<Pair<String, Int>, Entry>()
    @Synchronized fun receive(intent: Intent) {
        if (intent.action != AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION && intent.action != AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION) return
        val id = runCatching { intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1) }.getOrDefault(-1)
        val pkg = runCatching { intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) }.getOrNull() ?: return
        if (id <= 0 || pkg.length !in 3..255 || !pkg.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))) return
        val key = pkg to id
        if (intent.action == AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION) { entries.remove(key); return }
        val token = entries.remove(key)?.candidate?.sessionToken ?: UUID.randomUUID().toString()
        if (entries.size >= 32) entries.remove(entries.keys.first())
        entries[key] = Entry(id, PlayerSessionCandidate(token, pkg, SystemClock.elapsedRealtimeNanos() / 1e9))
    }
    @Synchronized fun snapshot() = entries.values.map { it.candidate }
    @Synchronized fun resolve(token: String): Int? = entries.values.firstOrNull { it.candidate.sessionToken == token }?.nativeId
    internal fun clearForTest() = synchronized(this) { entries.clear() }
}

class PlayerSessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { PlayerSessionAnnouncements.receive(intent) }
}

class AndroidPlayerSessionDiscovery(private val context: Context) : PlayerSessionDiscoveryPort {
    private val receiver = PlayerSessionReceiver()
    private var started = false
    override fun start() {
        if (started) return
        val filter = IntentFilter(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply { addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION) }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        started = true
    }
    override fun snapshot() = PlayerSessionAnnouncements.snapshot()
    override fun close() { if (started) { context.unregisterReceiver(receiver); started = false } }
}
