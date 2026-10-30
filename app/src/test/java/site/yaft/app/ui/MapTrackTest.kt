package site.yaft.app.ui

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import site.yaft.app.data.ColorMode
import site.yaft.app.data.SessionView

class MapTrackTest {
    private fun view(name: String) = SessionView.fromJson(JSONObject(javaClass.getResource("/$name")!!.readText()))

    private fun features(geoJson: String) = JSONObject(geoJson).getJSONArray("features").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
    private fun JSONObject.coordCount() = getJSONObject("geometry").getJSONArray("coordinates").length()

    // One run (points 61 to 120) between two stretches on shore. As on the website, an off-foil
    // stretch runs on to the first point of what follows, so the line has no gap where a run starts.
    @Test
    fun drawsEachRunOnFoilAndTheRestOffFoil() {
        val t = mapTrack(view("session-view-dockstart.json"))
        val foil = features(t.foil)
        assertEquals(1, foil.size)
        assertEquals(1, foil[0].getJSONObject("properties").getInt("run"))
        assertEquals(60, foil[0].coordCount())

        val off = features(t.off)
        assertEquals(listOf(62, 98), off.map { it.coordCount() })
        assertEquals("LineString", off[0].getJSONObject("geometry").getString("type"))
    }

    // Up-down and downwind sessions colour the track by phase (1 up, 2 reach, 3 down, 4 turn, 0 none); a run is cut
    // where its phase changes, each piece ending on the next one's first point. No recording has phases, so this
    // gives the phone recording's first flight (points 0 to 28) ten points up, then down.
    @Test
    fun cutsRunsWherePhaseChanges() {
        val response = JSONObject(javaClass.getResource("/session-view-phone.json")!!.readText())
        val track = response.getJSONObject("session").getJSONObject("track")
        track.put("phase", org.json.JSONArray(List(track.getJSONArray("coords").length()) { if (it < 10) 1 else if (it <= 28) 3 else 0 }))
        val foil = features(mapTrack(SessionView.fromJson(response)).foil)
        assertEquals(listOf(1 to 1, 1 to 3, 2 to 0), foil.map { it.getJSONObject("properties").let { p -> p.getInt("run") to p.getInt("phase") } })
        assertEquals(listOf(11, 19, 46), foil.map { it.coordCount() })
    }

    // The map opens on the whole track (the same box the server sends as track.bounds) and frames a run when one is picked.
    @Test
    fun framesTheTrackOrOneRun() {
        assertEquals(Bounds(0.0, 0.0, 0.9125, 1.0), mapTrack(view("session-view-dockstart.json")).bounds)

        val phone = view("session-view-phone.json")
        assertEquals(Bounds(9.509998, 40.911238, 9.511365, 40.91227), boundsOf(phone.lonLat, phone.runs[1].points))
    }

    // Speed and heart rate colour the on-foil track in short pieces, each carrying the mean of its points
    // (property v), as the website's map does. Off foil stays in the dashed line, so the pieces cover the run only.
    @Test
    fun colorsTheRunByItsSpeed() {
        val view = view("session-view-dockstart.json")
        val pieces = features(rampTrack(view, ColorMode.SPEED))
        // Each piece ends on the next one's first point, so together they run from point 61 to 120 without a gap.
        assertEquals(120 - 61, pieces.sumOf { it.coordCount() - 1 })
        assertEquals(true, pieces.all { it.coordCount() in 2..9 })
        val first = pieces.first().getJSONObject("geometry").getJSONArray("coordinates").getJSONArray(0)
        assertEquals(view.lonLat[61][0], first.getDouble(0), 1e-12)
        // Points 61 and 62 run at 15.8 and 16.1 km/h.
        assertEquals(15.95, pieces[0].getJSONObject("properties").getDouble("v"), 1e-9)
        assertEquals(1, pieces[0].getJSONObject("properties").getInt("run"))
        assertEquals(2, pieces[0].coordCount())
    }

    @Test
    fun colorsByHeartRateOnlyWhereThereIsSome() {
        val pieces = features(rampTrack(view("session-view-dockstart.json"), ColorMode.HR))
        // Points 61 and 62: 106 and 107 bpm.
        assertEquals(106.5, pieces[0].getJSONObject("properties").getDouble("v"), 1e-9)
        assertEquals(0, features(rampTrack(view("session-view-phone.json"), ColorMode.HR)).size)
    }
}
