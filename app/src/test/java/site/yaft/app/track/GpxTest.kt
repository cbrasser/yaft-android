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

    @Test
    fun readsAWatchExport() {
        // The shape of the Amazfit Bip 6's export (Zepp): its own prefix for Garmin's extension, heart rate next to speed.
        val f = File.createTempFile("zepp", ".gpx").apply { deleteOnExit() }
        f.writeText(
            """
            <?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
            <gpx xmlns="http://www.topografix.com/GPX/1/1" xmlns:ns3="http://www.garmin.com/xmlschemas/TrackPointExtension/v1" creator="Amazfit Bip 6" version="1.1">
            <trk><name><![CDATA[20261006 Outdoor running]]></name><trkseg>
            <trkpt lat="46.96608566" lon="7.38494466"><time>2026-10-06T11:57:34Z</time>
            <extensions><ns3:TrackPointExtension><ns3:speed>2.5</ns3:speed><ns3:cad>0.0</ns3:cad><ns3:hr>108</ns3:hr></ns3:TrackPointExtension></extensions></trkpt>
            <trkpt lat="46.96608799" lon="7.38494932"><time>2026-10-06T11:57:35Z</time></trkpt>
            </trkseg></trk></gpx>
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                TrackPoint(1_791_287_854_000, 46.96608566, 7.38494466, 2.5, null),
                TrackPoint(1_791_287_855_000, 46.96608799, 7.38494932, null, null),
            ),
            Gpx.read(f),
        )
        assertEquals("Amazfit Bip 6", Gpx.creator(f))
    }

    @Test
    fun aFileWithoutCreatorHasNone() {
        val f = File.createTempFile("bare", ".gpx").apply { deleteOnExit() }
        f.writeText("<gpx version=\"1.1\"><trk/></gpx>")
        assertEquals(null, Gpx.creator(f))
    }
}
