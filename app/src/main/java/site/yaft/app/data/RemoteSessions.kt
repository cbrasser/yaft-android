package site.yaft.app.data

import android.content.Context
import java.io.File
import java.net.URLEncoder
import java.time.OffsetDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import site.yaft.app.net.Account
import site.yaft.app.net.Http
import site.yaft.app.net.NetworkError
import site.yaft.app.track.Category

/** A session from the rider's yaft account, as the list endpoint describes it. */
data class RemoteSession(
    val id: String,
    val category: Category,
    val startedAt: Long,
    /** As the server sent it; the next page starts here. */
    val startedAtRaw: String,
    val timeZone: String,
    val place: String?,
    val device: String?,
    val durationS: Double,
    val distanceM: Double,
    val foilTimeS: Double,
    val top3Kmh: Double?,
    val summary: String,
    /** Preview track: [lon, lat] points with on-foil flags. */
    val lonLat: List<DoubleArray>,
    val onFoil: List<Boolean>,
) {
    companion object {
        fun fromJson(j: JSONObject): RemoteSession {
            val preview = j.optJSONObject("preview")
            val coords = preview?.optJSONArray("coords") ?: JSONArray()
            val flags = preview?.optJSONArray("onFoil") ?: JSONArray()
            val raw = j.getString("startedAt")
            return RemoteSession(
                id = j.getString("id"),
                category = Category.fromId(j.optString("category")),
                startedAt = OffsetDateTime.parse(raw).toInstant().toEpochMilli(),
                startedAtRaw = raw,
                timeZone = j.optString("timeZone", "UTC"),
                place = j.optString("place").takeIf { !j.isNull("place") && it.isNotBlank() },
                device = j.optString("device").takeIf { !j.isNull("device") && it.isNotBlank() },
                durationS = j.optDouble("durationS", 0.0),
                distanceM = j.optDouble("distanceM", 0.0),
                foilTimeS = j.optDouble("foilTimeS", 0.0),
                top3Kmh = if (j.isNull("top3Kmh")) null else j.optDouble("top3Kmh"),
                summary = j.optString("summary"),
                lonLat = (0 until coords.length()).map { i -> coords.getJSONArray(i).let { doubleArrayOf(it.getDouble(0), it.getDouble(1)) } },
                onFoil = (0 until flags.length()).map { flags.optInt(it) == 1 },
            )
        }
    }
}

