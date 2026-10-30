package site.yaft.app.ui

import site.yaft.app.data.SessionView

/**
 * A session's track as the map draws it, built the way the website's
 * SessionMap does: GeoJSON line features off foil and on foil.
 */
class MapTrack(val off: String, val foil: String, val bounds: Bounds)

/** A box on the map, in degrees. */
data class Bounds(val west: Double, val south: Double, val east: Double, val north: Double)

/** The box around [points] of a track of [lon, lat] pairs. */
fun boundsOf(lonLat: List<DoubleArray>, points: IntRange = lonLat.indices): Bounds {
    val part = lonLat.subList(points.first, points.last + 1)
    return Bounds(part.minOf { it[0] }, part.minOf { it[1] }, part.maxOf { it[0] }, part.maxOf { it[1] })
}

fun mapTrack(view: SessionView): MapTrack {
    val coords = view.lonLat
    val off = mutableListOf<String>()
    val foil = mutableListOf<String>()
    var next = 0
    // Runs that don't fit the track (it never happens with yaft's data) are left out rather than crash the screen.
    for (run in view.runs.filter { it.points.first >= next && it.points.last < coords.size }) {
        // The stretch before a run runs on to the run's first point.
        if (run.points.first > next) off += line(coords.subList(next, run.points.first + 1))
        foil += runLines(coords, run.points, run.number, view.phase)
        next = run.points.last + 1
    }
    if (next < coords.size - 1) off += line(coords.subList(next, coords.size))
    return MapTrack(collection(off), collection(foil), boundsOf(coords))
}

/** A run, cut where its phase changes; each piece ends on the next one's first point. */
private fun runLines(coords: List<DoubleArray>, points: IntRange, run: Int, phase: List<Int>?): List<String> {
    fun at(i: Int) = phase?.getOrNull(i) ?: 0
    val out = mutableListOf<String>()
    var a = points.first
    for (i in points.first + 1..points.last + 1) {
        if (i > points.last || at(i) != at(a)) {
            val end = minOf(i, points.last)
            if (end > a) out += line(coords.subList(a, end + 1), "\"run\":$run,\"phase\":${at(a)}")
            a = i
        }
    }
    return out
}

private fun line(coords: List<DoubleArray>, props: String = ""): String {
    return """{"type":"Feature","properties":{$props},"geometry":{"type":"LineString","coordinates":[${coords.joinToString(",") { "[${it[0]},${it[1]}]" }}]}}"""
}

private fun collection(features: List<String>) = """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""
