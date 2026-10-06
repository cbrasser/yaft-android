package site.yaft.app.track

import java.io.File
import java.io.Writer
import java.time.Instant
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * GPX 1.1 with Garmin TrackPointExtension speed (m/s), the format yaft.site
 * reads and stores (writeGpx in lib/gpx.ts). Speed is written only where the
 * receiver had its own.
 */
object Gpx {
    const val CREATOR = "yaft for Android"
    private const val TPX = "http://www.garmin.com/xmlschemas/TrackPointExtension/v1"

    fun write(out: Writer, points: List<TrackPoint>, name: String?) {
        out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        out.write("<gpx version=\"1.1\" creator=\"$CREATOR\" xmlns=\"http://www.topografix.com/GPX/1/1\" xmlns:gpxtpx=\"$TPX\">\n")
        out.write("<trk>")
        if (name != null) out.write("<name>${escape(name)}</name>")
        out.write("<trkseg>\n")
        for (p in points) {
            out.write(String.format(Locale.ROOT, "<trkpt lat=\"%.7f\" lon=\"%.7f\"><time>%s</time>", p.lat, p.lon, Instant.ofEpochMilli(p.t)))
            if (p.speedMs != null) {
                out.write(String.format(Locale.ROOT, "<extensions><gpxtpx:TrackPointExtension><gpxtpx:speed>%.3f</gpxtpx:speed></gpxtpx:TrackPointExtension></extensions>", p.speedMs))
            }
            out.write("</trkpt>\n")
        }
        out.write("</trkseg></trk>\n</gpx>\n")
    }

    /** Reads a GPX this app wrote (or any GPX 1.1 track) back into points. */
    fun read(file: File): List<TrackPoint> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = file.inputStream().use { factory.newDocumentBuilder().parse(it) }
        val nodes = doc.getElementsByTagNameNS("*", "trkpt")
        return (0 until nodes.length).mapNotNull { i ->
            val el = nodes.item(i) as Element
            val time = el.getElementsByTagNameNS("*", "time").item(0)?.textContent ?: return@mapNotNull null
            TrackPoint(
                t = runCatching { Instant.parse(time.trim()).toEpochMilli() }.getOrNull() ?: return@mapNotNull null,
                lat = el.getAttribute("lat").toDoubleOrNull() ?: return@mapNotNull null,
                lon = el.getAttribute("lon").toDoubleOrNull() ?: return@mapNotNull null,
                speedMs = el.getElementsByTagNameNS(TPX, "speed").item(0)?.textContent?.trim()?.toDoubleOrNull(),
                accuracyM = null,
            )
        }
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
