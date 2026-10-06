package site.yaft.app.track

import java.io.File
import java.io.StringWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxTest {
    private val points = listOf(
        TrackPoint(1_759_400_000_000, 40.9123456, 9.5123456, 5.123, 4f),
        TrackPoint(1_759_400_001_000, 40.9124, 9.5124, null, 4f),
        TrackPoint(1_759_400_002_000, 40.9125, 9.5125, 6.0, 4f),
    )

    @Test
    fun writesTheFormatTheWebsiteReads() {
        val xml = StringWriter().also { Gpx.write(it, points, "Wingfoil") }.toString()
        assertTrue(xml.contains("xmlns:gpxtpx=\"http://www.garmin.com/xmlschemas/TrackPointExtension/v1\""))
        assertTrue(xml.contains("<trkpt lat=\"40.9123456\" lon=\"9.5123456\"><time>2025-10-02T10:13:20Z</time>"))
        assertTrue(xml.contains("<gpxtpx:speed>5.123</gpxtpx:speed>"))
        // Speed only where the receiver had one.
        assertEquals(2, Regex("<gpxtpx:speed>").findAll(xml).count())
    }

    @Test
    fun readsBackWhatItWrote() {
        val f = File.createTempFile("yaft", ".gpx").apply { deleteOnExit() }
        f.writer().use { Gpx.write(it, points, "A & B") }
        val back = Gpx.read(f)
        assertEquals(points.map { it.copy(accuracyM = null) }, back)
    }
}
