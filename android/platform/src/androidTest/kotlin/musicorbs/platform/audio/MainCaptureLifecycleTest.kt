package musicorbs.platform.audio

import android.Manifest
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import musicorbs.bootstrap.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** FT-18/21: Main сама получает projection; fixture не подменяет её audio/bridge. */
@RunWith(AndroidJUnit4::class)
class MainCaptureLifecycleTest {
    @get:Rule val recordPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    @Test fun explicitMainMicrophoneStopsOnHomeAndDoesNotRestartOnReturn() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val savedSession = P7SessionPreserver(context)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val probe = P7MainUiProbe(scenario)
        val manager = context.getSystemService(AudioManager::class.java)
        var baseline: JsonObject? = null
        try {
            probe.ready()
            probe.closePanelIfOpen()
            val initial = probe.snapshot("microphone-baseline")
            baseline = initial
            assertEquals("none", probe.motion().getValue("sourceSessionId").jsonPrimitive.content)
            probe.click("Настройки")
            probe.click("Выбрать микрофон", scroll = true)
            probe.closePanelIfOpen()
            val active = probe.awaitMotion("Explicit Main microphone must publish an active source UUID") {
                it.getValue("sourceSessionId").jsonPrimitive.content != "none"
            }
            val sourceId = active.getValue("sourceSessionId").jsonPrimitive.content
            assertTrue("Explicit microphone indicator missing", probe.device.wait(
                androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.desc("Выбран микрофон")), 5000) == true)
            withTimeout(5000) {
                while (manager.activeRecordingConfigurations.none { it.clientAudioSource == MediaRecorder.AudioSource.MIC }) delay(50)
            }
            val activeRecordings = manager.activeRecordingConfigurations
            val micRecording = activeRecordings.firstOrNull { it.clientAudioSource == MediaRecorder.AudioSource.MIC }
            p7Evidence("main-microphone-active", "sourceId" to sourceId,
                "motionTimestampSeconds" to active.getValue("timestampSeconds").jsonPrimitive.double,
                "recorders" to activeRecordings.size, "recordingSource" to micRecording?.clientAudioSource,
                "recordingSessionId" to micRecording?.clientAudioSessionId,
                "indicator" to probe.device.hasObject(androidx.test.uiautomator.By.desc("Выбран микрофон")),
                "captureService" to probe.captureServicePresent(context))
            val activeScene = probe.snapshot("microphone-active-invariants")
            assertEquals(initial.getValue("shapes"), activeScene.getValue("shapes"))
            assertEquals(initial.getValue("settings"), activeScene.getValue("settings"))

