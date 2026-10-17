package musicorbs.platform.audio

import android.app.ActivityManager
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Configurator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*
import musicorbs.bootstrap.MainActivity
import org.junit.Assert.assertTrue
import java.util.UUID
import java.util.regex.Pattern
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Только чтение опубликованного API страницы; private поля Main не отражаются. */
internal class P7MainUiProbe(private val scenario: ActivityScenario<MainActivity>) : AutoCloseable {
    val device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val configurator = Configurator.getInstance()
    private val previousIdleTimeout = configurator.waitForIdleTimeout
    private var originalSessionRaw: String? = null
    init { configurator.setWaitForIdleTimeout(500) }

    private fun webView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) webView(view.getChildAt(index))?.let { return it }
        return null
    }

    suspend fun evaluate(expression: String): JsonElement {
        val result = CompletableDeferred<String>()
        // onActivity на worker вызывает waitForIdleSync: на живой анимации он искажал
        // deadline readonly snapshot. Main-thread ветка API выполняет action напрямую.
        withContext(Dispatchers.Main.immediate) {
            scenario.onActivity { activity ->
                check(!activity.isDestroyed) { "Main Activity destroyed" }
                val scene = checkNotNull(webView(activity.window.decorView)) { "Main WebView missing" }
                check(scene.isAttachedToWindow) { "Main WebView not attached" }
                scene.evaluateJavascript("JSON.stringify(($expression))") { result.complete(it) }
            }
        }
        val encoded = Json.parseToJsonElement(withTimeout(4000) { result.await() })
        if (encoded == JsonNull) return JsonNull
        return Json.parseToJsonElement(encoded.jsonPrimitive.content)
    }

    suspend fun ready() {
        var lastError = "Readiness has not been evaluated"
        val available = withTimeoutOrNull(15000) {
            while (true) {
                try {
                    val value = evaluate("typeof window.musicOrbs === 'object'")
                    if (value is JsonPrimitive && value.booleanOrNull == true) return@withTimeoutOrNull true
                    lastError = "MusicOrbs API unavailable: $value"
                } catch (failure: TimeoutCancellationException) {
                    // Только timeout отдельного evaluate; внешнюю отмену не скрываем.
                    currentCoroutineContext().ensureActive()
                    lastError = "WebView readiness evaluate timed out: ${failure.message}"
                } catch (failure: IllegalStateException) {
                    if (failure.message !in setOf("Main WebView missing", "Main WebView not attached")) throw failure
                    lastError = "${failure.javaClass.simpleName}: ${failure.message}"
                }
                delay(100)
            }
            @Suppress("UNREACHABLE_CODE") false
        }
        check(available == true) { "Main readiness timed out after 15000 ms; last error: $lastError" }
        // Наличие JS API не подтверждает native restore. В P7 требуется валидный
        // исходный v2 store; сравнение чисел канонизируется тем же JSON VM.
        if (originalSessionRaw == null) {
            val session = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "session.json")
            check(session.isFile && session.length() <= 1024 * 1024) { "P7 requires a valid initial session.json" }
            val raw = session.readText(Charsets.UTF_8)
            val parsed = Json.parseToJsonElement(raw).jsonObject
            check(parsed.getValue("schemaVersion").jsonPrimitive.int == 2) { "P7 requires schemaVersion 2 before Main actions" }
            musicorbs.experience.infrastructure.WireCodec.session(raw)
            originalSessionRaw = raw
        }
        val expected = evaluate("((value)=>({settings:value.settings,shapes:value.shapes}))(JSON.parse(${JsonPrimitive(originalSessionRaw!!)}))").jsonObject
        var nativeRestoreMatched = false
        withTimeoutOrNull(15000) {
            while (true) {
                // Ошибки snapshot после появления API не скрываются и не повторяются.
                if (snapshot("native-restore-ready") == expected) { nativeRestoreMatched = true; break }
                delay(100)
            }
        }
        check(nativeRestoreMatched) { "Native restore did not match valid original session within 15000 ms" }
        p7Evidence("main-native-restore-ready", "matchedOriginalSession" to true)
    }

    suspend fun motion(): JsonObject = evaluate("window.musicOrbs.visualState()").jsonObject

    suspend fun restoreScene(baseline: JsonObject) {
        evaluate("(window.musicOrbs.command({type:'restore',snapshot:$baseline}).catch(()=>{}),true)")
        withTimeout(5000) { while (snapshot("restore-scene-check") != baseline) delay(100) }
    }

    suspend fun snapshot(stage: String = "snapshot"): JsonObject {
        val token = UUID.randomUUID().toString()
        val started = android.os.SystemClock.elapsedRealtime()
        var lastResponse: JsonObject? = null
        p7Evidence("main-snapshot-start", "label" to stage, "probeId" to token)
        // Promise читается через отдельное тестовое свойство, не меняя состояние сцены.
        try {
            evaluate("(()=>{const started=performance.now();window.__p7SnapshotProbe={token:'$token',started};window.musicOrbs.snapshot().then(value=>{window.__p7SnapshotProbe={token:'$token',started,completed:performance.now(),value}}).catch(error=>{window.__p7SnapshotProbe={token:'$token',started,completed:performance.now(),error:String(error)}});return true})()")
            val snapshot = withTimeout(5000) {
                while (true) {
                    val response = evaluate("window.__p7SnapshotProbe").jsonObject
                    lastResponse = response
                    check(response["error"] == null) { "JS snapshot failed: ${response["error"]}" }
                    if (response["token"]?.jsonPrimitive?.content == token) {
                        response["value"]?.let { return@withTimeout it.jsonObject }
                    }
                    delay(50)
                }
                @Suppress("UNREACHABLE_CODE") error("Snapshot missing")
            }
            val response = checkNotNull(lastResponse)
            p7Evidence("main-snapshot-finish", "label" to stage, "probeId" to token,
                "elapsedMs" to android.os.SystemClock.elapsedRealtime() - started,
                "jsElapsedMs" to response.getValue("completed").jsonPrimitive.double - response.getValue("started").jsonPrimitive.double,
                "responseId" to response["token"]?.jsonPrimitive?.content, "hasValue" to response.containsKey("value"))
            return snapshot
        } catch (failure: Throwable) {
            p7Evidence("main-snapshot-error", "label" to stage, "probeId" to token,
                "elapsedMs" to android.os.SystemClock.elapsedRealtime() - started,
                "responseId" to lastResponse?.get("token")?.jsonPrimitive?.content,
                "hasValue" to (lastResponse?.containsKey("value") == true),
                "jsError" to lastResponse?.get("error")?.jsonPrimitive?.content?.take(180),
                "error" to "${failure.javaClass.simpleName}: ${failure.message}".take(220))
            throw failure
        } finally { evaluate("delete window.__p7SnapshotProbe") }
    }

    suspend fun awaitMotion(description: String, predicate: (JsonObject) -> Boolean): JsonObject = withTimeout(15000) {
        while (true) {
            val value = motion()
            if (predicate(value)) return@withTimeout value
            delay(100)
        }
        @Suppress("UNREACHABLE_CODE") error(description)
    }

    suspend fun click(label: String, scroll: Boolean = false) {
        repeat(if (scroll) 9 else 5) {
            val target = device.findObject(By.text(label)) ?: device.findObject(By.desc(label))
            if (target != null) {
                val bounds = target.visibleBounds
                if (bounds.width() > 5 && bounds.height() > 5) {
                    p7Evidence("main-native-click", "label" to label, "left" to bounds.left, "top" to bounds.top,
                        "width" to bounds.width(), "height" to bounds.height())
                    target.click(); delay(300); return
                }
            }
            if (scroll) device.swipe((device.displayWidth * .75).toInt(), (device.displayHeight * .78).toInt(),
                (device.displayWidth * .75).toInt(), (device.displayHeight * .32).toInt(), 20)
            delay(200)
        }
        error("Native control not found: $label")
    }

    suspend fun acceptCaptureConsent() {
        // User-choice consent может сначала предлагать захват отдельного приложения.
        repeat(15) {
            val spinner = device.findObject(By.res("com.android.systemui:id/screen_share_mode_spinner"))
            if (spinner != null) {
                spinner.click(); delay(200)
                device.findObject(By.text(Pattern.compile("(?i)(entire screen|весь экран|весь экран устройства)")))?.click()
                delay(200)
            }
            val allow = device.findObject(By.res("android:id/button1"))
                ?: device.findObject(By.text(Pattern.compile("(?i)(start now|start recording|share screen|начать|начать запись)")))
            if (allow != null) { allow.click(); delay(500); return }
            delay(200)
        }
        p7Evidence("main-capture-consent-missing", "topPackage" to device.currentPackageName,
            "hasAllowButton" to device.hasObject(By.res("android:id/button1")),
            "hasModeSpinner" to device.hasObject(By.res("com.android.systemui:id/screen_share_mode_spinner")))
        error("Main MediaProjection consent button missing")
    }

    suspend fun closePanelIfOpen() {
        val panelText = Pattern.compile("^(Ваша скульптура|Выбрать аудиофайл|Сбросить активную форму|Звук|Выбрать микрофон|Захват внешнего воспроизведения|Стоп)$")
        if (device.hasObject(By.text(panelText)) || device.hasObject(By.descStartsWith("Форма: "))) {
            device.pressBack(); delay(250)
        }
    }

    fun captureServicePresent(context: Context): Boolean {
        @Suppress("DEPRECATION")
        val services = context.getSystemService(ActivityManager::class.java).getRunningServices(100)
        return services.any { it.service.className == CaptureProjectionService::class.java.name }
    }

    fun assertNoCaptureService(context: Context) = assertTrue("Capture foreground service survived background", !captureServicePresent(context))

    fun bounds(): IntArray {
        var bounds: IntArray? = null
        scenario.onActivity { activity ->
            val view = checkNotNull(webView(activity.window.decorView))
            val location = IntArray(2); view.getLocationOnScreen(location)
            bounds = intArrayOf(location[0], location[1], view.width, view.height)
        }
        return checkNotNull(bounds)
    }

    override fun close() { configurator.setWaitForIdleTimeout(previousIdleTimeout) }
}

/** Снимок исходного файла остаётся только в памяти; restore вызывается после DESTROYED. */
internal class P7SessionPreserver(context: Context) {
    private val file = File(context.filesDir, "session.json")
    private val original = if (file.exists()) file.readBytes() else null

    suspend fun restore() = withContext(NonCancellable + Dispatchers.IO) {
        // Даём отменённым onPause save завершить уже начатый atomic replace.
        delay(500)
        if (original == null) Files.deleteIfExists(file.toPath())
        else {
            val temporary = Files.createTempFile(checkNotNull(file.parentFile).toPath(), ".p7-restore-", ".tmp")
            try {
                Files.write(temporary, original)
                Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally { Files.deleteIfExists(temporary) }
        }
        delay(500)
        assertTrue("Original session.json must survive test cleanup byte-for-byte",
            if (original == null) !file.exists() else file.exists() && original.contentEquals(file.readBytes()))
    }
}
