package site.yaft.app.data

import org.json.JSONObject

/** What the map colours the on-foil track by, as on the website: phase (on foil), speed or heart rate. */
enum class ColorMode(val id: String, val label: String) {
    FOIL("phase", "On foil"),
    SPEED("speed", "Speed"),
    HR("hr", "Heart rate");

    /** The map layer that draws the on-foil track in this mode. */
    val layer: String get() = if (this == FOIL) "track-foil" else "track-$id"
}

/** The span a mode's ramp covers, low to high, in [unit] (km/h or bpm). */
data class Ramp(val mode: ColorMode, val low: Double, val high: Double, val unit: String)

/**
 * How a session's track is coloured, from the `colors` of GET /api/app/sessions/<id>
 * (the website's colorKey): each ramp's range, and whether heart rate is available.
 * The website works the ranges out, so the app's colours match its map.
 */
data class ColorKey(val heartRate: Boolean, val ramps: List<Ramp>) {
    /** The switch's choices: On foil always, then each ramp the server sent. */
    val modes: List<ColorMode> get() = listOf(ColorMode.FOIL) + ramps.map { it.mode }

    fun ramp(mode: ColorMode): Ramp? = ramps.firstOrNull { it.mode == mode }

    companion object {
        /** A server from before the colour key: on foil only. */
        val NONE = ColorKey(false, emptyList())

        fun fromJson(colors: JSONObject?): ColorKey {
            colors ?: return NONE
            val heartRate = colors.optBoolean("heartRate")
            val ranges = colors.optJSONArray("ranges") ?: return NONE
            val ramps = (0 until ranges.length()).mapNotNull { i ->
                val r = ranges.optJSONObject(i) ?: return@mapNotNull null
                // Modes a newer server adds are left out, and heart rate counts only when the server offers it.
                val mode = ColorMode.entries.firstOrNull { it != ColorMode.FOIL && it.id == r.optString("mode") } ?: return@mapNotNull null
                if (mode == ColorMode.HR && !heartRate) return@mapNotNull null
                val range = r.optJSONArray("range") ?: return@mapNotNull null
                val low = range.optDouble(0)
                val high = range.optDouble(1)
                if (low.isNaN() || high.isNaN() || high <= low) null else Ramp(mode, low, high, r.optString("unit"))
            }
            return ColorKey(heartRate && ramps.any { it.mode == ColorMode.HR }, ramps)
        }
    }
}
