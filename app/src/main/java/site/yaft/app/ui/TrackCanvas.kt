package site.yaft.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import site.yaft.app.track.Category
import site.yaft.app.track.LiveStats
import site.yaft.app.track.TrackPoint

/** A recorded track, with on-foil worked out the way the live numbers do it. */
@Composable
fun TrackCanvas(points: List<TrackPoint>, category: Category, modifier: Modifier = Modifier) {
    val lonLat = remember(points) { points.map { doubleArrayOf(it.lon, it.lat) } }
    val onFoil = remember(points, category) {
        val stats = LiveStats(category.foilThresholdKmh)
        points.map { stats.add(it); stats.onFoil }
    }
    TrackCanvas(lonLat, onFoil, modifier)
}

/**
 * A track drawn without a map (no tiles, so it works offline): on-foil
 * stretches in ink, the rest in grey, start as a dot. North is up.
 * [lonLat] holds [lon, lat] pairs, like the website's preview.
 */
@Composable
fun TrackCanvas(lonLat: List<DoubleArray>, onFoil: List<Boolean>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(Yaft.mapWater)
        if (lonLat.size < 2) return@Canvas
        val midLat = Math.toRadians((lonLat.minOf { it[1] } + lonLat.maxOf { it[1] }) / 2)
        val xs = lonLat.map { it[0] * cos(midLat) }
        val ys = lonLat.map { it[1] }
        val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
        val pad = 16.dp.toPx()
        val scale = minOf((size.width - 2 * pad) / (maxX - minX).coerceAtLeast(1e-9), (size.height - 2 * pad) / (maxY - minY).coerceAtLeast(1e-9))
        val ox = (size.width - (maxX - minX) * scale) / 2
        val oy = (size.height - (maxY - minY) * scale) / 2
        fun at(i: Int) = Offset((ox + (xs[i] - minX) * scale).toFloat(), (oy + (maxY - ys[i]) * scale).toFloat())
        fun foil(i: Int) = onFoil.getOrElse(i) { false }

        // Consecutive points with the same state form one path.
        var i = 0
        while (i < lonLat.size - 1) {
            val state = foil(i + 1)
            val path = Path().apply { moveTo(at(i).x, at(i).y) }
            var j = i + 1
            while (j < lonLat.size && foil(j) == state) {
                path.lineTo(at(j).x, at(j).y)
                j++
            }
            drawPath(
                path,
                color = if (state) Yaft.ink else Yaft.reach,
                style = Stroke(width = (if (state) 3.dp else 2.dp).toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            i = j - 1
        }
        drawCircle(Yaft.ink, radius = 6.dp.toPx(), center = at(0))
        drawCircle(Yaft.paper, radius = 3.dp.toPx(), center = at(0))
    }
}
