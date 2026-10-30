package site.yaft.app.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

fun duration(seconds: Double): String {
    val s = seconds.toLong().coerceAtLeast(0)
    return if (s >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
    else String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60)
}

fun km(metres: Double): String = String.format(Locale.getDefault(), "%.2f km", metres / 1000)
fun kmh(v: Double): String = String.format(Locale.getDefault(), "%.1f", v)

private fun at(epochMs: Long, zone: String) = Instant.ofEpochMilli(epochMs).atZone(runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.systemDefault()))

fun dateTime(epochMs: Long, zone: String): String = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).format(at(epochMs, zone))

/** Like the website: metres under a kilometre, then km. */
fun distance(metres: Double): String =
    if (metres < 1000) "${metres.roundToInt()} m" else String.format(Locale.getDefault(), if (metres < 10_000) "%.2f km" else "%.1f km", metres / 1000)

fun pct(share: Double): String = "${(share * 100).roundToInt()} %"

private val POINTS = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")

/** The nearest of 16 compass points, as the website names wind directions. */
fun compassPoint(deg: Double): String = POINTS[(((deg % 360 + 360) % 360) / 22.5).roundToInt() % 16]

fun timeOfDay(epochMs: Long, zone: String): String = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(at(epochMs, zone))
