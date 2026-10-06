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

/**
 * The track drawn without a map (no tiles, so it works offline): on-foil
 * stretches in ink, the rest in grey, start as a dot. North is up.
 */
@Composable
fun TrackCanvas(points: List<TrackPoint>, category: Category, modifier: Modifier = Modifier) {
    val onFoil = remember(points, category) {
        val stats = LiveStats(category.foilThresholdKmh)
        points.map { stats.add(it); stats.onFoil }
    }
    Canvas(modifier) {
        drawRect(Yaft.mapWater)
        if (points.size < 2) return@Canvas
        val midLat = Math.toRadians((points.minOf { it.lat } + points.maxOf { it.lat }) / 2)
        val xs = points.map { it.lon * cos(midLat) }
        val ys = points.map { it.lat }
        val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
        val pad = 16.dp.toPx()
        val scale = minOf((size.width - 2 * pad) / (maxX - minX).coerceAtLeast(1e-9), (size.height - 2 * pad) / (maxY - minY).coerceAtLeast(1e-9))
        val ox = (size.width - (maxX - minX) * scale) / 2
        val oy = (size.height - (maxY - minY) * scale) / 2
        fun at(i: Int) = Offset((ox + (xs[i] - minX) * scale).toFloat(), (oy + (maxY - ys[i]) * scale).toFloat())

        // Consecutive points with the same state form one path.
        var i = 0
        while (i < points.size - 1) {
            val foil = onFoil[i + 1]
            val path = Path().apply { moveTo(at(i).x, at(i).y) }
            var j = i + 1
            while (j < points.size && onFoil[j] == foil) {
                path.lineTo(at(j).x, at(j).y)
                j++
            }
            drawPath(
                path,
                color = if (foil) Yaft.ink else Yaft.reach,
                style = Stroke(width = (if (foil) 3.dp else 2.dp).toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            i = j - 1
        }
        drawCircle(Yaft.ink, radius = 6.dp.toPx(), center = at(0))
        drawCircle(Yaft.paper, radius = 3.dp.toPx(), center = at(0))
    }
}
