package site.yaft.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import site.yaft.app.net.Account
import site.yaft.app.track.Category

class SessionViewTest {
    private fun recorded(name: String) = JSONObject(javaClass.getResource("/$name")!!.readText())

    // GET /api/app/sessions/<id> for an Amazfit dockstart session, uploaded to a local yaft. Its place is taken out
    // as yaft's session links do it: coordinates scaled to 0..1 (the website's trackShape), no spot name.
    @Test
    fun readsASessionWithHeartRate() {
        val v = SessionView.fromJson(recorded("session-view-dockstart.json"))
        assertEquals(Category.DOCKSTART, v.category)
        assertEquals(1_791_207_269_000L, v.startedAt)
        assertEquals("One run of 61 s on foil, 262 m at up to 18.7 km/h.", v.summary)
        assertEquals("Amazfit Bip 6", v.device)
        assertEquals(61.0, v.total.foilTime, 1e-9)
        assertEquals(332.3, v.total.stats.distance, 0.05)
        assertEquals(18.66, v.total.stats.top3!!, 1e-9)
        assertEquals(139.0, v.total.stats.maxHr!!, 1e-9)
        assertEquals(true, v.hasHeartRate)

        assertEquals(1, v.runs.size)
        val run = v.runs[0]
        assertEquals(1, run.number)
        assertEquals(61..120, run.points)
        assertEquals(61.0, run.stats.duration, 1e-9)

        assertEquals(219, v.lonLat.size)
        assertEquals(false, v.onFoil[60])
        assertEquals(true, v.onFoil[61])
        assertEquals(true, v.onFoil[120])
        assertEquals(false, v.onFoil[121])
        assertNull(v.wind)
        assertEquals("", v.notes)
        assertNull(v.rating)
    }

    // The same endpoint for a recording made in this app and uploaded: no heart rate, two flights.
    @Test
    fun readsAPhoneRecordingWithoutHeartRate() {
        val v = SessionView.fromJson(recorded("session-view-phone.json"))
        assertEquals(Category.WINGFOIL, v.category)
        assertEquals(1_791_277_510_409L, v.startedAt)
        assertEquals("yaft for Android", v.device)
        assertEquals(false, v.hasHeartRate)
        assertNull(v.total.stats.avgHr)
        assertNull(v.total.stats.maxHr)
        assertEquals(77.234, v.total.foilTime, 1e-9)
        assertEquals(listOf(1, 2), v.runs.map { it.number })
        assertEquals(39..84, v.runs[1].points)
        assertEquals(85, v.onFoil.size)
        assertEquals(false, v.onFoil[30])
        assertEquals(true, v.onFoil[39])
        assertNull(v.wind)
        assertNull(v.turns)
        assertEquals(0, v.turnCount)
    }

    // GET /api/app/config: servers before the session view report 1 and keep today's screen.
    @Test
    fun fallsBackOnApiVersion1() {
        val config = """{"supabaseUrl":"http://127.0.0.1:55421","supabaseKey":"sb_publishable_x","apiVersion":%d}"""
        assertFalse(SessionView.supported(Account.apiVersionOf(JSONObject(config.format(1)))))
        assertTrue(SessionView.supported(Account.apiVersionOf(JSONObject(config.format(2)))))
        assertTrue(SessionView.supported(Account.apiVersionOf(JSONObject(config.format(3)))))
        assertFalse(SessionView.supported(Account.apiVersionOf(JSONObject("""{"supabaseUrl":"x","supabaseKey":"y"}"""))))
    }
}
