package site.yaft.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import site.yaft.app.data.SessionMeta
import site.yaft.app.data.SessionStore

sealed interface UploadResult {
    data object Done : UploadResult
    /** yaft already has a session that looks like this one. */
    data class Duplicate(val otherId: String) : UploadResult
    data object SignedOut : UploadResult
    data class Failed(val message: String) : UploadResult
}

/**
 * Sends a session to yaft.site the way the website's upload does: the GPX goes
 * straight into the rider's tracks/ folder, then the server analyses it and
 * stores the session (POST /api/app/sessions). The session keeps its id there.
 */
class Uploader(private val account: Account) {
    suspend fun upload(meta: SessionMeta, allowDuplicate: Boolean = false): UploadResult = withContext(Dispatchers.IO) {
        try {
            val token = account.accessToken() ?: return@withContext UploadResult.SignedOut
            val user = account.rider.value?.userId ?: return@withContext UploadResult.SignedOut
            val key = account.supabaseKey.orEmpty()
            val auth = mapOf("Authorization" to "Bearer $token", "apikey" to key)

            val stored = Http.request(
                "POST", "${account.supabaseUrl}/storage/v1/object/tracks/$user/${meta.id}.gpx",
                headers = auth, file = SessionStore.gpxFile(meta.id), contentType = "application/gpx+xml",
            )
            // Already there from an earlier try that didn't finish: the same file, so carry on.
            val alreadyThere = stored.status == 409 || stored.json.optString("error") == "Duplicate" || stored.json.optString("statusCode") == "409"
            if (!stored.ok && !alreadyThere) {
                if (stored.status == 401 || stored.status == 403) return@withContext UploadResult.SignedOut
                return@withContext UploadResult.Failed("Couldn't send the track (${stored.status}). Try again in a moment.")
            }

            val res = Http.request(
                "POST", "${account.siteUrl}/api/app/sessions",
                headers = mapOf("Authorization" to "Bearer $token"),
                json = JSONObject()
                    .put("id", meta.id)
                    .put("category", meta.category.id)
                    .put("place", meta.place)
                    .put("timeZone", meta.timeZone)
                    .put("fileName", "yaft-${meta.id.take(8)}.gpx")
                    .put("allowDuplicate", allowDuplicate),
            )
            when {
                res.status == 201 || res.ok -> {
                    SessionStore.save(meta.copy(uploadedAt = System.currentTimeMillis()))
                    UploadResult.Done
                }
                res.status == 409 && res.json.has("duplicateOf") -> UploadResult.Duplicate(res.json.getString("duplicateOf"))
                    .takeIf { it.otherId != meta.id } ?: UploadResult.Done.also { SessionStore.save(meta.copy(uploadedAt = System.currentTimeMillis())) }
                res.status == 401 -> UploadResult.SignedOut
                else -> UploadResult.Failed(res.json.optString("error").ifEmpty { "yaft couldn't save it (${res.status})." })
            }
        } catch (e: NetworkError) {
            UploadResult.Failed(e.message.orEmpty())
        }
    }
}
