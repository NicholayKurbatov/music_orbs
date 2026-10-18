package musicorbs.platform.audio

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.audiofx.AudioEffect
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import musicorbs.audio.application.DefaultAudioSession
import musicorbs.audio.domain.*
import musicorbs.bootstrap.MainActivity
import musicorbs.motion.domain.*
import musicorbs.motion.infrastructure.CommonsMathSpectrum
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.*

/** Только synthetic AudioTrack в RAM; MotionSignal не подменяется. */
@RunWith(AndroidJUnit4::class)
class VisualizerTest {
    @get:Rule val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val origin = System.nanoTime()
    private fun now() = (System.nanoTime() - origin) / 1e9

    @Test fun sdkGlobalPlayerReleaseAndBoundedDiscovery() = runBlocking {
        PlayerSessionAnnouncements.clearForTest()
        val discovery = AndroidPlayerSessionDiscovery(context).also { it.start(); it.start() }
        val adapter = VisualizerAdapter(context, ::now)
        var track: AudioTrack? = null
        try {
            assertTrue(discovery.snapshot().isEmpty())
            for (id in 1..40) announce(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, id)
            assertEquals(32, discovery.snapshot().size)
            val remembered = discovery.snapshot().last().sessionToken
            announce(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, 40)
            assertEquals(remembered, discovery.snapshot().last().sessionToken)
            announce(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, -1)
            assertEquals(32, discovery.snapshot().size)
            PlayerSessionAnnouncements.clearForTest()
            track = p8RhythmicTrack(120)
            announce(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, track.audioSessionId)
            val token = discovery.snapshot().single().sessionToken
            val target = VisualizationTarget(VisualizationMode.PLAYER, token)
            val session = adapter.open(SourceRequest(SourceKind.VISUALIZER, visualizationTarget = target))
            val frame = withTimeout(5000) { adapter.read()!! }
            frame.validate()
            assertEquals(session.id, frame.sourceSessionId)
            val waveformCopy = frame.waveform.copyOf()
            delay(400)
            assertArrayEquals(waveformCopy, frame.waveform, 0f)
            val latest = withTimeout(2000) { adapter.read()!! }
            assertTrue("Latest slot must replace queued callbacks", latest.timestampSeconds > frame.timestampSeconds + 0.15)
            val diagnostic = adapter.diagnostics!!
            p7Evidence("visualizer-sdk-player", "samplingRateHz" to diagnostic.samplingRateHz,
                "callbackHz" to diagnostic.callbackRateHz, "size" to diagnostic.captureSize,
                "scaling" to diagnostic.scaling.name, "peakRmsSupported" to diagnostic.peakRmsSupported)
            announce(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION, track.audioSessionId)
            assertNull(PlayerSessionAnnouncements.resolve(token))
            adapter.close(); adapter.close()
            assertNull(withTimeout(1000) { adapter.read() })
            announce(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, track.audioSessionId)
            assertNotEquals(token, discovery.snapshot().single().sessionToken)
            val global = adapter.open(SourceRequest(SourceKind.VISUALIZER, visualizationTarget = VisualizationTarget(VisualizationMode.GLOBAL)))
            assertNotEquals(session.id, global.id)
            repeat(4) { assertEquals(global.id, withTimeout(2000) { adapter.read()!! }.sourceSessionId) }
            val waiting = async { adapter.read() }
            adapter.close()
            withTimeout(2000) { waiting.await() }
            assertNull(adapter.diagnostics)
            assertNull(adapter.read())
        } finally {
            adapter.close(); track?.let { runCatching { it.stop() }; it.release() }
            discovery.close(); discovery.close(); PlayerSessionAnnouncements.clearForTest()
        }
    }

