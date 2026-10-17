package musicorbs.bootstrap

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonPrimitive
import musicorbs.audio.application.DefaultAudioSession
import musicorbs.audio.domain.*
import musicorbs.experience.application.DefaultExperienceService
import musicorbs.experience.application.SceneCommand
import musicorbs.experience.domain.ProfileLimits
import musicorbs.experience.domain.SceneSettings
import musicorbs.experience.infrastructure.*
import musicorbs.experience.presentation.platform.MusicOrbsScreen
import musicorbs.motion.domain.*
import musicorbs.motion.infrastructure.CommonsMathSpectrum
import musicorbs.platform.LocalAssetsClient
import musicorbs.platform.audio.*

/** Composition root: выбор Android/WebView/store адаптеров и их соединение. */
class MainActivity : ComponentActivity() {
    private val runtimeOrigin = System.nanoTime()
    private fun runtimeSeconds() = (System.nanoTime() - runtimeOrigin) / 1e9
    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val latestFeatures = AtomicReference<FeatureFrame?>(null)
    private var featureArrival = -1.0
    private val analysis = Mutex()
    private val analyzer = BoundedAudioAnalyzer(CommonsMathSpectrum())
    private val clock = ContinuousMotionClock()
    private lateinit var scene: WebView
    private lateinit var bridge: CoroutineBridge
    private lateinit var experience: DefaultExperienceService
    private lateinit var audio: DefaultAudioSession
    private lateinit var captureHost: CaptureProjectionHost
    private var projection: MediaProjection? = null
    private var reader: Job? = null
    private var saver: Job? = null
    private var ready = false
    private var restorationFailed = false
    private var starting = false
    private var destroyed = false
    private var foreground = false
    private var sourceAction: String? = null
    private var focusRequest: AudioFocusRequest? = null
    private var audioGeneration = 0L
    private var settings by mutableStateOf(ProfileLimits.DEFAULT_SETTINGS)
    private var status by mutableStateOf("Подготовка сцены…")
    private var sourceKind by mutableStateOf<SourceKind?>(null)
    private var playing by mutableStateOf(false)
    private var applyingSettings by mutableStateOf(false)

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            choose(SourceRequest(SourceKind.LOCAL_FILE, uri.toString()))
        } else status = "Выбор файла отменён"
    }
    private val recordPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = sourceAction; sourceAction = null
        if (!granted) status = "Доступ к аудио не разрешён. Сцена продолжает двигаться."
        else if (action == "microphone") choose(SourceRequest(SourceKind.MICROPHONE))
        else if (action == "capture") requestProjection()
    }
    private val captureConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK || result.data == null) {
            status = "Захват не разрешён. Можно выбрать локальный файл."
        } else {
            val selection = ++audioGeneration
            work.launch {
                try {
                    while (!foreground) {
                        delay(16)
                        if (destroyed || selection != audioGeneration) return@launch
                    }
                    val acquired = captureHost.acquire(result.resultCode, result.data!!)
                    if (destroyed || !foreground || selection != audioGeneration) {
                        acquired.stop()
                        return@launch
                    }
                    projection = acquired
                    choose(SourceRequest(SourceKind.PLAYBACK_CAPTURE))
                } catch (failure: Exception) {
                    if (selection == audioGeneration) {
                        captureHost.close()
                        showFailure(failure, "Не удалось начать захват")
                    }
                }
            }
        }
    }
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pauseForFocus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureHost = CaptureProjectionHost(this)
        audio = DefaultAudioSession { kind -> when (kind) {
            SourceKind.LOCAL_FILE -> LocalFilePlaybackAdapter(this, ::runtimeSeconds)
            SourceKind.MICROPHONE -> MicAudioSourceAdapter(this, ::runtimeSeconds)
            SourceKind.PLAYBACK_CAPTURE -> PlaybackCaptureAdapter(this, checkNotNull(projection) { "PERMISSION_DENIED" }, ::runtimeSeconds) { captureHost.close() }
        } }
        scene = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true; domStorageEnabled = false
                allowFileAccess = false; allowContentAccess = false
                blockNetworkLoads = true; mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                mediaPlaybackRequiresUserGesture = true; setSupportMultipleWindows(false)
            }
            webViewClient = object : LocalAssetsClient(assets) {
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    work.launch { stopAudio(); recreate() }; return true
                }
            }
            webChromeClient = WebChromeClient()
            addJavascriptInterface(NativeEndpoint(), "MusicOrbsNative")
        }
        bridge = CoroutineBridge(UUID.randomUUID().toString(), work, { json ->
            scene.post { if (!destroyed) scene.evaluateJavascript("window.MusicOrbsReceive?.(${JsonPrimitive(json)})", null) }
        }, ::runtimeSeconds)
        experience = DefaultExperienceService(bridge, AtomicSessionStore(filesDir.toPath().resolve("session.json")))
        setContent {
            MusicOrbsScreen(scene, settings, status, sourceKind, playing,
                onSettings = ::configure, onReset = { applyScene(SceneCommand.Reset) },
                onPick = { picker.launch(arrayOf("audio/*")) },
                onCapture = { requestAudioPermission("capture") },
                onMicrophone = { requestAudioPermission("microphone") },
                onPauseResume = ::togglePlayback, onStop = { work.launch { stopAudio(); status = "Автономное движение" } },
                settingsEnabled = ready && !applyingSettings)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), RECEIVER_NOT_EXPORTED)
        else registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        scene.loadUrl(LocalAssetsClient.INDEX_URL)
        work.launch {
            var previous = runtimeSeconds()
            while (isActive) {
                delay(34)
                val now = runtimeSeconds(); val delta = now - previous; previous = now
                val features = latestFeatures.get().takeIf { foreground && now - featureArrival <= 0.5 }
                val signal = clock.advance(delta, features, settings.musicSync)
                if (foreground) bridge.sendMotion(signal)
            }
        }
    }

    private inner class NativeEndpoint {
        @JavascriptInterface fun postMessage(raw: String) {
            scene.post {
                if (destroyed) return@post
                if (raw == "scene-dirty") { restorationFailed = false; scheduleSave() }
                else {
                    bridge.receive(raw)
                    if (raw == "graphics-ready" && !starting) {
                        starting = true
                        work.launch {
                            try {
                                val restored = experience.restore()
                                if (restored != null) settings = restored.settings
                                // Lifecycle-команда, пришедшая во время initial ack, должна увидеть ready.
                                ready = true
                                experience.dispatch(if (foreground) SceneCommand.Resume else SceneCommand.Pause)
                                status = "Автономное движение"
                                if (restored == null) scheduleSave()
                            } catch (failure: Exception) {
                                restorationFailed = true
                                ready = true; showFailure(failure, "Сохранение не восстановлено. Открыта исходная форма.")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun configure(value: SceneSettings) = applyScene(SceneCommand.Configure(value))
    private fun applyScene(command: SceneCommand) {
        if (!ready || applyingSettings) return
        applyingSettings = true
        work.launch {
            try {
                experience.dispatch(command)
                if (command is SceneCommand.Configure) settings = command.settings
                restorationFailed = false
                scheduleSave()
            } catch (failure: Exception) { showFailure(failure, "Изменение не применено") }
            finally { applyingSettings = false }
        }
    }

    private fun scheduleSave() {
        if (!ready || destroyed) return
        saver?.cancel()
        saver = work.launch {
            delay(250)
            try { experience.save() } catch (failure: CancellationException) { throw failure }
            catch (failure: Exception) { showFailure(failure, "Не удалось сохранить форму") }
        }
    }

    private fun choose(request: SourceRequest) {
        val selection = ++audioGeneration
        work.launch {
            try {
                while (!foreground) { delay(16); if (destroyed || selection != audioGeneration) return@launch }
                reader?.cancel(); latestFeatures.set(null); clock.resetSource("none"); bridge.setSource("none")
                sourceKind = null; playing = false
                if (request.kind == SourceKind.LOCAL_FILE && !requestFocus()) error("SOURCE_UNAVAILABLE")
                val session = audio.select(request)
                if (selection != audioGeneration || !foreground) return@launch
                val selected = analysis.withLock {
                    if (selection != audioGeneration || !foreground) false
                    else { analyzer.selectSource(session.id); true }
                }
                if (!selected || selection != audioGeneration || !foreground) return@launch
                clock.resetSource(session.id); bridge.setSource(session.id)
                sourceKind = session.kind; playing = true
                status = when (session.kind) {
                    SourceKind.LOCAL_FILE -> "Локальный файл: воспроизведение"
                    SourceKind.PLAYBACK_CAPTURE -> "Захват включён; доступность зависит от источника"
                    SourceKind.MICROPHONE -> "Микрофон включён"
                }
                startReader(session.id)
            } catch (failure: CancellationException) { throw failure }
            catch (failure: Exception) { if (selection == audioGeneration) { stopAudio(); showFailure(failure, "Источник недоступен") } }
        }
    }

    private fun startReader(sourceId: String) {
        val readerGeneration = audioGeneration
        reader?.cancel()
        reader = work.launch {
            try {
                while (audio.currentSession?.id == sourceId && !audio.isPaused) {
                    val frame = audio.read() ?: break
                    val features = withContext(Dispatchers.Default) { analysis.withLock { analyzer.analyze(frame) } }
                    if (audio.currentSession?.id == sourceId) { latestFeatures.set(features); featureArrival = runtimeSeconds() }
                }
                if (audio.currentSession?.id == sourceId) {
                    latestFeatures.set(null); playing = false
                    if (!audio.isPaused) { stopAudio(); status = "Воспроизведение завершено. Автономное движение." }
                }
            } catch (failure: CancellationException) { throw failure }
            catch (failure: Exception) { if (readerGeneration == audioGeneration) { stopAudio(); showFailure(failure, "Аудио остановлено") } }
        }
    }

    private fun togglePlayback() {
        work.launch {
            try {
                if (audio.currentSession?.kind != SourceKind.LOCAL_FILE) return@launch
                if (audio.isPaused) {
                    if (!requestFocus()) error("SOURCE_UNAVAILABLE")
                    audio.resume(); playing = true; status = "Локальный файл: воспроизведение"
                    startReader(audio.currentSession!!.id)
                } else { audio.pause(); latestFeatures.set(null); playing = false; status = "Пауза" }
            } catch (failure: Exception) { stopAudio(); showFailure(failure, "Не удалось изменить воспроизведение") }
        }
    }

    private fun requestAudioPermission(action: String) {
        work.launch {
            stopAudio()
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                if (action == "microphone") choose(SourceRequest(SourceKind.MICROPHONE)) else requestProjection()
            } else { sourceAction = action; recordPermission.launch(Manifest.permission.RECORD_AUDIO) }
        }
    }
    private fun requestProjection() {
        captureConsent.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
    }

    private suspend fun stopAudio() {
        ++audioGeneration
        val activeReader = reader; reader = null
        if (activeReader != currentCoroutineContext()[Job]) activeReader?.cancel()
        withContext(NonCancellable) {
            latestFeatures.set(null); clock.resetSource("none")
            if (!destroyed) bridge.setSource("none")
            sourceKind = null; playing = false
            try { audio.stop() } finally {
                projection = null; captureHost.close()
                focusRequest?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }; focusRequest = null
            }
        }
    }
    private fun requestFocus(): Boolean {
        val manager = getSystemService(AudioManager::class.java)
        val request = focusRequest ?: run {
            lateinit var ownedRequest: AudioFocusRequest
            ownedRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setOnAudioFocusChangeListener { change ->
                    if (focusRequest === ownedRequest &&
                        (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)) pauseForFocus()
                }.build()
            ownedRequest.also { focusRequest = it }
        }
        return manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
    private fun pauseForFocus() {
        if (!::audio.isInitialized) return
        work.launch { try {
            audio.onFocusLost(); latestFeatures.set(null); playing = false
            if (audio.currentSession == null) { sourceKind = null; clock.resetSource("none"); bridge.setSource("none") }
            status = if (audio.currentSession == null) "Аудио остановлено после изменения аудиовыхода" else "Пауза после изменения аудиовыхода"
        } catch (failure: Exception) { showFailure(failure, "Аудио остановлено") } }
    }
    private fun showFailure(failure: Exception, message: String) {
        if (failure is CancellationException) return
        val reasons = generateSequence<Throwable>(failure) { it.cause }.mapNotNull { it.message }.joinToString(" ")
        status = when {
            "bridge deadline" in reasons -> "Сцена не ответила. Изменение не подтверждено."
            "revoked" in reasons -> "Разрешение на захват отозвано. Сцена продолжает двигаться."
            failure is SecurityException || "permission denied" in reasons -> "Доступ к аудио не разрешён. Сцена продолжает двигаться."
            else -> message
        }
        Log.w("MusicOrbs", "${failure.javaClass.simpleName}: ${failure.message?.substringBefore(':')}")
    }

    override fun onResume() {
        super.onResume(); foreground = true; clock.resume()
        if (::scene.isInitialized) scene.onResume()
        if (ready) work.launch { runCatching { experience.dispatch(SceneCommand.Resume) } }
    }
    override fun onPause() {
        foreground = false; clock.pause(); saver?.cancel()
        work.launch {
            stopAudio()
            if (ready) {
                if (!restorationFailed) runCatching { experience.save() }
                if (!foreground) runCatching { experience.dispatch(SceneCommand.Pause) }
            }
            if (!destroyed && !foreground) scene.onPause()
        }
        super.onPause()
    }
    override fun onDestroy() {
        destroyed = true; ready = false
        runCatching { unregisterReceiver(noisy) }
        reader?.cancel(); saver?.cancel(); bridge.close()
        work.launch(NonCancellable + Dispatchers.IO) { runCatching { audio.stop() }; captureHost.close() }
        work.cancel()
        scene.removeJavascriptInterface("MusicOrbsNative"); scene.stopLoading(); scene.removeAllViews(); scene.destroy()
        super.onDestroy()
    }
}
