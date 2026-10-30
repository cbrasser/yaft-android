package site.yaft.app.data

import java.time.OffsetDateTime
import org.json.JSONArray
import org.json.JSONObject
import site.yaft.app.track.Category

/** Numbers for the whole session or one run, as the website's analysis gives them (seconds, metres, km/h, bpm). */
data class Stats(
    val duration: Double,
    val distance: Double,
    val avgSpeed: Double?,
    val top3: Double?,
    val top10: Double?,
    val avgHr: Double?,
    val maxHr: Double?,
) {
    companion object {
        fun fromJson(j: JSONObject) = Stats(
            duration = j.optDouble("duration", 0.0),
            distance = j.optDouble("distance", 0.0),
            avgSpeed = j.num("avgSpeed"),
            top3 = j.num("top3"),
            top10 = j.num("top10"),
            avgHr = j.num("avgHr"),
            maxHr = j.num("maxHr"),
        )
    }
}

/** The whole session: everything recorded, plus time and distance on foil. */
data class Totals(val stats: Stats, val foilTime: Double, val foilDistance: Double, val onFoilShare: Double)

/** One run or flight: its number, the track points it covers and its numbers. */
data class Run(val number: Int, val points: IntRange, val startedAt: Long, val stats: Stats)

/** Tacks and jibes, and how many stayed on foil; only when yaft knows the wind. */
data class Turns(val jibes: Int, val jibesFoiled: Int, val tacks: Int, val tacksFoiled: Int, val cleanJibes: Int)

/** Where the wind came from, degrees, and whose it is: "user", "estimate" or "weather". */
data class Wind(val deg: Double, val source: String)

/** Downwind sessions: crashes and the longest flight. */
data class Downwind(val crashes: Int, val longestFlight: Double?, val avgFlight: Double?)

/**
 * A session on yaft as the website analyses it, from GET /api/app/sessions/<id>
 * (API version 2). The app only shows it; the analysis lives on the website.
 */
data class SessionView(
    val id: String,
    val category: Category,
    val startedAt: Long,
    val timeZone: String,
    val place: String?,
    val device: String?,
    val summary: String,
    val total: Totals,
    val runs: List<Run>,
    /** All turns, including ones the wind can't name. */
    val turnCount: Int,
    val turns: Turns?,
    val wind: Wind?,
    val downwind: Downwind?,
    val lonLat: List<DoubleArray>,
    val onFoil: List<Boolean>,
    val hasHeartRate: Boolean,
    val notes: String,
    val rating: Int?,
    /** The session's gear ids per slot column (board_id…); all null once it's split into parts. */
    val gear: Map<String, String?> = emptyMap(),
    /** Parts in time order, each with its own gear; empty when the session isn't split. */
    val parts: List<PartGear> = emptyList(),
    /** Per point on foil, for up-down and downwind sessions: 1 up, 2 reach, 3 down, 4 turn, 0 none. */
    val phase: List<Int>? = null,
    /** The wind direction the rider set (degrees it blows from); null when yaft estimates it. */
    val savedWind: Int? = null,
    /** The wind strength the rider logged, knots. */
    val windBase: Int? = null,
    val windGust: Int? = null,
) {
    /** Split into parts on the website: the gear is on the parts, so the app shows it read-only. */
    val split: Boolean get() = parts.isNotEmpty()

    companion object {
        /** The server API version that serves this view. */
        const val API_VERSION = 2

        /** Whether a server of [apiVersion] serves the view; older ones get the app's summary screen. */
        fun supported(apiVersion: Int) = apiVersion >= API_VERSION

        fun fromJson(response: JSONObject): SessionView {
            val s = response.getJSONObject("session")
            val a = s.getJSONObject("analysis")
            val track = s.getJSONObject("track")
            val segments = a.getJSONArray("segments").objects()
            val foil = segments.map { it.optString("kind") == "foil" }
            val coords = track.getJSONArray("coords")
            val pointSegment = track.getJSONArray("segment")
            val total = a.getJSONObject("total")
            val turns = a.optJSONObject("turns")
            val wind = a.optJSONObject("wind")
            val downwind = a.optJSONObject("downwind")
            return SessionView(
                id = s.getString("id"),
                category = Category.fromId(s.optString("category")),
                startedAt = OffsetDateTime.parse(s.getString("start")).toInstant().toEpochMilli(),
                timeZone = s.optString("timeZone", "UTC"),
                place = s.str("place"),
                device = s.str("device"),
                summary = s.optString("summary"),
                total = Totals(
                    Stats.fromJson(total),
                    foilTime = total.optDouble("foilTime", 0.0),
                    foilDistance = total.optDouble("foilDistance", 0.0),
                    onFoilShare = total.optDouble("onFoilShare", 0.0),
                ),
                runs = segments.filter { it.optString("kind") == "foil" }.map {
                    Run(it.optInt("number"), it.getInt("i0")..it.getInt("i1"), it.getLong("start"), Stats.fromJson(it.getJSONObject("stats")))
                },
                turnCount = a.optJSONArray("maneuvers")?.length() ?: 0,
                turns = turns?.let { Turns(it.optInt("jibes"), it.optInt("jibesFoiled"), it.optInt("tacks"), it.optInt("tacksFoiled"), it.optInt("cleanJibes")) },
                wind = wind?.let { Wind(it.getDouble("deg"), it.optString("source")) },
                downwind = downwind?.let { d ->
                    Downwind(d.optInt("crashes"), d.optJSONObject("longestFlight")?.optDouble("duration"), d.num("avgFlight"))
                },
                lonLat = (0 until coords.length()).map { i -> coords.getJSONArray(i).let { doubleArrayOf(it.getDouble(0), it.getDouble(1)) } },
                onFoil = (0 until pointSegment.length()).map { foil.getOrElse(pointSegment.getInt(it)) { false } },
                hasHeartRate = !track.isNull("hr"),
                notes = response.optString("notes"),
                rating = if (response.isNull("rating")) null else response.optInt("rating"),
                gear = gearIds(s.optJSONObject("details")?.optJSONObject("gear")),
                parts = s.optJSONObject("details")?.optJSONArray("parts")?.objects()?.map { PartGear(it.optLong("startsAt"), gearIds(it.optJSONObject("gear"))) }.orEmpty(),
                phase = track.optJSONArray("phase")?.let { p -> (0 until p.length()).map { p.optInt(it) } },
                savedWind = s.num("savedWind")?.toInt(),
                windBase = s.optJSONObject("details")?.num("windBase")?.toInt(),
                windGust = s.optJSONObject("details")?.num("windGust")?.toInt(),
            )
        }
    }
}

private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
private fun JSONObject.num(key: String): Double? = if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }
private fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