            val homeStarted = android.os.SystemClock.elapsedRealtime()
            val homeAccepted = probe.device.pressHome()
            p7Evidence("main-microphone-home-injected", "homeAccepted" to homeAccepted,
                "scenarioState" to scenario.state.name, "topPackage" to probe.device.currentPackageName,
                "recorders" to manager.activeRecordingConfigurations.size)
            assertTrue("HOME key injection rejected", homeAccepted)
            try {
                withTimeout(5000) { while (manager.activeRecordingConfigurations.isNotEmpty()) delay(100) }
            } finally {
                val recordings = manager.activeRecordingConfigurations
                p7Evidence("main-microphone-home-observed", "elapsedMs" to android.os.SystemClock.elapsedRealtime() - homeStarted,
                    "scenarioState" to scenario.state.name, "topPackage" to probe.device.currentPackageName,
                    "recorders" to recordings.size,
                    "recordingSources" to recordings.take(8).joinToString(",") { it.clientAudioSource.toString() },
                    "recordingSessions" to recordings.take(8).joinToString(",") { it.clientAudioSessionId.toString() },
                    "captureService" to probe.captureServicePresent(context))
            }
            p7Evidence("main-microphone-background", "previousSourceId" to sourceId,
                "recorders" to manager.activeRecordingConfigurations.size, "captureService" to probe.captureServicePresent(context))
            probe.assertNoCaptureService(context)
            probe.device.executeShellCommand("am start -W -f 0x00020000 -n musicorbs.app/musicorbs.bootstrap.MainActivity")
            probe.ready()
            probe.closePanelIfOpen()
            val returnStarted = android.os.SystemClock.elapsedRealtime()
            repeat(20) {
                val returned = probe.motion()
                assertEquals("none", returned.getValue("sourceSessionId").jsonPrimitive.content)
                assertEquals("autonomous", returned.getValue("mode").jsonPrimitive.content)
                assertTrue(returned.getValue("sourceSessionId").jsonPrimitive.content != sourceId)
                assertTrue("Microphone recorder restarted after return", manager.activeRecordingConfigurations.isEmpty())
                assertTrue("Microphone indicator restarted after return", !probe.device.hasObject(androidx.test.uiautomator.By.desc("Выбран микрофон")))
                delay(150)
            }
            val returned = probe.motion()
            p7Evidence("main-microphone-returned", "previousSourceId" to sourceId,
                "sourceId" to returned.getValue("sourceSessionId").jsonPrimitive.content,
                "mode" to returned.getValue("mode").jsonPrimitive.content, "observations" to 20,
                "observedMs" to android.os.SystemClock.elapsedRealtime() - returnStarted,
                "recorders" to manager.activeRecordingConfigurations.size,
                "indicator" to probe.device.hasObject(androidx.test.uiautomator.By.desc("Выбран микрофон")),
                "captureService" to probe.captureServicePresent(context))
            probe.assertNoCaptureService(context)
            val returnedScene = probe.snapshot("microphone-returned-invariants")
            assertEquals(initial.getValue("shapes"), returnedScene.getValue("shapes"))
            assertEquals(initial.getValue("settings"), returnedScene.getValue("settings"))
        } finally {
            try { baseline?.let { probe.restoreScene(it) } }
            finally { try { scenario.close() } finally { try { probe.close() } finally { savedSession.restore() } } }
        }
    }

    @Test fun realMainCaptureStopsOnHomeAndDoesNotRestartOnReturn() = runBlocking {
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
            val initial = probe.snapshot("capture-baseline")
            baseline = initial
            assertTrue("Enable Music Sync in native UI before this positive capture test", initial.getValue("settings").jsonObject.getValue("musicSync").jsonPrimitive.boolean)
            assertEquals("none", probe.motion().getValue("sourceSessionId").jsonPrimitive.content)
            manager.setStreamVolume(AudioManager.STREAM_MUSIC, maxOf(1, manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 2), 0)
            track = p7SyntheticTrack(AudioAttributes.ALLOW_CAPTURE_BY_ALL)
            probe.click("Настройки")
            probe.click("Захват внешнего воспроизведения", scroll = true)
            probe.acceptCaptureConsent()
            val active = probe.awaitMotion("Main capture must receive nonzero synthetic PCM") {
                it.getValue("sourceSessionId").jsonPrimitive.content != "none" && it.getValue("energy").jsonPrimitive.double > 0.005
            }
            val sourceId = active.getValue("sourceSessionId").jsonPrimitive.content
            p7Evidence("main-capture-active", "sourceId" to sourceId,
                "timestampSeconds" to active.getValue("timestampSeconds").jsonPrimitive.double,
                "mode" to active.getValue("mode").jsonPrimitive.content,
                "energy" to active.getValue("energy").jsonPrimitive.double,
                "intensity" to active.getValue("intensity").jsonPrimitive.double,
                "phase" to active.getValue("phase").jsonPrimitive.double,
                "bpm" to active.getValue("bpm").jsonPrimitive.doubleOrNull,
                "slowCycleSeconds" to active.getValue("slowCycleSeconds").jsonPrimitive.double,
                "confidence" to active.getValue("confidence").jsonPrimitive.double,
                "recorders" to manager.activeRecordingConfigurations.size,
                "captureService" to probe.captureServicePresent(context))
            assertEquals(initial.getValue("shapes"), probe.snapshot("capture-active-invariants").getValue("shapes"))
            assertTrue("Fixture must stay audible to the capture pipeline", track.playState == AudioTrack.PLAYSTATE_PLAYING)

            probe.device.pressHome()
            delay(1200)
            p7Evidence("main-capture-background", "previousSourceId" to sourceId,
                "recorders" to manager.activeRecordingConfigurations.size, "captureService" to probe.captureServicePresent(context))
            probe.assertNoCaptureService(context)
            assertTrue("AudioRecord must be closed in background", manager.activeRecordingConfigurations.isEmpty())
            probe.device.executeShellCommand("am start -W -f 0x00020000 -n musicorbs.app/musicorbs.bootstrap.MainActivity")
            probe.ready()
            probe.closePanelIfOpen()
            val returnStarted = android.os.SystemClock.elapsedRealtime()
            repeat(20) {
                val returned = probe.motion()
                assertEquals("none", returned.getValue("sourceSessionId").jsonPrimitive.content)
                assertEquals("autonomous", returned.getValue("mode").jsonPrimitive.content)
                assertTrue(returned.getValue("sourceSessionId").jsonPrimitive.content != sourceId)
                delay(150)
            }
            val returned = probe.motion()
            p7Evidence("main-capture-returned", "previousSourceId" to sourceId,
                "sourceId" to returned.getValue("sourceSessionId").jsonPrimitive.content,
                "mode" to returned.getValue("mode").jsonPrimitive.content, "observations" to 20,
                "observedMs" to android.os.SystemClock.elapsedRealtime() - returnStarted,
                "recorders" to manager.activeRecordingConfigurations.size,
                "microphoneIndicator" to probe.device.hasObject(androidx.test.uiautomator.By.desc("Выбран микрофон")),
                "captureService" to probe.captureServicePresent(context))
            probe.assertNoCaptureService(context)
            assertTrue(manager.activeRecordingConfigurations.isEmpty())
            assertTrue("No hidden microphone fallback", !probe.device.hasObject(androidx.test.uiautomator.By.desc("Выбран микрофон")))
            val returnedScene = probe.snapshot("capture-returned-invariants")
            assertEquals(initial.getValue("shapes"), returnedScene.getValue("shapes"))
            assertEquals(initial.getValue("settings"), returnedScene.getValue("settings"))
        } finally {
            try {
                runCatching { track?.stop() }; runCatching { track?.release() }
                runCatching { manager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0) }
                baseline?.let { probe.restoreScene(it) }
            } finally {
                try { scenario.close() } finally { try { probe.close() } finally { savedSession.restore() } }
            }
        }
    }
}
