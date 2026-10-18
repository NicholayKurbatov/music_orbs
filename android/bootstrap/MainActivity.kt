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
import android.media.projection.MediaProjectionConfig
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
import kotlin.math.roundToInt
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
    @Volatile private var featureArrival = -1.0
    private val analysis = Mutex()
    private val analyzer = BoundedAudioAnalyzer(CommonsMathSpectrum())
    private val visualizationAnalyzer = VisualizationAnalyzer(CommonsMathSpectrum())
    private var visualizer: VisualizerAdapter? = null
    private lateinit var discovery: AndroidPlayerSessionDiscovery
    private var playerCandidates by mutableStateOf<List<PlayerSessionCandidate>>(emptyList())
    private var visualizationTarget by mutableStateOf<VisualizationTarget?>(null)
    private var externalMethod by mutableStateOf("Visualizer GLOBAL")
    private var pendingVisualizationTarget: VisualizationTarget? = null
    private var monitoredPlayer: String? = null
    @Volatile private var visualStatus: VisualizationSignalStatus? = null
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
    private var captureMonitor: PlaybackCaptureMonitor? = null
    private var captureStatusAt = -1.0
    private var discoveryStatusAt = -1.0
    @Volatile private var audioGeneration = 0L
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
        else if (action == "visualizer") beginVisualization()
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
        discovery = AndroidPlayerSessionDiscovery(this).also { it.start() }
        audio = DefaultAudioSession { kind -> when (kind) {
            SourceKind.LOCAL_FILE -> LocalFilePlaybackAdapter(this, ::runtimeSeconds)
            SourceKind.MICROPHONE -> MicAudioSourceAdapter(this, ::runtimeSeconds)
            SourceKind.PLAYBACK_CAPTURE -> PlaybackCaptureAdapter(this, checkNotNull(projection) { "PERMISSION_DENIED" }, ::runtimeSeconds) { captureHost.close() }
            SourceKind.VISUALIZER -> VisualizerAdapter(this, ::runtimeSeconds).also { visualizer = it }
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
                onCapture = { externalMethod = "PCM capture"; requestAudioPermission("capture") },
                onMicrophone = { requestAudioPermission("microphone") },
                externalMethod = externalMethod, playerCandidates = playerCandidates,
                onVisualization = ::requestVisualization,
                onWaitForPlayer = { work.launch { stopAudio(); externalMethod = "Visualizer PLAYER"; visualizationTarget = null; status = "Ожидание сессии плеера. Запустите или перезапустите воспроизведение в плеере; затем выберите объявленную сессию. Можно явно выбрать общий микс." } },
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
                if (foreground && sourceKind == SourceKind.PLAYBACK_CAPTURE && now - captureStatusAt >= 0.5) {
                    captureStatusAt = now
                    status = when (captureMonitor?.stateAt(now)) {
                        CaptureSignalState.NO_SIGNAL -> "Звук не поступает. Включите воспроизведение и проверьте громкость. Плеер может запрещать захват; можно выбрать локальный файл или микрофон."
                        CaptureSignalState.RECEIVING -> when {
                            !settings.musicSync -> "Звук поступает. Включите Music Sync для движения под музыку."
                            signal.energy <= MotionPolicy.SILENCE_RMS -> "Звук поступает, но слишком тихий для анализа ритма. Увеличьте громкость."
                            signal.mode == MotionMode.SYNCED -> "Синхронизация: ${signal.bpm!!.roundToInt()} BPM. Вращение плавно следует темпу."
                            else -> "Звук поступает. Определяем темп; пока движение автономное."
                        }
                        else -> "Ожидание звука внешнего воспроизведения…"
                    }
                }
                if (foreground && now - discoveryStatusAt >= 0.5) {
                    discoveryStatusAt = now
                    playerCandidates = discovery.snapshot()
                    updatePlayerBinding()
                    if (sourceKind == SourceKind.VISUALIZER) {
                        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            stopAudio(); status = "Разрешение Visualizer отозвано. Сцена продолжает двигаться."
                        } else updateVisualizationStatus(now, signal)
                    }
                }
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
                sourceKind = null; playing = false; captureMonitor = null
                visualStatus = null
                if (request.kind != SourceKind.LOCAL_FILE) {
                    focusRequest?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }; focusRequest = null
                }
                if (request.kind == SourceKind.LOCAL_FILE && !requestFocus()) error("SOURCE_UNAVAILABLE")
                val session = audio.select(request)
                if (selection != audioGeneration || !foreground) return@launch
                val selected = analysis.withLock {
                    if (selection != audioGeneration || !foreground) false
                    else { if (session.kind == SourceKind.VISUALIZER) visualizationAnalyzer.selectSource(session.id) else analyzer.selectSource(session.id); true }
                }
                if (!selected || selection != audioGeneration || !foreground) return@launch
                clock.resetSource(session.id); bridge.setSource(session.id)
                sourceKind = session.kind; playing = true
                status = when (session.kind) {
                    SourceKind.LOCAL_FILE -> "Локальный файл: воспроизведение"
                    SourceKind.PLAYBACK_CAPTURE -> "Ожидание звука внешнего воспроизведения…"
                    SourceKind.MICROPHONE -> "Микрофон включён"
                    SourceKind.VISUALIZER -> "${visualizationLabel()}: ресурс открыт, ожидание данных…"
                }
                startReader(session.id)
            } catch (failure: CancellationException) { throw failure }
            catch (failure: Exception) { if (selection == audioGeneration) { stopAudio(); showFailure(failure,
                if (request.kind == SourceKind.VISUALIZER) "Visualizer не поддерживается или не открылся. Выберите другой способ анализа явно." else "Источник недоступен") } }
        }
    }

    private fun startReader(sourceId: String) {
        val readerGeneration = audioGeneration
        reader?.cancel()
        val sourceCaptureMonitor = if (audio.currentSession?.kind == SourceKind.PLAYBACK_CAPTURE)
            PlaybackCaptureMonitor(sourceId, runtimeSeconds()) else null
        captureMonitor = sourceCaptureMonitor
        val observation = visualizer.takeIf { audio.currentSession?.kind == SourceKind.VISUALIZER }
        val sourceVisualStatus = if (observation != null) VisualizationSignalStatus(runtimeSeconds()) else null
        visualStatus = sourceVisualStatus
        // Чтение PCM и DSP не ждут UI/WebView между блоками: иначе AudioRecord
        // переполняет короткий buffer, теряет участки ритма и BPM не сходится.
        reader = work.launch(Dispatchers.Default) {
            try {
                while (audio.currentSession?.id == sourceId && !audio.isPaused) {
                    val features = if (observation != null) {
                        val frame = observation.read() ?: break
                        analysis.withLock { visualizationAnalyzer.analyze(frame) }.also { sourceVisualStatus?.observe(frame, it) }
                    } else {
                        val frame = audio.read() ?: break
                        analysis.withLock { analyzer.analyze(frame) }.also { sourceCaptureMonitor?.observe(frame) }
                    }
                    if (audio.currentSession?.id == sourceId && readerGeneration == audioGeneration) {
                        latestFeatures.set(features); featureArrival = if (observation != null) features.timestampSeconds else runtimeSeconds()
                    }
                }
                withContext(Dispatchers.Main.immediate) {
                    if (audio.currentSession?.id == sourceId && readerGeneration == audioGeneration) {
                        latestFeatures.set(null); playing = false
                        if (!audio.isPaused) { stopAudio(); status = "Воспроизведение завершено. Автономное движение." }
                    }
                }
            } catch (failure: CancellationException) { throw failure }
            catch (failure: Exception) {
                withContext(Dispatchers.Main.immediate) {
                    if (readerGeneration == audioGeneration) {
                        val waiting = monitoredPlayer != null && foreground && discovery.snapshot().none { it.sessionToken == visualizationTarget?.sessionToken }
                        stopAudio(keepPlayerMonitoring = waiting)
                        if (waiting) status = "Ожидание новой сессии выбранного плеера. Можно выбрать общий микс."
                        else showFailure(failure, if (observation != null) "Visualizer недоступен или не открылся. Выберите способ анализа явно." else "Аудио остановлено")
                    }
                }
            }
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
                when (action) { "microphone" -> choose(SourceRequest(SourceKind.MICROPHONE)); "visualizer" -> beginVisualization(); else -> requestProjection() }
            } else { sourceAction = action; recordPermission.launch(Manifest.permission.RECORD_AUDIO) }
        }
    }
    private fun requestProjection() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        // Нужен внешний звук, а не выбор окна самой сцены. Видеопоток не создаётся.
        val intent = if (Build.VERSION.SDK_INT >= 34)
            manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        else manager.createScreenCaptureIntent()
        captureConsent.launch(intent)
    }

    private fun requestVisualization(target: VisualizationTarget) {
        externalMethod = "Visualizer ${target.mode}"
        pendingVisualizationTarget = target
        visualizationTarget = target
        requestAudioPermission("visualizer")
    }

    private fun beginVisualization() {
        val target = pendingVisualizationTarget ?: return
        pendingVisualizationTarget = null
        visualizationTarget = target
        monitoredPlayer = if (target.mode == VisualizationMode.PLAYER)
            discovery.snapshot().firstOrNull { it.sessionToken == target.sessionToken }?.packageName else null
        if (target.mode == VisualizationMode.PLAYER && monitoredPlayer == null) {
            status = "Сессия плеера больше недоступна. Дождитесь нового объявления или выберите общий микс."
            return
        }
        choose(SourceRequest(SourceKind.VISUALIZER, visualizationTarget = target))
    }

    private suspend fun updatePlayerBinding() {
        val pkg = monitoredPlayer ?: return
        val token = visualizationTarget?.sessionToken
        if (playerCandidates.any { it.sessionToken == token }) return
        if (sourceKind == SourceKind.VISUALIZER) stopAudio(keepPlayerMonitoring = true)
        val replacement = playerCandidates.lastOrNull { it.packageName == pkg }
        if (replacement != null) {
            val target = VisualizationTarget(VisualizationMode.PLAYER, replacement.sessionToken)
            visualizationTarget = target
            choose(SourceRequest(SourceKind.VISUALIZER, visualizationTarget = target))
        } else status = "Ожидание новой сессии $pkg. Запустите воспроизведение в плеере или выберите общий микс."
    }

    private fun visualizationLabel(): String = if (visualizationTarget?.mode == VisualizationMode.PLAYER)
        "Visualizer PLAYER · ${monitoredPlayer ?: "сессия плеера"}" else "Visualizer GLOBAL · общий микс"

    private fun updateVisualizationStatus(now: Double, signal: MotionSignal) {
        val quality = visualizer?.diagnostics?.let {
            if (it.scaling == VisualizationScaling.NORMALIZED && !it.peakRmsSupported) "Уровень относительный (NORMALIZED). " else ""
        }.orEmpty()
        val state = visualStatus?.stateAt(now) ?: VisualizationSignalState.WAITING
        val detail = when (state) {
            VisualizationSignalState.WAITING -> "Ресурс открыт, ожидание данных."
            VisualizationSignalState.NO_DATA -> "Данные не поступают ≥3 с. Эффект или аудиовыход могут быть недоступны; выберите другой способ явно."
            VisualizationSignalState.STALE -> "Данные устарели; движение автономное."
            VisualizationSignalState.QUIET -> "Тихий измеренный сигнал ≥3 с. Музыка не подтверждена; проверьте воспроизведение и громкость."
            VisualizationSignalState.UNKNOWN_LEVEL -> "Окна поступают; абсолютный уровень неизвестен. " + when {
                !settings.musicSync -> "Music Sync выключен."
                signal.mode == MotionMode.SYNCED -> "Синхронизация: ${signal.bpm!!.roundToInt()} BPM."
                else -> "Поиск темпа; движение автономное."
            }
            VisualizationSignalState.RECEIVING -> "Уровень ${"%.3f".format(java.util.Locale.ROOT, signal.energy)}. " + when {
                !settings.musicSync -> "Music Sync выключен."
                signal.mode == MotionMode.SYNCED -> "Синхронизация: ${signal.bpm!!.roundToInt()} BPM."
                else -> "Поиск темпа; движение автономное."
            }
        }
        status = "${visualizationLabel()}: $quality$detail"
    }

    private suspend fun stopAudio(keepPlayerMonitoring: Boolean = false) {
        ++audioGeneration
        val activeReader = reader; reader = null
        if (activeReader != currentCoroutineContext()[Job]) activeReader?.cancel()
        withContext(NonCancellable) {
            latestFeatures.set(null); clock.resetSource("none")
            if (!destroyed) bridge.setSource("none")
            sourceKind = null; playing = false; captureMonitor = null; captureStatusAt = -1.0; visualStatus = null
            if (!keepPlayerMonitoring) monitoredPlayer = null
            try { audio.stop() } finally {
                visualizer = null
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
            failure is SecurityException || "permission denied" in reasons || "PERMISSION_DENIED" in reasons -> "Доступ к аудио не разрешён. Сцена продолжает двигаться."
            "UNSUPPORTED_FORMAT" in reasons -> "Формат Visualizer не поддерживается устройством. Выберите другой способ анализа."
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
        discovery.close()
        runCatching { unregisterReceiver(noisy) }
        reader?.cancel(); saver?.cancel(); bridge.close()
        work.launch(NonCancellable + Dispatchers.IO) { runCatching { audio.stop() }; captureHost.close() }
        work.cancel()
        scene.removeJavascriptInterface("MusicOrbsNative"); scene.stopLoading(); scene.removeAllViews(); scene.destroy()
        super.onDestroy()
    }
}
