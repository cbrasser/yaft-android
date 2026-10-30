package site.yaft.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorKeyTest {
    private fun recorded(name: String) = JSONObject(javaClass.getResource("/$name")!!.readText())

    // The dockstart session's watch logged heart rate on foil: the key has a speed range from the
    // dockstart threshold to the best 3 s, and the on-foil heart rate's 5th to 95th percentile.
    @Test
    fun readsTheRangesAndHeartRateOfAWatchSession() {
        val key = SessionView.fromJson(recorded("session-view-dockstart.json")).colors
        assertTrue(key.heartRate)
        assertEquals(listOf(ColorMode.FOIL, ColorMode.SPEED, ColorMode.HR), key.modes)
        val speed = key.ramp(ColorMode.SPEED)!!
        assertEquals(11.0, speed.low, 1e-9)
        assertEquals(18.66, speed.high, 1e-9)
        assertEquals("km/h", speed.unit)
        val hr = key.ramp(ColorMode.HR)!!
        assertEquals(104.0, hr.low, 1e-9)
        assertEquals(113.0, hr.high, 1e-9)
        assertEquals("bpm", hr.unit)
    }

    // A phone recording has no heart rate, so the switch offers On foil and Speed only.
    @Test
    fun aPhoneRecordingHasNoHeartRate() {
        val key = SessionView.fromJson(recorded("session-view-phone.json")).colors
        assertFalse(key.heartRate)
        assertEquals(listOf(ColorMode.FOIL, ColorMode.SPEED), key.modes)
        assertEquals(12.0, key.ramp(ColorMode.SPEED)!!.low, 1e-9)
        assertNull(key.ramp(ColorMode.HR))
    }

    // A server from before the colour key answers without it: the map keeps its on-foil colours.
    @Test
    fun anOlderServerOffersOnFoilOnly() {
        val response = recorded("session-view-dockstart.json").apply { remove("colors") }
        val key = SessionView.fromJson(response).colors
        assertFalse(key.heartRate)
        assertEquals(listOf(ColorMode.FOIL), key.modes)
    }

    // A newer server may add modes; the app skips the ones it doesn't know, and a heart-rate
    // range counts only when the server says heart rate is available.
    @Test
    fun skipsModesItDoesNotKnow() {
        val response = recorded("session-view-dockstart.json")
        response.getJSONObject("colors").getJSONArray("ranges").put(JSONObject("""{"mode":"power","range":[100,300],"unit":"W"}"""))
        assertEquals(listOf(ColorMode.FOIL, ColorMode.SPEED, ColorMode.HR), SessionView.fromJson(response).colors.modes)

        response.getJSONObject("colors").put("heartRate", false)
        assertEquals(listOf(ColorMode.FOIL, ColorMode.SPEED), SessionView.fromJson(response).colors.modes)
    }

    // The chart reads time, speed and heart rate per point.
    @Test
    fun readsTimeSpeedAndHeartRatePerPoint() {
        val watch = SessionView.fromJson(recorded("session-view-dockstart.json"))
        assertEquals(219, watch.seconds.size)
        assertEquals(219, watch.speed.size)
        assertEquals(219, watch.hr!!.size)
        assertEquals(0, watch.seconds[0])
        assertTrue(watch.seconds.zipWithNext().all { (a, b) -> b >= a })

        val phone = SessionView.fromJson(recorded("session-view-phone.json"))
        assertEquals(85, phone.speed.size)
        assertNull(phone.hr)
    }
}
