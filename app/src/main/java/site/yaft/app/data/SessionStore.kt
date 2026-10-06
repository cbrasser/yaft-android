package site.yaft.app.data

import android.content.Context
import java.io.File
import java.util.TimeZone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import site.yaft.app.track.Category
import site.yaft.app.track.Gpx
import site.yaft.app.track.LiveStats
import site.yaft.app.track.TrackPoint

/** A recorded session as kept on the phone, next to its GPX file. */
data class SessionMeta(
    val id: String,
    val category: Category,
    val startedAt: Long,
    val timeZone: String,
    val durationS: Double,
    val distanceM: Double,
    val foilTimeS: Double,
    val top3Kmh: Double,
    val points: Int,
    val place: String = "",
    /** When it reached yaft.site; the session there has the same id. */
    val uploadedAt: Long? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("category", category.id).put("startedAt", startedAt).put("timeZone", timeZone)
        .put("durationS", durationS).put("distanceM", distanceM).put("foilTimeS", foilTimeS).put("top3Kmh", top3Kmh)
        .put("points", points).put("place", place).put("uploadedAt", uploadedAt ?: JSONObject.NULL)

    companion object {
        fun fromJson(j: JSONObject) = SessionMeta(
            id = j.getString("id"),
            category = Category.fromId(j.optString("category")),
            startedAt = j.getLong("startedAt"),
            timeZone = j.optString("timeZone", "UTC"),
            durationS = j.optDouble("durationS", 0.0),
            distanceM = j.optDouble("distanceM", 0.0),
            foilTimeS = j.optDouble("foilTimeS", 0.0),
            top3Kmh = j.optDouble("top3Kmh", 0.0),
            points = j.optInt("points", 0),
            place = j.optString("place", ""),
            uploadedAt = if (j.isNull("uploadedAt")) null else j.optLong("uploadedAt"),
        )

        fun of(id: String, category: Category, points: List<TrackPoint>, timeZone: String, place: String = "", uploadedAt: Long? = null): SessionMeta {
            val stats = LiveStats.of(points, category)
            return SessionMeta(id, category, stats.startT, timeZone, stats.durationS, stats.distanceM, stats.foilTimeS, stats.top3Kmh, stats.points, place, uploadedAt)
        }
    }
}

/** What a recording in progress keeps on disk, so it survives the app being killed. */
data class Recording(val id: String, val category: Category, val timeZone: String)

/**
 * Sessions live in files/sessions as <id>.gpx plus <id>.json. A recording in
 * progress is files/recording/<id>.csv (one point per line, appended as it
 * arrives) plus <id>.json, and becomes a session when it's finished.
 */
object SessionStore {
    private lateinit var sessionsDir: File
    private lateinit var recordingDir: File
    private val _sessions = MutableStateFlow<List<SessionMeta>>(emptyList())
    val sessions: StateFlow<List<SessionMeta>> = _sessions

    fun init(context: Context) {
        if (::sessionsDir.isInitialized) return
        sessionsDir = File(context.filesDir, "sessions").apply { mkdirs() }
        recordingDir = File(context.filesDir, "recording").apply { mkdirs() }
        reload()
    }

    fun gpxFile(id: String) = File(sessionsDir, "$id.gpx")
    private fun metaFile(id: String) = File(sessionsDir, "$id.json")

    @Synchronized
    fun reload() {
        _sessions.value = sessionsDir.listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { f -> runCatching { SessionMeta.fromJson(JSONObject(f.readText())) }.getOrNull() }
            .sortedByDescending { it.startedAt }
    }

    fun get(id: String): SessionMeta? = _sessions.value.firstOrNull { it.id == id }

    fun loadPoints(id: String): List<TrackPoint> = runCatching { Gpx.read(gpxFile(id)) }.getOrDefault(emptyList())

    @Synchronized
    fun save(meta: SessionMeta) {
        val tmp = File(sessionsDir, "${meta.id}.json.tmp")
        tmp.writeText(meta.toJson().toString())
        tmp.renameTo(metaFile(meta.id))
        reload()
    }

    /** Changes the category; the on-foil numbers depend on it, so they're worked out again. */
    fun setCategory(id: String, category: Category) {
        val meta = get(id) ?: return
        save(SessionMeta.of(id, category, loadPoints(id), meta.timeZone, meta.place, meta.uploadedAt))
    }

    @Synchronized
    fun delete(id: String) {
        gpxFile(id).delete()
        metaFile(id).delete()
        reload()
    }

    // Recording in progress

    fun recordingCsv(id: String) = File(recordingDir, "$id.csv")
    private fun recordingMeta(id: String) = File(recordingDir, "$id.json")

    fun startRecording(id: String, category: Category): Recording {
        val r = Recording(id, category, TimeZone.getDefault().id)
        recordingMeta(id).writeText(JSONObject().put("id", id).put("category", category.id).put("timeZone", r.timeZone).toString())
        recordingCsv(id).createNewFile()
        return r
    }

    /** A recording left on disk (the app was killed mid-session), if any. */
    fun unfinishedRecording(): Recording? {
        val f = recordingDir.listFiles { f -> f.extension == "json" }?.firstOrNull() ?: return null
        return runCatching {
            val j = JSONObject(f.readText())
            Recording(j.getString("id"), Category.fromId(j.optString("category")), j.optString("timeZone", "UTC"))
        }.getOrNull()
    }

    fun recordedPoints(id: String): List<TrackPoint> =
        recordingCsv(id).takeIf { it.exists() }?.readLines()?.mapNotNull(TrackPoint::fromLine).orEmpty()

    /**
     * Turns a recording into a session: writes the GPX and its summary, then
     * removes the recording. Returns null (and drops it) when it has fewer
     * than two points, since there's nothing to show.
     */
    @Synchronized
    fun finishRecording(r: Recording): SessionMeta? {
        val points = recordedPoints(r.id)
        val meta = if (points.size < 2) null else {
            val tmp = File(sessionsDir, "${r.id}.gpx.tmp")
            tmp.bufferedWriter().use { Gpx.write(it, points, r.category.label) }
            tmp.renameTo(gpxFile(r.id))
            SessionMeta.of(r.id, r.category, points, r.timeZone).also(::save)
        }
        recordingCsv(r.id).delete()
        recordingMeta(r.id).delete()
        return meta
    }
}
