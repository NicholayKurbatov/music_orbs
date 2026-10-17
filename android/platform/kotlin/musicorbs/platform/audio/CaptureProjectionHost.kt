package musicorbs.platform.audio

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

/** Данные системного согласия не сохраняются и не переиспользуются после close. */
class CaptureProjectionHost(context: Context) {
    private val appContext = context.applicationContext
    private var connection: ServiceConnection? = null
    private var service: CaptureProjectionService? = null
    private var pending: CompletableDeferred<CaptureProjectionService>? = null
    private val generation = AtomicLong()
    private val main = Handler(Looper.getMainLooper())

    suspend fun acquire(resultCode: Int, data: Intent): MediaProjection = withContext(Dispatchers.Main.immediate) {
        require(resultCode == Activity.RESULT_OK) { "SOURCE_UNAVAILABLE: capture consent denied" }
        val token = generation.incrementAndGet()
        closeState()
        val ready = CompletableDeferred<CaptureProjectionService>()
        pending = ready
        val next = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (generation.get() == token && connection === this) {
                    ready.complete((binder as CaptureProjectionService.LocalBinder).service)
                }
            }
            override fun onServiceDisconnected(name: ComponentName) {
                if (generation.get() == token && connection === this) {
                    ready.completeExceptionally(IllegalStateException("SOURCE_UNAVAILABLE: capture service disconnected"))
                }
            }
            override fun onBindingDied(name: ComponentName) {
                if (generation.get() == token && connection === this) {
                    ready.completeExceptionally(IllegalStateException("SOURCE_UNAVAILABLE: capture service binding died"))
                }
            }
            override fun onNullBinding(name: ComponentName) {
                if (generation.get() == token && connection === this) {
                    ready.completeExceptionally(IllegalStateException("SOURCE_UNAVAILABLE: capture service has no binder"))
                }
            }
        }
        connection = next
        try {
            appContext.startForegroundService(Intent(appContext, CaptureProjectionService::class.java))
            check(appContext.bindService(Intent(appContext, CaptureProjectionService::class.java), next, Context.BIND_AUTO_CREATE)) {
                "SOURCE_UNAVAILABLE: cannot bind capture service"
            }
            val current = withTimeoutOrNull(10000) { ready.await() }
                ?: error("SOURCE_UNAVAILABLE: capture service binding timed out")
            check(generation.get() == token && connection === next) { "SOURCE_UNAVAILABLE: capture binding retired" }
            pending = null
            service = current
            current.createProjection(resultCode, data)
        } catch (failure: Throwable) {
            if (generation.get() == token && connection === next) {
                generation.incrementAndGet()
                closeState()
            }
            throw failure
        }
    }

    fun close() {
        val token = generation.incrementAndGet()
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { if (generation.get() == token) closeState() }
            return
        }
        closeState()
    }

    private fun closeState() {
        pending?.completeExceptionally(CancellationException("Capture binding retired"))
        pending = null
        service?.releaseProjection()
        service = null
        connection?.let { runCatching { appContext.unbindService(it) } }
        connection = null
        appContext.stopService(Intent(appContext, CaptureProjectionService::class.java))
    }
}

/** FGS требуется системой до получения MediaProjection на Android 14+. */
class CaptureProjectionService : Service() {
    inner class LocalBinder : Binder() { val service: CaptureProjectionService get() = this@CaptureProjectionService }
    private val binder = LocalBinder()
    private var projection: MediaProjection? = null
    private var callback: MediaProjection.Callback? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Захват воспроизведения", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        foreground()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun foreground() {
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Music Orbs")
            .setContentText("Захват воспроизведения включён")
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    fun createProjection(resultCode: Int, data: Intent): MediaProjection {
        check(projection == null) { "SOURCE_UNAVAILABLE: projection already active" }
        foreground()
        val next = getSystemService(MediaProjectionManager::class.java).getMediaProjection(resultCode, data)
            ?: error("SOURCE_UNAVAILABLE: no projection")
        val nextCallback = object : MediaProjection.Callback() {
            override fun onStop() {
                if (projection !== next) return
                projection = null
                callback = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        next.registerCallback(nextCallback, Handler(Looper.getMainLooper()))
        callback = nextCallback
        projection = next
        return next
    }

    fun releaseProjection() {
        projection?.let { current ->
            projection = null
            callback?.let { previous -> runCatching { current.unregisterCallback(previous) } }
            callback = null
            runCatching { current.stop() }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        releaseProjection()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "music-orbs-playback-capture"
        private const val NOTIFICATION = 1001
    }
}
