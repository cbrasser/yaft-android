package site.yaft.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import site.yaft.app.track.Category

class RemoteSessionTest {
    // The shape GET /api/app/sessions sends, timestamps as Postgres writes them.
    private val json = """
        {"id":"eb96c975-844c-4bf2-a694-d417ac9329cc","category":"parawing-downwind","startedAt":"2026-10-06T09:05:10.409+00:00",
         "timeZone":"Europe/Zurich","place":null,"device":"Amazfit Bip 6","durationS":87.26,"distanceM":339.9,"foilTimeS":77.2,
         "top3Kmh":null,"summary":"77 s on foil.","preview":{"coords":[[9.51,40.91],[9.52,40.92]],"onFoil":[0,1]}}
    """.trimIndent()

    @Test
    fun readsTheListEndpointsShape() {
        val s = RemoteSession.fromJson(JSONObject(json))
        assertEquals(Category.PARAWING_DOWNWIND, s.category)
        assertEquals(1_791_277_510_409L, s.startedAt)
        assertEquals("2026-10-06T09:05:10.409+00:00", s.startedAtRaw)
        assertNull(s.place)
        assertNull(s.top3Kmh)
        assertEquals("Amazfit Bip 6", s.device)
        assertEquals(9.51, s.lonLat[0][0], 1e-9)
        assertEquals(40.92, s.lonLat[1][1], 1e-9)
        assertEquals(listOf(false, true), s.onFoil)
    }

    @Test
    fun readsWholeSecondTimestamps() {
        val s = RemoteSession.fromJson(JSONObject(json.replace("09:05:10.409+00:00", "13:34:29+00:00")))
        assertEquals(1_791_293_669_000L, s.startedAt)
    }
}
