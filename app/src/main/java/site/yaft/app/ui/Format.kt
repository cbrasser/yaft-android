package site.yaft.app.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

fun duration(seconds: Double): String {
    val s = seconds.toLong().coerceAtLeast(0)
    return if (s >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
    else String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60)
}

fun km(metres: Double): String = String.format(Locale.getDefault(), "%.2f km", metres / 1000)
fun kmh(v: Double): String = String.format(Locale.getDefault(), "%.1f", v)

fun dateTime(epochMs: Long, zone: String): String {
    val z = runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.systemDefault())
    return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).format(Instant.ofEpochMilli(epochMs).atZone(z))
}