    @Test fun mainAudioTrackThroughVisualizerDspClockBridgeSceneAndRebind() = runBlocking {
        val saved = P7SessionPreserver(context)
        PlayerSessionAnnouncements.clearForTest()
        val manager = context.getSystemService(AudioManager::class.java)
        val volume = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val probe = P7MainUiProbe(scenario)
        var track: AudioTrack? = null
        var baseline: JsonObject? = null
        try {
            probe.ready(); probe.closePanelIfOpen()
            baseline = probe.snapshot("visualizer-baseline")
            probe.evaluate("(window.__p8Samples=[],window.__p8Timer=setInterval(()=>{if(window.__p8Samples.length<4000)window.__p8Samples.push(window.musicOrbs.visualState())},50),true)")
            assertTrue(baseline.getValue("settings").jsonObject.getValue("musicSync").jsonPrimitive.boolean)
            manager.setStreamVolume(AudioManager.STREAM_MUSIC, manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0)
            track = p8RhythmicTrack(90)
            probe.click("Настройки")
            probe.click("Visualizer GLOBAL · общий микс", scroll = true)
            probe.closePanelIfOpen()
            val initial = awaitTempo(probe, 90)
            probe.assertNoCaptureService(context)
            assertTrue(manager.activeRecordingConfigurations.isEmpty())
            val firstId = initial.getValue("sourceSessionId").jsonPrimitive.content
            track.stop(); track.release(); track = null
            delay(4000)
            track = p8RhythmicTrack(150)
            val changed = awaitTempo(probe, 150)
            assertEquals(firstId, changed.getValue("sourceSessionId").jsonPrimitive.content)
            assertEquals(baseline.getValue("shapes"), probe.snapshot().getValue("shapes"))
            val samples = probe.evaluate("(clearInterval(window.__p8Timer),window.__p8Samples)").jsonArray.map { it.jsonObject }
            var maximumResidual = 0.0
            var maximumSlope = 0.0
            var examined = 0
            for ((previous, current) in samples.zipWithNext()) {
                val dt = current.getValue("timestampSeconds").jsonPrimitive.double - previous.getValue("timestampSeconds").jsonPrimitive.double
                if (dt <= 0) continue
                val period = current.getValue("slowCycleSeconds").jsonPrimitive.double
                val previousPeriod = previous.getValue("slowCycleSeconds").jsonPrimitive.double
                val phaseDelta = (current.getValue("phase").jsonPrimitive.double - previous.getValue("phase").jsonPrimitive.double + 1) % 1
                maximumResidual = max(maximumResidual, phaseDelta - dt / min(period, previousPeriod))
                maximumSlope = max(maximumSlope, abs(1 / period - 1 / previousPeriod) / dt)
                examined++
            }
            assertTrue("Real pipeline phase must stay continuous: $maximumResidual", maximumResidual <= 0.015)
            assertTrue("Real pipeline frequency slope must stay bounded: $maximumSlope", maximumSlope <= 0.021)
            assertTrue(examined > 100)
            p7Evidence("visualizer-phase-continuity", "observations" to examined, "maximumResidualCycles" to maximumResidual,
                "maximumFrequencySlope" to maximumSlope)
            // Sync управляется настоящей Compose-панелью, источник не меняется.
            probe.click("Настройки"); probe.click("Синхронизация с музыкой", scroll = true); probe.closePanelIfOpen()
            probe.awaitMotion("Sync off must stay autonomous") { it.getValue("mode").jsonPrimitive.content == "autonomous" }
            probe.click("Настройки"); probe.click("Синхронизация с музыкой", scroll = true); probe.closePanelIfOpen()
            awaitTempo(probe, 150)
            // Настоящее объявление Android-сессии через публичный broadcast.
            broadcast(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, track.audioSessionId)
            delay(700)
            val candidate = PlayerSessionAnnouncements.snapshot().single()
            probe.click("Настройки")
            probe.click("Плеер: ${candidate.packageName} · 1", scroll = true)
            probe.closePanelIfOpen()
            val player = awaitTempo(probe, 150)
            val playerId = player.getValue("sourceSessionId").jsonPrimitive.content
            assertNotEquals(firstId, playerId)
            broadcast(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION, track.audioSessionId)
            track.stop(); track.release(); track = null
            probe.awaitMotion("Closed player invalidates source") { it.getValue("sourceSessionId").jsonPrimitive.content == "none" }
            track = p8RhythmicTrack(150)
            broadcast(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, track.audioSessionId)
            val rebound = awaitTempo(probe, 150)
            assertNotEquals(playerId, rebound.getValue("sourceSessionId").jsonPrimitive.content)
            assertEquals(baseline.getValue("shapes"), probe.snapshot().getValue("shapes"))
            scenario.moveToState(Lifecycle.State.CREATED)
            delay(700)
            scenario.moveToState(Lifecycle.State.RESUMED)
            delay(1000)
            repeat(10) {
                assertEquals("none", probe.motion().getValue("sourceSessionId").jsonPrimitive.content)
                delay(100)
            }
            assertEquals(baseline.getValue("shapes"), probe.snapshot().getValue("shapes"))
            p7Evidence("visualizer-main-lifecycle", "backgroundStopped" to true, "noAutoRestart" to true, "shapesPreserved" to true)
        } finally {
            try {
                track?.let { runCatching { it.stop() }; it.release() }
                manager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                runCatching { probe.evaluate("(clearInterval(window.__p8Timer),delete window.__p8Timer,delete window.__p8Samples,true)") }
                baseline?.let { probe.restoreScene(it) }
            } finally { try { scenario.close() } finally { probe.close(); saved.restore(); PlayerSessionAnnouncements.clearForTest() } }
        }
    }

