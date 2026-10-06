package site.yaft.app.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveStatsTest {
    /** A straight line north at a steady speed, 1 Hz, starting at [t0]. */
    private fun run(t0: Long, seconds: Int, kmh: Double, lat0: Double = 46.0): List<TrackPoint> {
        val metresPerDegree = 111_195.0
        return (0..seconds).map { i ->
            TrackPoint(t0 + i * 1000L, lat0 + i * (kmh / 3.6) / metresPerDegree, 7.0, kmh / 3.6, 4f)
        }
    }

    @Test
    fun countsDistanceAndFoilTimeAboveThreshold() {
        val pts = run(0, 60, 18.0)
        val s = LiveStats.of(pts, Category.WINGFOIL)
        assertEquals(60.0, s.durationS, 0.01)
        assertEquals(300.0, s.distanceM, 1.0) // 18 km/h = 5 m/s
        assertEquals(60.0, s.foilTimeS, 0.01)
        assertEquals(18.0, s.top3Kmh, 0.01)
    }

    @Test
    fun slowRidingIsNotFoiling() {
        val s = LiveStats.of(run(0, 60, 8.0), Category.WINGFOIL)
        assertEquals(0.0, s.foilTimeS, 0.01)
    }

    @Test
    fun dockstartUsesItsLowerThreshold() {
        val pts = run(0, 30, 11.5)
        assertEquals(0.0, LiveStats.of(pts, Category.WINGFOIL).foilTimeS, 0.01)
        assertTrue(LiveStats.of(pts, Category.DOCKSTART).foilTimeS > 25)
    }

    @Test
    fun aSingleSpikeIsNotATopSpeed() {
        val pts = run(0, 20, 15.0).toMutableList()
        pts[10] = pts[10].copy(speedMs = 60 / 3.6)
        val s = LiveStats.of(pts, Category.WINGFOIL)
        assertTrue("top ${s.top3Kmh}", s.top3Kmh < 31)
    }

    @Test
    fun aLostFixDoesNotCountAsFoilTime() {
        val first = run(0, 10, 20.0)
        val second = run(40_000, 10, 20.0, first.last().lat + 0.001)
        val s = LiveStats.of(first + second, Category.WINGFOIL)
        assertEquals(20.0, s.foilTimeS, 0.01)
    }

    @Test
    fun derivesSpeedWhenTheReceiverHasNone() {
        val pts = run(0, 30, 18.0).map { it.copy(speedMs = null) }
        assertEquals(18.0, LiveStats.of(pts, Category.WINGFOIL).top3Kmh, 0.2)
    }

    @Test
    fun skipsPointsThatGoBackInTime() {
        val pts = run(0, 10, 18.0)
        val s = LiveStats.of(pts + pts[3], Category.WINGFOIL)
        assertEquals(11, s.points)
    }

    @Test
    fun pointLinesSurviveATornLastLine() {
        val p = TrackPoint(1_700_000_000_000, 46.1, 7.2, 5.5, 3f)
        assertEquals(p, TrackPoint.fromLine(p.toLine()))
        assertEquals(null, TrackPoint.fromLine("1700000000000,46.1"))
        assertEquals(p.copy(speedMs = null, accuracyM = null), TrackPoint.fromLine(p.copy(speedMs = null, accuracyM = null).toLine()))
    }
}
