package musicorbs.platform.audio

import android.Manifest
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*
import musicorbs.bootstrap.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** FT-14/15/19: настоящий consent, PCM, DSP и UI; synthetic источник вместо плеера. */
@RunWith(AndroidJUnit4::class)
class MainCaptureSignalTest {
    @get:Rule val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    @Test fun silentCaptureExplainsMissingSignalAndRhythmicPlaybackChangesRotation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val savedSession = P7SessionPreserver(context)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val probe = P7MainUiProbe(scenario)
        val manager = context.getSystemService(AudioManager::class.java)
        val volume = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        var track: AudioTrack? = null
        var baseline: JsonObject? = null
        try {
            probe.ready()
            probe.closePanelIfOpen()
            val initial = probe.snapshot("capture-signal-baseline")
            baseline = initial
            val settings = initial.getValue("settings").jsonObject
            assertTrue("Enable Music Sync before this positive capture test", settings.getValue("musicSync").jsonPrimitive.boolean)
            manager.setStreamVolume(AudioManager.STREAM_MUSIC, manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 2, 0)
            track = p7SyntheticTrack(AudioAttributes.ALLOW_CAPTURE_BY_NONE)
            probe.click("Настройки")
            probe.click("Захват внешнего воспроизведения", scroll = true)
            probe.acceptCaptureConsent()
            probe.awaitMotion("Capture consent must open a source") {
                it.getValue("sourceSessionId").jsonPrimitive.content != "none"
            }
            probe.closePanelIfOpen()
            awaitStatus(probe, "Звук не поступает.")
            assertTrue("Capture must never start microphone fallback", !probe.device.hasObject(By.desc("Выбран микрофон")))
            val silent = probe.motion()
            val sourceId = silent.getValue("sourceSessionId").jsonPrimitive.content
            assertTrue(sourceId != "none")
            assertEquals("autonomous", silent.getValue("mode").jsonPrimitive.content)
            p7Evidence("main-capture-no-signal", "sourceId" to sourceId,
                "energy" to silent.getValue("energy").jsonPrimitive.double, "visibleWarning" to true)
            track.stop(); track.release(); track = null

            for (bpm in listOf(90, 150)) {
                track = rhythmicTrack(bpm)
                val synced = awaitTempo(probe, bpm)
                assertEquals(sourceId, synced.getValue("sourceSessionId").jsonPrimitive.content)
                awaitStatus(probe, "Синхронизация:")
                p7Evidence("main-capture-tempo", "expectedBpm" to bpm,
                    "bpm" to synced.getValue("bpm").jsonPrimitive.double,
                    "period" to synced.getValue("slowCycleSeconds").jsonPrimitive.double,
                    "energy" to synced.getValue("energy").jsonPrimitive.double)
                track.stop(); track.release(); track = null
                // История темпа устаревает до следующего synthetic трека, consent сохраняется.
                delay(4000)
            }
            assertEquals(initial.getValue("shapes"), probe.snapshot("capture-signal-invariants").getValue("shapes"))
        } finally {
            try {
                runCatching { track?.stop() }; runCatching { track?.release() }
                manager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                baseline?.let { probe.restoreScene(it) }
            } finally {
                try { scenario.close() } finally { try { probe.close() } finally { savedSession.restore() } }
            }
        }
    }

    private suspend fun awaitTempo(probe: P7MainUiProbe, bpm: Int): JsonObject {
        var latest: JsonObject? = null
        // Бюджет ожидания автоматизации на AVD; допуски BPM/периода не меняются.
        // Монотонный clock намеренно ограничивает dt после зависания UI.
        val synced = withTimeoutOrNull(45000) {
            while (true) {
                val value = try { probe.motion() } catch (_: TimeoutCancellationException) {
                    currentCoroutineContext().ensureActive()
                    delay(100); continue
                }
                latest = value
                if (value.getValue("mode").jsonPrimitive.content == "synced" &&
                    abs((value.getValue("bpm").jsonPrimitive.doubleOrNull ?: 0.0) - bpm) <= 2 &&
                    abs(value.getValue("slowCycleSeconds").jsonPrimitive.double - 60.0 / bpm * 32) < 0.4)
                    return@withTimeoutOrNull value
                delay(200)
            }
            @Suppress("UNREACHABLE_CODE") null
        }
        checkNotNull(synced) { "Captured $bpm BPM did not converge: $latest" }
        return synced
    }

    private suspend fun awaitStatus(probe: P7MainUiProbe, prefix: String) = withTimeout(15000) {
        // Проверяем фактический текст Compose live-region при непрерывной анимации.
        while (probe.device.findObjects(By.pkg("musicorbs.app").clazz("android.widget.TextView"))
                .none { it.text?.startsWith(prefix) == true }) delay(200)
    }

    private fun rhythmicTrack(bpm: Int): AudioTrack {
        val samples = ShortArray(48000 * 2) { index ->
            val time = index / 48000.0
            val beat = time % (60.0 / bpm)
            val drum = if (beat < 0.08) 0.18 * sin(2 * PI * 1000 * beat) * (1 - beat / 0.08) else 0.0
            ((0.3 * sin(2 * PI * 220 * time) + drum) * 32767).toInt().toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_ALL).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(samples.size * 2).build()
        try {
            assertEquals(samples.size, track.write(samples, 0, samples.size))
            assertEquals(AudioTrack.SUCCESS, track.setLoopPoints(0, samples.size, -1))
            track.play()
            return track
        } catch (failure: Throwable) { track.release(); throw failure }
    }
}
