package site.yaft.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import site.yaft.app.net.Account
import site.yaft.app.net.Http
import site.yaft.app.net.NetworkError

/** What loading a session's view from yaft came to. */
sealed interface ViewLoad {
    data class Ready(val view: SessionView) : ViewLoad
    /** The server is older than the view (API version 1): show the app's summary screen. */
    data object Unsupported : ViewLoad
    data object SignedOut : ViewLoad
    /** Deleted on the website, or not the rider's. */
    data object Gone : ViewLoad
    data class Failed(val message: String) : ViewLoad
}

/** Loads session [id] as the website analyses it, from GET /api/app/sessions/<id>. */
suspend fun loadSessionView(account: Account, id: String): ViewLoad = withContext(Dispatchers.IO) {
    try {
        val version = account.apiVersion() ?: return@withContext ViewLoad.Failed(NetworkError.MESSAGE)
        if (!SessionView.supported(version)) return@withContext ViewLoad.Unsupported
        val token = account.accessToken() ?: return@withContext ViewLoad.SignedOut
        val res = Http.request("GET", "${account.siteUrl}/api/app/sessions/$id", headers = mapOf("Authorization" to "Bearer $token"))
        when {
            res.status == 401 -> ViewLoad.SignedOut
            res.status == 404 -> ViewLoad.Gone
            !res.ok -> ViewLoad.Failed(res.json.optString("error").ifEmpty { "yaft couldn't load this session. Try again later." })
            else -> ViewLoad.Ready(SessionView.fromJson(res.json))
        }
    } catch (_: NetworkError) {
        ViewLoad.Failed(NetworkError.MESSAGE)
    } catch (_: Exception) {
        ViewLoad.Failed("yaft sent something unexpected. Try again later.")
    }
}