    private suspend fun awaitTempo(probe: P7MainUiProbe, bpm: Int): JsonObject {
        var last: JsonObject? = null
        val found = withTimeoutOrNull(45000) {
            while (true) {
                val value = probe.motion(); last = value
                if (value.getValue("mode").jsonPrimitive.content == "synced" &&
                    abs((value.getValue("bpm").jsonPrimitive.doubleOrNull ?: 0.0) - bpm) <= 3 &&
                    abs(value.getValue("slowCycleSeconds").jsonPrimitive.double - 60.0 / bpm * 32) <= 0.6) {
                    p7Evidence("visualizer-main-tempo", "expectedBpm" to bpm, "bpm" to value.getValue("bpm").jsonPrimitive.double,
                        "period" to value.getValue("slowCycleSeconds").jsonPrimitive.double,
                        "energy" to value.getValue("energy").jsonPrimitive.double, "sourceId" to value.getValue("sourceSessionId").jsonPrimitive.content)
                    return@withTimeoutOrNull value
                }
                delay(200)
            }
            @Suppress("UNREACHABLE_CODE") null
        }
        return checkNotNull(found) { "Visualizer $bpm BPM did not converge: $last" }
    }

    @Test fun realGlobalWindowTempoDiagnostic() = runBlocking {
        val manager = context.getSystemService(AudioManager::class.java)
        val volume = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val adapter = VisualizerAdapter(context, ::now)
        var track: AudioTrack? = null
        try {
            manager.setStreamVolume(AudioManager.STREAM_MUSIC, manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0)
            track = p8RhythmicTrack(90)
            val session = adapter.open(SourceRequest(SourceKind.VISUALIZER, visualizationTarget = VisualizationTarget(VisualizationMode.GLOBAL)))
            val analyzer = VisualizationAnalyzer(CommonsMathSpectrum()).apply { selectSource(session.id) }
            val start = now()
            var frames = 0
            var maxEnergy = 0.0
            var minRms = 0.0
            var maxRms = -120.0
            var maxBpm = 0.0
            var last: FeatureFrame? = null
            while (now() - start < 24.0) {
                val frame = withTimeout(2000) { adapter.read()!! }
                last = analyzer.analyze(frame); frames++
                maxEnergy = max(maxEnergy, last.energy)
                frame.rmsDbfs?.let { minRms = min(minRms, it); maxRms = max(maxRms, it) }
                if (last.bpm != null) maxBpm = max(maxBpm, last.bpm!!)
            }
            p7Evidence("visualizer-real-global-dsp", "frames" to frames, "onsets" to analyzer.onsetCount,
                "energyMax" to maxEnergy, "rmsMinDbfs" to minRms, "rmsMaxDbfs" to maxRms,
                "bpm" to last!!.bpm, "confidence" to last.confidence, "maxBpm" to maxBpm)
            assertNotNull(last.bpm)
            assertEquals(90.0, last.bpm!!, 3.0)
        } finally {
            adapter.close(); track?.let { runCatching { it.stop() }; it.release() }
            manager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
        }
    }

