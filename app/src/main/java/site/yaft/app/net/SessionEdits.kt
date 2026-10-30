package site.yaft.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import site.yaft.app.data.SessionView
import site.yaft.app.track.Category

/**
 * An edit of a session on yaft, sent as PATCH /api/app/sessions/<id> (API
 * version 2). Each form sends its own fields; the website leaves the others as
 * they are. Fields go as typed, so the website checks them and answers with its
 * own messages.
 */
sealed interface SessionEdit {
    fun toJson(): JSONObject

    /** What the rider rode; yaft redoes the analysis for it. */
    data class Ridden(val category: Category) : SessionEdit {
        override fun toJson(): JSONObject = JSONObject().put("category", category.id)
    }

    /**
     * Where the wind came from (degrees; empty: yaft estimates it) and its
     * strength in knots, base and gusts. Null isn't sent: the direction when
     * it didn't change, the strength when it didn't or the category has none.
     */
    data class Wind(val fromDeg: String?, val base: String?, val gust: String?) : SessionEdit {
        override fun toJson(): JSONObject = JSONObject().apply {
            if (fromDeg != null) put("windFrom", typed(fromDeg))
            if (base != null || gust != null) {
                put("windBase", typed(base.orEmpty()))
                put("windGust", typed(gust.orEmpty()))
            }
        }
    }

    /**
     * Gear ids per slot column (board_id…); null is none. A slot left out stays
     * as it is. yaft refuses gear on a split session, whose gear is on its parts.
     */
    data class Gear(val picks: Map<String, String?>) : SessionEdit {
        override fun toJson(): JSONObject = JSONObject().put("gear", JSONObject().apply { picks.forEach { (slot, id) -> put(slot, id ?: JSONObject.NULL) } })
    }

    /** The rider's own notes and rating (1 to 5, or none); only they see them. */
    data class Notes(val notes: String, val rating: Int?) : SessionEdit {
        override fun toJson(): JSONObject = JSONObject().put("notes", notes).put("rating", rating ?: JSONObject.NULL)
    }
}

/** A field as typed: empty is null, a whole number a number, anything else the text. */
private fun typed(text: String): Any {
    val t = text.trim()
    return if (t.isEmpty()) JSONObject.NULL else t.toIntOrNull() ?: t
}

/** What sending an edit came to. Nothing is kept to send later: an edit that fails is the rider's to try again. */
sealed interface EditResult {
    /** Saved; the session as the website now analyses it. */
    data class Saved(val view: SessionView) : EditResult
    /** The website refused the input; its message says why. */
    data class Invalid(val message: String) : EditResult
    data object SignedOut : EditResult
    /** Deleted on the website, or not the rider's. */
    data object Gone : EditResult
    data class Failed(val message: String) : EditResult

    companion object {
        const val TRY_AGAIN = "yaft couldn't save that. Try again later."
        const val UNEXPECTED = "yaft sent something unexpected. Check the session on the website."
        const val OLD_SERVER = "This yaft server can't save edits from the app yet. Edit the session on the website."
    }
}

/** Reads the answer to PATCH /api/app/sessions/<id>. */
fun editResult(status: Int, body: String): EditResult {
    val json = runCatching { JSONObject(body) }.getOrDefault(JSONObject())
    val error = json.optString("error")
    return when {
        status == 401 -> EditResult.SignedOut
        status == 404 -> EditResult.Gone
        status == 405 -> EditResult.Failed(EditResult.OLD_SERVER)
        // 409: gear on a split session, or gear that's no longer the rider's.
        status in setOf(400, 409) && error.isNotEmpty() -> EditResult.Invalid(error)
        status !in 200..299 -> EditResult.Failed(error.ifEmpty { EditResult.TRY_AGAIN })
        else -> runCatching { EditResult.Saved(SessionView.fromJson(json)) }.getOrDefault(EditResult.Failed(EditResult.UNEXPECTED))
    }
}

/** Sends [edit] for session [id] on yaft. Needs a connection; offline it fails at once. */
suspend fun saveSessionEdit(account: Account, id: String, edit: SessionEdit): EditResult = withContext(Dispatchers.IO) {
    try {
        val token = account.accessToken() ?: return@withContext EditResult.SignedOut
        val res = Http.request("PATCH", "${account.siteUrl}/api/app/sessions/$id", headers = mapOf("Authorization" to "Bearer $token"), json = edit.toJson())
        editResult(res.status, res.body)
    } catch (_: NetworkError) {
        EditResult.Failed(NetworkError.MESSAGE)
    }
}
