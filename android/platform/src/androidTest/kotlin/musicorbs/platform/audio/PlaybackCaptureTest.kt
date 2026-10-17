package musicorbs.platform.audio

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.content.Context
import android.content.ContextWrapper
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import musicorbs.audio.domain.SourceKind
import musicorbs.audio.domain.SourceRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Совместимость подтверждается только для synthetic AudioTrack этого процесса. */
@RunWith(AndroidJUnit4::class)
class PlaybackCaptureTest {
    @get:Rule val recordPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    @Test fun closingPendingBindingRejectsLateCallbackWithoutRetiringNextAcquire() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = DelayedBindingContext(instrumentation.targetContext)
        val result = CompletableDeferred<Pair<Int, Intent?>>()
        CaptureConsentTestActivity.result = result
        val scenario = ActivityScenario.launch(CaptureConsentTestActivity::class.java)
        val host = CaptureProjectionHost(context)
        try {
            scenario.onActivity { it.requestConsent() }
            val device = UiDevice.getInstance(instrumentation)
            checkNotNull(device.wait(Until.findObject(By.res("android:id/button1")), 10000)).click()
            val consent = withTimeout(15000) { result.await() }
            assertEquals(Activity.RESULT_OK, consent.first)
            val data = checkNotNull(consent.second)
            val first = async(Dispatchers.Main) { host.acquire(consent.first, data) }
            val oldBinding = withTimeout(10000) { context.callbacks.receive() }
            withContext(Dispatchers.Main) { host.close() }
            assertTrue(runCatching { first.await() }.exceptionOrNull() is CancellationException)
            delay(150)
            val second = async(Dispatchers.Main) { host.acquire(consent.first, data) }
            val newBinding = withTimeout(10000) { context.callbacks.receive() }
            withContext(Dispatchers.Main) { oldBinding.deliver() }
            delay(100)
            assertTrue("Old service callback must not resolve the new acquire", !second.isCompleted)
            withContext(Dispatchers.Main) { newBinding.deliver() }
            val projection = withTimeout(10000) { second.await() }
            projection.stop()
        } finally {
            withContext(Dispatchers.Main) { host.close() }
            CaptureConsentTestActivity.result = null
            scenario.close()
        }
    }

    @Test fun canceledSystemConsentLeavesNoProjectionServiceOrAudioRecord() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val result = CompletableDeferred<Pair<Int, Intent?>>()
        CaptureConsentTestActivity.result = result
        val scenario = ActivityScenario.launch(CaptureConsentTestActivity::class.java)
        val host = CaptureProjectionHost(context)
        try {
            scenario.onActivity { it.requestConsent() }
            val device = UiDevice.getInstance(instrumentation)
            val cancel = device.wait(Until.findObject(By.res("android:id/button2")), 10000)
                ?: device.wait(Until.findObject(By.text(Pattern.compile("(?i)(cancel|отмена)"))), 5000)
                ?: error("System capture cancel button not found")
            cancel.click()
            val consent = withTimeout(15000) { result.await() }
            assertEquals(Activity.RESULT_CANCELED, consent.first)
            var denied = false
            try { host.acquire(consent.first, consent.second ?: Intent()) }
            catch (expected: IllegalArgumentException) { denied = expected.message?.contains("SOURCE_UNAVAILABLE") == true }
            assertTrue("Denied token must not open a projection", denied)
            @Suppress("DEPRECATION")
            val services = context.getSystemService(ActivityManager::class.java).getRunningServices(100)
            assertTrue(services.none { it.service.className == CaptureProjectionService::class.java.name })
            assertTrue(context.getSystemService(AudioManager::class.java).activeRecordingConfigurations.isEmpty())
        } finally {
            withContext(Dispatchers.Main) { host.close() }
            CaptureConsentTestActivity.result = null
            scenario.close()
        }
    }

    @Test fun explicitConsentCapturesSyntheticPcmAndRevocationStopsIt() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val result = CompletableDeferred<Pair<Int, Intent?>>()
        CaptureConsentTestActivity.result = result
        val scenario = ActivityScenario.launch(CaptureConsentTestActivity::class.java)
        val host = CaptureProjectionHost(context)
        var adapter: PlaybackCaptureAdapter? = null
        var track: AudioTrack? = null
        val manager = context.getSystemService(AudioManager::class.java)
        val volume = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        try {
            scenario.onActivity { it.requestConsent() }
            val device = UiDevice.getInstance(instrumentation)
            val allow = device.wait(Until.findObject(By.res("android:id/button1")), 10000)
                ?: device.wait(Until.findObject(By.text(Pattern.compile("(?i)(start now|start recording|share screen|начать|начать запись)"))), 5000)
                ?: error("System capture consent button not found")
            allow.click()
            val consent = withTimeout(15000) { result.await() }
            assertEquals(Activity.RESULT_OK, consent.first)
            val projection = host.acquire(consent.first, checkNotNull(consent.second))
            val origin = System.nanoTime()
            val runtime = { (System.nanoTime() - origin) / 1e9 }
            val capture = PlaybackCaptureAdapter(context, projection, runtime) { host.close() }
            adapter = capture
            val session = capture.open(SourceRequest(SourceKind.PLAYBACK_CAPTURE))
            manager.setStreamVolume(AudioManager.STREAM_MUSIC, manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 2, 0)
            val sine = ShortArray(48000) { (sin(2 * PI * 440 * it / 48000) * 12000).toInt().toShort() }
            track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_ALL).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(sine.size * 2).build()
            assertEquals(sine.size, track.write(sine, 0, sine.size))
            assertEquals(AudioTrack.SUCCESS, track.setLoopPoints(0, sine.size, -1))
            track.play()
            var nonzero = false
            withTimeout(10000) {
                while (!nonzero) {
                    val frame = checkNotNull(capture.read())
                    assertEquals(session.id, frame.sourceSessionId)
                    assertTrue(frame.samples.all { it.isFinite() && it in -1f..1f })
                    nonzero = frame.samples.any { abs(it) > 0.005f }
                }
            }
            assertTrue(nonzero)
            projection.stop()
            delay(150)
            var revoked = false
            try { withTimeout(3000) { capture.read() } }
            catch (expected: IllegalStateException) { revoked = expected.message?.contains("SOURCE_UNAVAILABLE") == true }
            assertTrue("revoked projection must stop AudioRecord", revoked)
        } finally {
            runCatching { track?.stop() }; track?.release()
            adapter?.close()
            withContext(Dispatchers.Main) { host.close() }
            manager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
            CaptureConsentTestActivity.result = null
            scenario.close()
        }
    }

    /** Real Android service/binder; only callback delivery is held to expose the lifecycle race. */
    private class DelayedBindingContext(base: Context) : ContextWrapper(base) {
        data class Callback(val connection: ServiceConnection, val name: ComponentName, val binder: IBinder) {
            fun deliver() = connection.onServiceConnected(name, binder)
        }
        val callbacks = Channel<Callback>(Channel.UNLIMITED)
        private val bindings = java.util.IdentityHashMap<ServiceConnection, ServiceConnection>()
        override fun getApplicationContext(): Context = this
        override fun bindService(intent: Intent, connection: ServiceConnection, flags: Int): Boolean {
            val proxy = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    callbacks.trySend(Callback(connection, name, binder))
                }
                override fun onServiceDisconnected(name: ComponentName) = connection.onServiceDisconnected(name)
                override fun onBindingDied(name: ComponentName) = connection.onBindingDied(name)
                override fun onNullBinding(name: ComponentName) = connection.onNullBinding(name)
            }
            bindings[connection] = proxy
            return super.bindService(intent, proxy, flags)
        }
        override fun unbindService(connection: ServiceConnection) {
            super.unbindService(checkNotNull(bindings.remove(connection)))
        }
    }
}