data class RemoteState(
    val sessions: List<RemoteSession> = emptyList(),
    /** Cursor for older sessions; null when the whole history is loaded. */
    val next: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * The rider's sessions on yaft, newest first, loaded a page at a time from
 * GET /api/app/sessions and cached in a file, so the list shows offline. A
 * refresh also notices phone sessions that were deleted on the website and
 * marks them as not uploaded (the phone keeps its copy).
 */
class RemoteSessions(context: Context, private val account: Account) {
    private val cache = File(context.filesDir, "remote-sessions.json")
    private val lock = Mutex()
    private val _state = MutableStateFlow(readCache())
    val state: StateFlow<RemoteState> = _state

    /** Reloads from the newest session, fetching as many pages as it takes to cover the phone's uploaded sessions. */
    suspend fun refresh() = load(from = null)

    suspend fun loadMore() {
        val next = _state.value.next ?: return
        load(from = next)
    }

    fun clear() {
        cache.delete()
        _state.value = RemoteState()
    }

    private suspend fun load(from: String?) = lock.withLock {
        if (account.rider.value == null) return@withLock
        _state.update { it.copy(loading = true, error = null) }
        val startedAt = System.currentTimeMillis()
        try {
            val fetched = mutableListOf<RemoteSession>()
            var cursor = from
            var next: String?
            val oldestUploaded = SessionStore.sessions.value.filter { it.uploadedAt != null }.minOfOrNull { it.startedAt }
            do {
                val page = fetchPage(cursor) ?: return@withLock
                val known = fetched.mapTo(HashSet()) { it.id }
                fetched += page.first.filter { it.id !in known }
                next = page.second
                cursor = next
                // Keep going until the page reaches back past the oldest uploaded phone session.
            } while (from == null && next != null && oldestUploaded != null && (fetched.lastOrNull()?.startedAt ?: Long.MIN_VALUE) > oldestUploaded)

            val sessions = if (from == null) fetched else {
                val known = _state.value.sessions.mapTo(HashSet()) { it.id }
                _state.value.sessions + fetched.filter { it.id !in known }
            }
            val state = RemoteState(sessions, next)
            _state.value = state
            writeCache(state)
            markDeleted(state, startedAt)
        } catch (e: NetworkError) {
            _state.update { it.copy(loading = false, error = "Couldn't reach yaft. Showing the last list.") }
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = "yaft sent something unexpected. Try again later.") }
        }
    }

    /** One page: the sessions and the next cursor, or null (and signed out) when the token is refused. */
    private suspend fun fetchPage(before: String?): Pair<List<RemoteSession>, String?>? = withContext(Dispatchers.IO) {
        val token = account.accessToken()
        if (token == null) {
            _state.update { it.copy(loading = false) }
            return@withContext null
        }
        val query = before?.let { "?before=" + URLEncoder.encode(it, "UTF-8") }.orEmpty()
        val res = Http.request("GET", "${account.siteUrl}/api/app/sessions$query", headers = mapOf("Authorization" to "Bearer $token"))
        if (res.status == 401) {
            account.signOut()
            _state.update { it.copy(loading = false) }
            return@withContext null
        }
        if (!res.ok) throw IllegalStateException("list failed: ${res.status}")
        val list = res.json.getJSONArray("sessions")
        val sessions = (0 until list.length()).map { RemoteSession.fromJson(list.getJSONObject(it)) }
        sessions to res.json.optString("next").takeIf { !res.json.isNull("next") && it.isNotEmpty() }
    }

    /**
     * A phone session marked uploaded that isn't in the list, although the list
     * reaches back past it, was deleted on the website. Only sessions uploaded
     * before this refresh began count, so a fresh upload can't be mistaken for one.
     */
    private fun markDeleted(state: RemoteState, refreshStartedAt: Long) {
        val ids = state.sessions.mapTo(HashSet()) { it.id }
        val coveredFrom = if (state.next == null) Long.MIN_VALUE else state.sessions.lastOrNull()?.startedAt ?: return
        SessionStore.sessions.value
            .filter { it.uploadedAt != null && it.uploadedAt < refreshStartedAt && it.startedAt >= coveredFrom && it.id !in ids }
            .forEach { SessionStore.save(it.copy(uploadedAt = null)) }
    }

    private fun readCache(): RemoteState = runCatching {
        val j = JSONObject(cache.readText())
        val list = j.getJSONArray("sessions")
        RemoteState(
            sessions = (0 until list.length()).map { RemoteSession.fromJson(list.getJSONObject(it)) },
            next = j.optString("next").takeIf { !j.isNull("next") && it.isNotEmpty() },
        )
    }.getOrDefault(RemoteState())

    private fun writeCache(state: RemoteState) = runCatching {
        val list = JSONArray()
        state.sessions.forEach { s ->
            list.put(
                JSONObject()
                    .put("id", s.id).put("category", s.category.id).put("startedAt", s.startedAtRaw).put("timeZone", s.timeZone)
                    .put("place", s.place ?: JSONObject.NULL).put("device", s.device ?: JSONObject.NULL)
                    .put("durationS", s.durationS).put("distanceM", s.distanceM).put("foilTimeS", s.foilTimeS)
                    .put("top3Kmh", s.top3Kmh ?: JSONObject.NULL).put("summary", s.summary)
                    .put("preview", JSONObject()
                        .put("coords", JSONArray().apply { s.lonLat.forEach { put(JSONArray().put(it[0]).put(it[1])) } })
                        .put("onFoil", JSONArray().apply { s.onFoil.forEach { put(if (it) 1 else 0) } })),
            )
        }
        val tmp = File(cache.path + ".tmp")
        tmp.writeText(JSONObject().put("sessions", list).put("next", state.next ?: JSONObject.NULL).toString())
        tmp.renameTo(cache)
    }
}
