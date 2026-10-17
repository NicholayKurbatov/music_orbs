package musicorbs.platform.audio

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.os.FileObserver
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Collections
import java.util.regex.Pattern
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.PI
import kotlin.math.sin

/** Только ограниченные scalar evidence; PCM, массивы и consent сюда не принимаются. */
internal fun p7Evidence(stage: String, vararg fields: Pair<String, Any?>) {
    require(stage.length <= 80 && fields.size <= 24)
    val payload = kotlinx.serialization.json.buildJsonObject {
        put("stage", kotlinx.serialization.json.JsonPrimitive(stage))
        for ((key, value) in fields) {
            require(key.length <= 64)
            val scalar = when (value) {
                null -> kotlinx.serialization.json.JsonNull
                is Boolean -> kotlinx.serialization.json.JsonPrimitive(value)
                is Number -> {
                    require(value.toDouble().isFinite())
                    kotlinx.serialization.json.JsonPrimitive(value)
                }
                is String -> {
                    require(value.length <= 256)
                    kotlinx.serialization.json.JsonPrimitive(value)
                }
                else -> error("Non-scalar test evidence rejected")
            }
            put(key, scalar)
        }
    }.toString()
    require(payload.toByteArray(Charsets.UTF_8).size <= 4096)
    InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("stream", "P7_AUDIO_EVIDENCE $payload\n") })
}

/** Сгенерированный источник, не запись. Политика задаётся отдельно каждому AudioTrack. */
internal fun p7SyntheticTrack(capturePolicy: Int, frequency: Int = 440): AudioTrack {
    val samples = ShortArray(48000) { (sin(2 * PI * frequency * it / 48000) * 12000).toInt().toShort() }
    val track = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setAllowedCapturePolicy(capturePolicy).build())
        .setAudioFormat(AudioFormat.Builder().setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
        .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(samples.size * 2).build()
    try {
        assertEquals(samples.size, track.write(samples, 0, samples.size))
        assertEquals(AudioTrack.SUCCESS, track.setLoopPoints(0, samples.size, -1))
        assertEquals(capturePolicy, track.audioAttributes.allowedCapturePolicy)
        track.play()
        return track
    } catch (failure: Throwable) {
        track.release()
        throw failure
    }
}

internal fun p7SyntheticWave(context: Context): File {
    val sampleRate = 48000
    val count = sampleRate * 5
    val bytes = ByteBuffer.allocate(44 + count * 2).order(ByteOrder.LITTLE_ENDIAN)
    bytes.put("RIFF".toByteArray()).putInt(36 + count * 2).put("WAVEfmt ".toByteArray()).putInt(16)
    bytes.putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
    bytes.put("data".toByteArray()).putInt(count * 2)
    repeat(count) { bytes.putShort((sin(2 * PI * 440 * it / sampleRate) * 12000).toInt().toShort()) }
    return File.createTempFile("music-orbs-p7-synthetic-", ".wav", context.cacheDir).apply { writeBytes(bytes.array()) }
}

internal suspend fun p7ProjectionConsent(scenario: ActivityScenario<CaptureConsentTestActivity>): Pair<Int, Intent> {
    val result = CompletableDeferred<Pair<Int, Intent?>>()
    CaptureConsentTestActivity.result = result
    scenario.onActivity { it.requestConsent() }
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val allow = device.wait(Until.findObject(By.res("android:id/button1")), 10000)
        ?: device.wait(Until.findObject(By.text(Pattern.compile("(?i)(start now|start recording|share screen|начать|начать запись)"))), 5000)
        ?: error("System capture consent button not found")
    allow.click()
    val granted = withTimeout(15000) { result.await() }
    assertEquals(Activity.RESULT_OK, granted.first)
    return granted.first to checkNotNull(granted.second)
}

/**
 * Runtime-аудит файлов приложения, не аудит сети/общего хранилища устройства.
 * Снимки содержат только относительные пути, длины и SHA-256, без содержимого PCM.
 * Любая запись, включая системный cache, приводит к ошибке; allowlist нет.
 */
internal class P7AudioStorageAudit(context: Context) : AutoCloseable {
    private data class Fingerprint(val size: Long, val sha256: String)
    private val roots: List<Pair<String, File>> = buildList {
        add("private" to context.dataDir.canonicalFile)
        context.getExternalFilesDirs(null).filterNotNull().forEachIndexed { index, file -> add("external-files-$index" to file.canonicalFile) }
        context.externalCacheDirs.filterNotNull().forEachIndexed { index, file -> add("external-cache-$index" to file.canonicalFile) }
    }.distinctBy { it.second.absolutePath }
    private val writes = Collections.synchronizedSet(mutableSetOf<String>())
    private val observers = mutableListOf<FileObserver>()
    private val baseline = inventory()

    init {
        for ((label, root) in roots) {
            if (!root.isDirectory) continue
            root.walkTopDown().filter { it.isDirectory }.forEach { directory ->
                require(observers.size < 4096) { "Storage audit directory limit exceeded" }
                @Suppress("DEPRECATION")
                val observer = object : FileObserver(directory.absolutePath,
                    FileObserver.CREATE or FileObserver.MODIFY or FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO or FileObserver.DELETE or FileObserver.MOVED_FROM) {
                    override fun onEvent(event: Int, path: String?) {
                        val relative = directory.relativeTo(root).invariantSeparatorsPath
                        writes.add("$label/$relative/${path ?: "<directory>"}")
                    }
                }
                observer.startWatching()
                observers.add(observer)
            }
        }
    }

    fun assertUnchanged(stage: String) {
        // Callback inotify доставляется асинхронно, включая быстро созданный/удалённый файл.
        android.os.SystemClock.sleep(100)
        val current = inventory()
        val changed = (baseline.keys + current.keys).filter { baseline[it] != current[it] }.sorted()
        val events = synchronized(writes) { writes.toList().sorted() }
        val report = kotlinx.serialization.json.buildJsonObject {
            put("stage", kotlinx.serialization.json.JsonPrimitive(stage))
            put("filesBefore", kotlinx.serialization.json.JsonPrimitive(baseline.size))
            put("filesNow", kotlinx.serialization.json.JsonPrimitive(current.size))
            put("changed", kotlinx.serialization.json.JsonArray(changed.map { kotlinx.serialization.json.JsonPrimitive(it) }))
            put("writeEvents", kotlinx.serialization.json.JsonArray(events.map { kotlinx.serialization.json.JsonPrimitive(it) }))
        }.toString()
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("stream", "P7_AUDIO_STORAGE $report\n") })
        assertTrue("Unexpected file changes at $stage: $report", changed.isEmpty() && events.isEmpty())
    }

    private fun inventory(): Map<String, Fingerprint> = buildMap {
        var totalBytes = 0L
        for ((label, root) in roots) {
            if (!root.exists()) continue
            root.walkTopDown().filter { it.isFile }.forEach { file ->
                require(size < 10000) { "Storage audit file limit exceeded" }
                totalBytes += file.length()
                require(totalBytes <= 256L * 1024 * 1024) { "Storage audit byte limit exceeded" }
                val digest = MessageDigest.getInstance("SHA-256")
                file.inputStream().buffered().use { input ->
                    val buffer = ByteArray(16384)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                put("$label/${file.relativeTo(root).invariantSeparatorsPath}", Fingerprint(file.length(), digest.digest().joinToString("") { "%02x".format(it) }))
            }
        }
    }

    override fun close() { observers.forEach { it.stopWatching() } }
}
