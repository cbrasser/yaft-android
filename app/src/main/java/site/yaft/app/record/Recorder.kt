package site.yaft.app.record

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import site.yaft.app.track.Category

/** What the tracking screen shows while a session records. */
data class Live(
    val id: String,
    val category: Category,
    /** Wall-clock start, for the timer before the first fix arrives. */
    val startedAt: Long,
    val hasFix: Boolean = false,
    val accuracyM: Float? = null,
    val points: Int = 0,
    val durationS: Double = 0.0,
    val distanceM: Double = 0.0,
    val speedKmh: Double = 0.0,
    val top3Kmh: Double = 0.0,
    val foilTimeS: Double = 0.0,
    val onFoil: Boolean = false,
    /** Set once the rider asked to stop, while the GPX is written. */
    val stopping: Boolean = false,
)

/** The recording's state, shared by the service and the screens. */
object Recorder {
    internal val _live = MutableStateFlow<Live?>(null)
    val live: StateFlow<Live?> = _live

    /** Id of the session that just finished, so the app can open it. */
    internal val _finished = MutableStateFlow<String?>(null)
    val finished: StateFlow<String?> = _finished
    fun consumeFinished() { _finished.value = null }

    fun start(context: Context, category: Category) {
        val intent = Intent(context, RecordingService::class.java)
            .setAction(RecordingService.ACTION_START)
            .putExtra(RecordingService.EXTRA_CATEGORY, category.id)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        context.startService(Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_STOP))
    }

    /** Picks a recording left behind by a killed app back up. */
    fun resume(context: Context) {
        ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_RESUME))
    }
}
