package site.yaft.app.net

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/** A response as status plus body; the body is parsed as JSON where it can be. */
data class HttpResult(val status: Int, val body: String) {
    val ok get() = status in 200..299
    val json: JSONObject get() = runCatching { JSONObject(body) }.getOrDefault(JSONObject())
}

/** Plain HttpURLConnection, so the app needs no networking library. Call off the main thread. */
object Http {
    fun request(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        json: JSONObject? = null,
        file: File? = null,
        contentType: String? = null,
    ): HttpResult {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("Accept", "application/json")
            headers.forEach(conn::setRequestProperty)
            when {
                json != null -> {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.outputStream.use { it.write(json.toString().toByteArray()) }
                }
                file != null -> {
                    conn.doOutput = true
                    conn.setFixedLengthStreamingMode(file.length())
                    conn.setRequestProperty("Content-Type", contentType ?: "application/octet-stream")
                    conn.outputStream.use { out -> file.inputStream().use { it.copyTo(out) } }
                }
            }
            val status = conn.responseCode
            val stream = if (status >= 400) conn.errorStream else conn.inputStream
            return HttpResult(status, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } catch (e: IOException) {
            throw NetworkError(e)
        } finally {
            conn.disconnect()
        }
    }
}

class NetworkError(cause: Throwable) : Exception("No connection to yaft. Try again when you're online.", cause)