    @Test fun quietSignalAndCapturePolicyAreMeasuredWithoutSavingAudio() = runBlocking {
        val adapter = VisualizerAdapter(context, ::now)
        var track: AudioTrack? = null
        var audit: P7AudioStorageAudit? = null
        val manager = context.getSystemService(AudioManager::class.java)
        val volume = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        try {
            manager.setStreamVolume(AudioManager.STREAM_MUSIC, manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0)
            audit = P7AudioStorageAudit(context)
            track = p8RhythmicTrack(120, gain = 0.0)
            val opened = now()
            val session = adapter.open(SourceRequest(SourceKind.VISUALIZER, visualizationTarget = VisualizationTarget(VisualizationMode.GLOBAL)))
            val analyzer = VisualizationAnalyzer(CommonsMathSpectrum()).apply { selectSource(session.id) }
            val monitor = VisualizationSignalStatus(opened)
            while (now() - opened < 4.2) {
                val f = withTimeout(2000) { adapter.read()!! }
                val features = analyzer.analyze(f)
                monitor.observe(f, features)
                assertNull(features.bpm)
            }
            assertEquals(VisualizationSignalState.QUIET, monitor.stateAt(now()))
            audit.assertUnchanged("visualizer-quiet")
            track.stop(); track.release(); track = p8RhythmicTrack(120, AudioAttributes.ALLOW_CAPTURE_BY_NONE)
            var maximum = 0.0
            repeat(70) {
                val f = withTimeout(2000) { adapter.read()!! }
                maximum = max(maximum, analyzer.analyze(f).energy)
            }
            // Результат запретительной policy исследуется, успешный обход не предполагается.
            p7Evidence("visualizer-forbidden-policy", "energyMax" to maximum, "quietStatus" to true,
                "trackPlaying" to (track.playState == AudioTrack.PLAYSTATE_PLAYING))
            audit.assertUnchanged("visualizer-forbidden")
        } finally {
            audit?.close()
            adapter.close(); track?.let { runCatching { it.stop() }; it.release() }
            manager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
        }
    }

    private fun intent(action: String, id: Int) = Intent(action).putExtra(AudioEffect.EXTRA_AUDIO_SESSION, id)
        .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
    private fun announce(action: String, id: Int) { PlayerSessionReceiver().onReceive(context, intent(action, id)) }
    private fun broadcast(action: String, id: Int) { context.sendBroadcast(intent(action, id).setPackage(context.packageName)) }
}

internal fun p8RhythmicTrack(bpm: Int, policy: Int = AudioAttributes.ALLOW_CAPTURE_BY_ALL, gain: Double = 1.0): AudioTrack {
    val samples = ShortArray(48000 * 2) { index ->
        val t = index / 48000.0
        val beat = t % (60.0 / bpm)
        val drum = if (beat < 0.16) 0.65 * sin(2 * PI * 1000 * t) * (1 - beat / 0.16) else 0.0
        ((0.12 * sin(2 * PI * 220 * t) + drum) * 32767 * gain).roundToInt().toShort()
    }
    val track = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setAllowedCapturePolicy(policy).build())
        .setAudioFormat(AudioFormat.Builder().setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
        .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(samples.size * 2).build()
    try {
        check(track.write(samples, 0, samples.size) == samples.size)
        check(track.setLoopPoints(0, samples.size, -1) == AudioTrack.SUCCESS)
        track.play(); return track
    } catch (failure: Throwable) { track.release(); throw failure }
}
