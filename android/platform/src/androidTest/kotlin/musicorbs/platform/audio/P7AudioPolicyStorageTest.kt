package musicorbs.platform.audio

import android.Manifest
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioTrack
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import musicorbs.audio.domain.SourceKind
import musicorbs.audio.domain.SourceRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** FT-19/27: только собственные synthetic источники и доступные каталоги приложения. */
@RunWith(AndroidJUnit4::class)
class P7AudioPolicyStorageTest {
    @get:Rule val recordPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun forbiddenTrackIsSilentBetweenTwoAllowedControlsAndCaptureCreatesNoFiles() = runBlocking {
        val scenario = ActivityScenario.launch(CaptureConsentTestActivity::class.java)
        val host = CaptureProjectionHost(context)
        val manager = context.getSystemService(AudioManager::class.java)
        val volume = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        var adapter: PlaybackCaptureAdapter? = null
        var track: AudioTrack? = null
        var audit: P7AudioStorageAudit? = null
        try {
            val consent = p7ProjectionConsent(scenario)
            // AndroidX откладывает profileInstalled на 5–6 с после первого frame.
            // Ждём реальное завершение verifier до baseline, файл не исключается из аудита.
            val profileStarted = android.os.SystemClock.elapsedRealtime()
            withContext(Dispatchers.IO) {
                // ProfileInstaller — существующая runtime-only зависимость AndroidX.
                // Публичный метод вызывается без добавления compile dependency/изменения APK.
                val verifier = Class.forName("androidx.profileinstaller.ProfileVerifier")
                val future = verifier.getMethod("getCompilationStatusAsync").invoke(null) as java.util.concurrent.Future<*>
                future.get(15, java.util.concurrent.TimeUnit.SECONDS)
            }
            p7Evidence("profile-installer-before-audit", "waitMs" to android.os.SystemClock.elapsedRealtime() - profileStarted,
                "markerBytes" to java.io.File(context.filesDir, "profileInstalled").length())
            audit = P7AudioStorageAudit(context)
            val projection = host.acquire(consent.first, consent.second)
            val origin = System.nanoTime()
            val capture = PlaybackCaptureAdapter(context, projection, { (System.nanoTime() - origin) / 1e9 }) { host.close() }
            adapter = capture
            val session = capture.open(SourceRequest(SourceKind.PLAYBACK_CAPTURE))
            manager.setStreamVolume(AudioManager.STREAM_MUSIC, maxOf(1, manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 2), 0)

            suspend fun allowedControl(): Float {
                return withTimeout(10000) {
                    while (true) {
                        val frame = checkNotNull(capture.read())
                        assertEquals(session.id, frame.sourceSessionId)
                        assertTrue(frame.samples.isNotEmpty() && frame.samples.all { it.isFinite() && it in -1f..1f })
                        val peak = frame.samples.maxOf { abs(it) }
                        if (peak > 0.005f) return@withTimeout peak
                    }
                    @Suppress("UNREACHABLE_CODE") 0f
                }
            }
            track = p7SyntheticTrack(AudioAttributes.ALLOW_CAPTURE_BY_ALL)
            val allowedFirstPeak = allowedControl()
            p7Evidence("capture-policy-allowed-first", "sourceId" to session.id, "peak" to allowedFirstPeak)
            audit.assertUnchanged("capture-allowed-active")
            track.stop(); track.release(); track = null

            // Отдельный реальный track запрещает capture. Старый разрешённый PCM дренируется.
            val denied = p7SyntheticTrack(AudioAttributes.ALLOW_CAPTURE_BY_NONE, frequency = 880)
            track = denied
            withTimeout(5000) { repeat(36) { checkNotNull(capture.read()) } }
            val headBefore = denied.playbackHeadPosition
            var samples = 0
            var peak = 0f
            withTimeout(6000) {
                repeat(48) {
                    val frame = checkNotNull(capture.read())
                    assertEquals(session.id, frame.sourceSessionId)
                    assertTrue(frame.samples.isNotEmpty() && frame.samples.all { it.isFinite() && it in -1f..1f })
                    samples += frame.samples.size
                    peak = maxOf(peak, frame.samples.maxOf { abs(it) })
                }
            }
            val headAfter = denied.playbackHeadPosition
            p7Evidence("capture-policy-forbidden", "sourceId" to session.id, "allowedFirstPeak" to allowedFirstPeak,
                "deniedPeak" to peak, "samples" to samples, "headBefore" to headBefore, "headAfter" to headAfter,
                "playState" to denied.playState, "capturePolicy" to denied.audioAttributes.allowedCapturePolicy)
            assertTrue("Forbidden source must keep playing during silent capture", headAfter > headBefore)
            assertEquals(AudioTrack.PLAYSTATE_PLAYING, denied.playState)
            assertTrue(samples >= 48000)
            assertTrue("ALLOW_CAPTURE_BY_NONE leaked PCM: peak=$peak", peak <= 1f / 32768f)
            audit.assertUnchanged("capture-forbidden-active")
            denied.stop(); denied.release(); track = null

            // Тишина не считается доказательством работающего recorder без этого второго контроля.
            track = p7SyntheticTrack(AudioAttributes.ALLOW_CAPTURE_BY_ALL)
            val allowedSecondPeak = allowedControl()
            p7Evidence("capture-policy-allowed-second", "sourceId" to session.id, "peak" to allowedSecondPeak,
                "allowedFirstPeak" to allowedFirstPeak, "deniedPeak" to peak, "deniedSamples" to samples)
            track.stop(); track.release(); track = null
            capture.close()
            withContext(Dispatchers.Main) { host.close() }
            assertNull(capture.read())
            audit.assertUnchanged("capture-after-close")
        } finally {
            runCatching { track?.stop() }; track?.release()
            adapter?.close()
            withContext(Dispatchers.Main) { host.close() }
            audit?.close()
            manager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
            CaptureConsentTestActivity.result = null
            scenario.close()
        }
    }

