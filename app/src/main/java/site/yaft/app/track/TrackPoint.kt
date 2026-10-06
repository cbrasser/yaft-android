package site.yaft.app.track

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One GPS fix. [speedMs] is the receiver's own (Doppler) speed when it had one,
 * which the website treats like a watch's speed field; null otherwise.
 */
data class TrackPoint(val t: Long, val lat: Double, val lon: Double, val speedMs: Double?, val accuracyM: Float?) {
    fun toLine() = "$t,$lat,$lon,${speedMs ?: ""},${accuracyM ?: ""}"

    companion object {
        /** Reads a line written by [toLine]; null for a torn last line after a crash. */
        fun fromLine(line: String): TrackPoint? {
            val f = line.split(',')
            if (f.size < 5) return null
            return TrackPoint(
                t = f[0].toLongOrNull() ?: return null,
                lat = f[1].toDoubleOrNull() ?: return null,
                lon = f[2].toDoubleOrNull() ?: return null,
                speedMs = f[3].toDoubleOrNull(),
                accuracyM = f[4].toFloatOrNull(),
            )
        }
    }
}

private const val EARTH_RADIUS_M = 6_371_008.8

/** Great-circle distance in metres (same formula as lib/geo.ts). */
fun distanceM(a: TrackPoint, b: TrackPoint): Double {
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = sin(dLat / 2).let { it * it } +
        cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).let { it * it }
    return 2 * EARTH_RADIUS_M * asin(sqrt(h))
}
