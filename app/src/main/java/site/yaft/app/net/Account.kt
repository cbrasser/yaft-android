package site.yaft.app.net

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class SignedIn(val email: String, val userId: String)

/**
 * The rider's yaft account. The app asks the yaft server which Supabase
 * project it uses (GET /api/app/config), then signs in there with email and
 * password, the same accounts as the website. Tokens stay in app-private storage.
 */
class Account(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("account", Context.MODE_PRIVATE)
    private val refreshLock = Mutex()
    private val _rider = MutableStateFlow(currentRider())
    val rider: StateFlow<SignedIn?> = _rider

    var siteUrl: String
        get() = prefs.getString(SITE, null) ?: DEFAULT_SITE
        set(value) = prefs.edit { putString(SITE, value.trim().trimEnd('/')) }

    val supabaseUrl get() = prefs.getString(SUPABASE_URL, null)
    val supabaseKey get() = prefs.getString(SUPABASE_KEY, null)

    private fun currentRider(): SignedIn? {
        val email = prefs.getString(EMAIL, null) ?: return null
        val user = prefs.getString(USER_ID, null) ?: return null
        prefs.getString(REFRESH, null) ?: return null
        return SignedIn(email, user)
    }

    suspend fun signIn(site: String, email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val base = site.trim().trimEnd('/').ifEmpty { DEFAULT_SITE }
            val config = Http.request("GET", "$base/api/app/config")
            if (!config.ok) error(if (config.status == 404) "That server doesn't have yaft accounts." else "Couldn't reach yaft at $base.")
            val url = config.json.getString("supabaseUrl")
            val key = config.json.getString("supabaseKey")

            val res = Http.request(
                "POST", "$url/auth/v1/token?grant_type=password",
                headers = mapOf("apikey" to key),
                json = JSONObject().put("email", email.trim()).put("password", password),
            )
            if (!res.ok) error(authError(res))
            prefs.edit {
                putString(SITE, base)
                putString(SUPABASE_URL, url)
                putString(SUPABASE_KEY, key)
            }
            val previous = prefs.getString(USER_ID, null)
            saveTokens(res.json)
            if (previous != null && previous != prefs.getString(USER_ID, null)) onSignOut()
            _rider.value = currentRider()
        }
    }

    /** Called after signing out, so data of that rider is dropped. */
    var onSignOut: () -> Unit = {}

    fun signOut() {
        onSignOut()
        prefs.edit {
            remove(ACCESS); remove(REFRESH); remove(EXPIRES_AT); remove(EMAIL); remove(USER_ID)
        }
        _rider.value = null
    }

    /** A valid access token, refreshed when it's about to run out; null when the rider has to sign in again. */
    suspend fun accessToken(): String? = refreshLock.withLock {
        withContext(Dispatchers.IO) {
            val access = prefs.getString(ACCESS, null)
            if (access != null && prefs.getLong(EXPIRES_AT, 0) > System.currentTimeMillis() + 60_000) return@withContext access
            val refresh = prefs.getString(REFRESH, null) ?: return@withContext null
            val res = Http.request(
                "POST", "$supabaseUrl/auth/v1/token?grant_type=refresh_token",
                headers = mapOf("apikey" to supabaseKey.orEmpty()),
                json = JSONObject().put("refresh_token", refresh),
            )
            if (res.status in 400..499) {
                signOut()
                return@withContext null
            }
            if (!res.ok) throw NetworkError(IllegalStateException("refresh failed: ${res.status}"))
            saveTokens(res.json)
            prefs.getString(ACCESS, null)
        }
    }

    private fun saveTokens(j: JSONObject) {
        val user = j.optJSONObject("user")
        prefs.edit {
            putString(ACCESS, j.getString("access_token"))
            putString(REFRESH, j.getString("refresh_token"))
            putLong(EXPIRES_AT, System.currentTimeMillis() + j.optLong("expires_in", 3600) * 1000)
            if (user != null) {
                putString(USER_ID, user.getString("id"))
                putString(EMAIL, user.optString("email"))
            }
        }
    }

    private fun authError(res: HttpResult): String {
        val j = res.json
        return when (j.optString("error_code", j.optString("error"))) {
            "invalid_credentials", "invalid_grant" -> "Wrong email or password."
            "email_not_confirmed" -> "Confirm your email first; the link is in your inbox."
            else -> j.optString("msg").ifEmpty { j.optString("error_description") }.ifEmpty { "Couldn't sign in (${res.status})." }
        }
    }

    companion object {
        const val DEFAULT_SITE = "https://yaft.site"
        private const val SITE = "site"
        private const val SUPABASE_URL = "supabaseUrl"
        private const val SUPABASE_KEY = "supabaseKey"
        private const val ACCESS = "access"
        private const val REFRESH = "refresh"
        private const val EXPIRES_AT = "expiresAt"
        private const val EMAIL = "email"
        private const val USER_ID = "userId"
    }
}