    @Test fun explicitMicrophoneCreatesNoFilesDuringReadOrClose() = runBlocking {
        val scenario = ActivityScenario.launch(CaptureConsentTestActivity::class.java)
        val origin = System.nanoTime()
        val adapter = MicAudioSourceAdapter(context) { (System.nanoTime() - origin) / 1e9 }
        try {
            delay(200)
            P7AudioStorageAudit(context).use { audit ->
                val session = adapter.open(SourceRequest(SourceKind.MICROPHONE))
                withTimeout(5000) {
                    repeat(8) {
                        val frame = checkNotNull(adapter.read())
                        assertEquals(session.id, frame.sourceSessionId)
                        assertTrue(frame.samples.isNotEmpty() && frame.samples.all { it.isFinite() && it in -1f..1f })
                    }
                }
                audit.assertUnchanged("microphone-active")
                adapter.close()
                assertNull(adapter.read())
                audit.assertUnchanged("microphone-after-close")
            }
        } finally { adapter.close(); scenario.close() }
    }

    @Test fun localDecodeAndPlaybackCreateNoFilesBesidesPreexistingSyntheticInput() = runBlocking {
        val fixture = p7SyntheticWave(context)
        val origin = System.nanoTime()
        val adapter = LocalFilePlaybackAdapter(context) { (System.nanoTime() - origin) / 1e9 }
        try {
            P7AudioStorageAudit(context).use { audit ->
                val session = adapter.open(SourceRequest(SourceKind.LOCAL_FILE, fixture.toURI().toString()))
                withTimeout(6000) {
                    repeat(8) {
                        val frame = checkNotNull(adapter.read())
                        assertEquals(session.id, frame.sourceSessionId)
                        assertTrue(frame.samples.any { abs(it) > 0.005f })
                    }
                }
                audit.assertUnchanged("local-active")
                adapter.close()
                assertNull(adapter.read())
                audit.assertUnchanged("local-after-close")
            }
        } finally { adapter.close(); fixture.delete() }
    }
}
