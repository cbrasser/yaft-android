package site.yaft.app.record

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.io.BufferedWriter
import java.io.FileWriter
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import site.yaft.app.MainActivity
import site.yaft.app.R
import site.yaft.app.data.Recording
import site.yaft.app.data.SessionStore
import site.yaft.app.track.Category
import site.yaft.app.track.LiveStats
import site.yaft.app.track.TrackPoint

/**
 * Records GPS at 1 Hz as a foreground service, so it keeps going with the
 * screen off in a pouch. Each fix is appended to the recording file right
 * away; if Android kills the service it restarts and carries on in the same file.
 */
class RecordingService : Service(), LocationListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recording: Recording? = null
    private var stats: LiveStats? = null
    private var writer: BufferedWriter? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var startedAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        SessionStore.init(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stop()
            ACTION_START -> if (recording == null) begin(intent.getStringExtra(EXTRA_CATEGORY))
            // A null intent is Android restarting a killed service: resume what's on disk.
            else -> if (recording == null) begin(null)
        }
        return START_STICKY
    }

    private fun begin(categoryId: String?) {
        val r = SessionStore.unfinishedRecording()
            ?: categoryId?.let { SessionStore.startRecording(UUID.randomUUID().toString(), Category.fromId(it)) }
        if (r == null) {
            stopSelf()
            return
        }
        recording = r
        val previous = SessionStore.recordedPoints(r.id)
        stats = LiveStats.of(previous, r.category)
        startedAt = previous.firstOrNull()?.t ?: System.currentTimeMillis()
        Recorder._live.value = Live(r.id, r.category, startedAt).withStats(stats!!)

        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(r.category),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
        writer = BufferedWriter(FileWriter(SessionStore.recordingCsv(r.id), true))
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "yaft:recording")
            .apply { acquire(12 * 60 * 60 * 1000L) }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stop()
            return
        }
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
    }

    override fun onLocationChanged(location: Location) {
        val s = stats ?: return
        val w = writer ?: return
        val accuracy = if (location.hasAccuracy()) location.accuracy else null
        if (accuracy != null && accuracy > MAX_ACCURACY_M) {
            Recorder._live.value = Recorder._live.value?.copy(hasFix = false, accuracyM = accuracy)
            return
        }
        val p = TrackPoint(
            t = location.time,
            lat = location.latitude,
            lon = location.longitude,
            speedMs = if (location.hasSpeed()) location.speed.toDouble() else null,
            accuracyM = accuracy,
        )
        if (p.t <= s.lastT) return
        w.write(p.toLine())
        w.newLine()
        w.flush()
        s.add(p)
        Recorder._live.value = Recorder._live.value?.withStats(s)?.copy(hasFix = true, accuracyM = accuracy)
    }

    @Deprecated("Needed below API 29")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {
        Recorder._live.value = Recorder._live.value?.copy(hasFix = false)
    }

    private fun stop() {
        val r = recording
        (getSystemService(LOCATION_SERVICE) as LocationManager).removeUpdates(this)
        runCatching { writer?.close() }
        writer = null
        recording = null
        stats = null
        Recorder._live.value = Recorder._live.value?.copy(stopping = true)
        scope.launch {
            val meta = r?.let { SessionStore.finishRecording(it) }
            Recorder._live.value = null
            Recorder._finished.value = meta?.id
            wakeLock?.takeIf { it.isHeld }?.release()
            ServiceCompat.stopForeground(this@RecordingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        // Destroyed without a stop (rare): leave the file for the next start to resume.
        if (recording != null) {
            (getSystemService(LOCATION_SERVICE) as LocationManager).removeUpdates(this)
            runCatching { writer?.close() }
            wakeLock?.takeIf { it.isHeld }?.release()
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(category: Category): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.channel_recording), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.recording_title, category.label))
            .setContentText(getString(R.string.recording_text))
            .setWhen(startedAt)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val ACTION_START = "site.yaft.app.START"
        const val ACTION_STOP = "site.yaft.app.STOP"
        const val ACTION_RESUME = "site.yaft.app.RESUME"
        const val EXTRA_CATEGORY = "category"
        private const val CHANNEL = "recording"
        private const val NOTIFICATION_ID = 1
        /** Fixes vaguer than this are skipped (they draw zigzags and fake speed). */
        private const val MAX_ACCURACY_M = 30f
    }
}

private fun Live.withStats(s: LiveStats) = copy(
    points = s.points,
    durationS = s.durationS,
    distanceM = s.distanceM,
    speedKmh = s.speedKmh,
    top3Kmh = s.top3Kmh,
    foilTimeS = s.foilTimeS,
    onFoil = s.onFoil,
)
