package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.TrackingMode
import com.example.domain.DateStreakUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.util.Locale

data class WorkoutServiceSnapshot(
    val sessionId: String,
    val sportThaiName: String,
    val trackingMode: TrackingMode,
    val elapsedSeconds: Int,
    val distanceMeters: Int,
    val caloriesBurned: Int,
    val isRunning: Boolean,
    val isAutoPaused: Boolean,
    val isAutoPauseEnabled: Boolean
)

object WorkoutLiveNotificationBus {
    val togglePauseEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val snapshotFlow = MutableStateFlow<WorkoutServiceSnapshot?>(null)
    val isServiceActive = MutableStateFlow(false)
}

class WorkoutTrackingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotificationAtMs: Long = 0L
    private var lastRunningState: Boolean? = null
    private var lastAutoPauseState: Boolean? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PAUSE -> {
                WorkoutLiveNotificationBus.togglePauseEvents.tryEmit(Unit)
                if (!WorkoutLiveNotificationBus.isServiceActive.value) {
                    stopSelf()
                }
                return START_NOT_STICKY
            }
            ACTION_STOP_TRACKING -> {
                stopForegroundSafely()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (!hasLocationPermission(this)) {
            Log.w(TAG, "Cannot start location foreground service without location permission")
            stopForegroundSafely()
            stopSelf()
            return START_NOT_STICKY
        }

        ensureNotificationChannel()
        val snapshot = WorkoutLiveNotificationBus.snapshotFlow.value ?: WorkoutServiceSnapshot(
            sessionId = "",
            sportThaiName = "กิจกรรมกลางแจ้ง",
            trackingMode = TrackingMode.SENSOR_GPS,
            elapsedSeconds = 0,
            distanceMeters = 0,
            caloriesBurned = 0,
            isRunning = true,
            isAutoPaused = false,
            isAutoPauseEnabled = true
        )

        val started = try {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                0
            }
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(snapshot),
                fgsType
            )
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission missing for foreground service: ${e.message}")
            false
        } catch (e: Exception) {
            Log.w(TAG, "Foreground service start blocked: ${e.message}")
            false
        }

        if (!started) {
            WorkoutLiveNotificationBus.isServiceActive.value = false
            stopSelf()
            return START_NOT_STICKY
        }

        WorkoutLiveNotificationBus.isServiceActive.value = true
        updateWakeLockForState(snapshot)
        startSnapshotObserverIfNeeded()
        return START_NOT_STICKY
    }

    private fun startSnapshotObserverIfNeeded() {
        if (observerJob?.isActive == true) return
        observerJob = serviceScope.launch {
            WorkoutLiveNotificationBus.snapshotFlow
                .filterNotNull()
                .collect { snap ->
                    updateWakeLockForState(snap)
                    val now = System.currentTimeMillis()
                    val stateChanged = lastRunningState != snap.isRunning || lastAutoPauseState != snap.isAutoPaused
                    if (stateChanged || (now - lastNotificationAtMs) >= 900L) {
                        lastRunningState = snap.isRunning
                        lastAutoPauseState = snap.isAutoPaused
                        lastNotificationAtMs = now
                        val mgr = getSystemService(NotificationManager::class.java)
                        runCatching {
                            mgr?.notify(NOTIFICATION_ID, buildNotification(snap))
                        }
                    }
                }
        }
    }

    private fun updateWakeLockForState(snapshot: WorkoutServiceSnapshot) {
        if (snapshot.isRunning && !snapshot.isAutoPaused) {
            acquireWakeLock()
        } else {
            releaseWakeLock()
        }
    }

    private fun acquireWakeLock() {
        val current = wakeLock
        if (current?.isHeld == true) return
        runCatching {
            val pm = getSystemService(PowerManager::class.java) ?: return
            val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
                setReferenceCounted(false)
                acquire(WAKELOCK_TIMEOUT_MS)
            }
            wakeLock = wl
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
        wakeLock = null
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java) ?: return
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "การติดตามเส้นทางและเวลาออกกำลังกาย",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "แสดงระยะทาง เวลา และความเร็วแบบเรียลไทม์บนหน้าจอล็อกขณะออกกำลังกาย"
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                mgr.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(snapshot: WorkoutServiceSnapshot): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            100,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = Intent(this, WorkoutTrackingService::class.java).apply {
            action = ACTION_TOGGLE_PAUSE
        }
        val togglePendingIntent = PendingIntent.getService(
            this,
            101,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusSuffix = when {
            !snapshot.isRunning -> " (หยุดชั่วคราว)"
            snapshot.isAutoPaused -> " (พักอัตโนมัติ)"
            else -> ""
        }
        val title = "MOVE • ${snapshot.sportThaiName}$statusSuffix"

        val clock = DateStreakUtils.formatDurationClock(snapshot.elapsedSeconds)
        val dist = DateStreakUtils.formatDistanceKm(snapshot.distanceMeters)
        val paceLabel = if (snapshot.distanceMeters >= 50 && snapshot.elapsedSeconds > 0) {
            val secPerKm = (snapshot.elapsedSeconds * 1000.0 / snapshot.distanceMeters).toInt()
            String.format(Locale.US, "%d:%02d น./กม.", secPerKm / 60, secPerKm % 60)
        } else {
            "--:-- น./กม."
        }
        val body = "$clock • $dist • เพซ $paceLabel • ${snapshot.caloriesBurned} kcal"
        val actionLabel = if (snapshot.isRunning) "หยุดชั่วคราว" else "ทำต่อ"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_move)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setColor(0xFF1B4332.toInt())
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, actionLabel, togglePendingIntent)
            .build()
    }

    private fun stopForegroundSafely() {
        releaseWakeLock()
        WorkoutLiveNotificationBus.isServiceActive.value = false
        runCatching {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
    }

    override fun onDestroy() {
        observerJob?.cancel()
        serviceScope.cancel()
        releaseWakeLock()
        WorkoutLiveNotificationBus.isServiceActive.value = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WorkoutTrackingService"
        private const val CHANNEL_ID = "move_live_workout"
        private const val NOTIFICATION_ID = 4102
        private const val WAKELOCK_TAG = "MOVE:WorkoutTrackingWakeLock"
        private const val WAKELOCK_TIMEOUT_MS = 4 * 60 * 60 * 1000L

        const val ACTION_SYNC = "com.example.action.SYNC_WORKOUT_SERVICE"
        const val ACTION_TOGGLE_PAUSE = "com.example.action.TOGGLE_PAUSE_WORKOUT"
        const val ACTION_STOP_TRACKING = "com.example.action.STOP_WORKOUT_SERVICE"

        fun hasLocationPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        fun syncState(context: Context, snapshot: WorkoutServiceSnapshot) {
            if (!hasLocationPermission(context)) {
                stopService(context)
                return
            }
            WorkoutLiveNotificationBus.snapshotFlow.value = snapshot
            if (!WorkoutLiveNotificationBus.isServiceActive.value) {
                val intent = Intent(context, WorkoutTrackingService::class.java).apply {
                    action = ACTION_SYNC
                }
                runCatching {
                    ContextCompat.startForegroundService(context, intent)
                }
            }
        }

        fun stopService(context: Context) {
            WorkoutLiveNotificationBus.snapshotFlow.value = null
            if (WorkoutLiveNotificationBus.isServiceActive.value) {
                WorkoutLiveNotificationBus.isServiceActive.value = false
                runCatching {
                    context.stopService(Intent(context, WorkoutTrackingService::class.java))
                }
            }
        }
    }
}
