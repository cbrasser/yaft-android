package site.yaft.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.roundToInt
import site.yaft.app.data.SessionView

/** First index in [from]..[to] whose time is at or after [t] (times ascend), like the website's indexAt. */
fun indexAt(seconds: List<Int>, t: Double, from: Int = 0, to: Int = seconds.size - 1): Int {
    var lo = from
    var hi = to
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (seconds[mid] < t) lo = mid + 1 else hi = mid
    }
    return lo
}

/**
 * Speed (ink) and heart rate (grey) over time for [points] (the whole session
 * or a picked run), on-foil stretches in the hue's wash and the on-foil
 * threshold dashed, as on the website. Dragging along it (or tapping) picks
 * a point: [onCursor] gets its index, which the map shows as a dot, and the
 * line above reads its time, speed and heart rate.
 */
@Composable
fun ColumnScope.SessionChart(view: SessionView, points: IntRange, cursor: Int?, onCursor: (Int) -> Unit) {
    val seconds = view.seconds
    if (seconds.size < 2 || view.speed.size != seconds.size || points.last >= seconds.size || points.first >= points.last) return
    val i0 = points.first
    val i1 = points.last
    val hr = view.hr?.takeIf { it.size == seconds.size }
    val threshold = view.category.foilThresholdKmh
    val shape = remember(view, points) { chartShape(view, i0, i1, hr) }
    val cur = cursor?.takeIf { it in points }
    val pick by rememberUpdatedState(onCursor)
    val wash = Yaft.hueWash(view.category)

    Text("Speed", style = Yaft.display, fontSize = 22.sp)
    Readout(view, cur, hr)
    Text("${shape.top} km/h", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Yaft.ink2, style = Yaft.num, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
    val boxShape = RoundedCornerShape(8.dp)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(boxShape)
            .background(Yaft.paper)
            .border(2.dp, Yaft.ink, boxShape)
            .semantics {
                contentDescription = "Speed over time. Drag along it to read a moment and see it on the map."
                setProgress("Move along the track") { v ->
                    pick((i0 + (v * (i1 - i0)).roundToInt()).coerceIn(i0, i1))
                    true
                }
            }
            .pointerInput(view, points) {
                fun at(x: Float) = pick(indexAt(seconds, seconds[i0] + (x / size.width).coerceIn(0f, 1f).toDouble() * (seconds[i1] - seconds[i0]), i0, i1))
                detectTapGestures { at(it.x) }
            }
            .pointerInput(view, points) {
                fun at(x: Float) = pick(indexAt(seconds, seconds[i0] + (x / size.width).coerceIn(0f, 1f).toDouble() * (seconds[i1] - seconds[i0]), i0, i1))
                detectHorizontalDragGestures(onDragStart = { at(it.x) }) { change, _ ->
                    change.consume()
                    at(change.position.x)
                }
            },
    ) {
        val w = size.width
        val h = size.height
        val span = (seconds[i1] - seconds[i0]).coerceAtLeast(1).toFloat()
        fun x(i: Int) = (seconds[i] - seconds[i0]) / span * w
        fun y(v: Double) = (h - v / shape.top * (h - 4.dp.toPx())).toFloat()

        for ((a, b) in shape.foil) drawRect(wash, Offset(x(a), 0f), Size((x(b) - x(a)).coerceAtLeast(1f), h))
        drawLine(Yaft.ink3, Offset(0f, y(threshold)), Offset(w, y(threshold)), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
        shape.hrRange?.let { (lo, hi) ->
            val hy = { v: Int -> h - 6.dp.toPx() - (v - lo).toFloat() / (hi - lo) * (h - 16.dp.toPx()) }
            val path = Path()
            var pen = false
            for (i in i0..i1) {
                val v = hr!![i]
                if (v <= 0) { pen = false; continue }
                if (pen) path.lineTo(x(i), hy(v)) else path.moveTo(x(i), hy(v))
                pen = true
            }
            drawPath(path, Yaft.reach, style = Stroke(1.5.dp.toPx(), join = StrokeJoin.Round))
        }
        val speed = Path().apply {
            moveTo(x(i0), y(view.speed[i0]))
            for (i in i0 + 1..i1) lineTo(x(i), y(view.speed[i]))
        }
        drawPath(speed, Yaft.ink, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
        cur?.let { drawLine(Yaft.ink, Offset(x(it), 0f), Offset(x(it), h), strokeWidth = 2.dp.toPx()) }
    }
    Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        AxisText(timeOfDay(view.startedAt + seconds[i0] * 1000L, view.timeZone))
        Box(Modifier.weight(1f))
        AxisText(timeOfDay(view.startedAt + seconds[i1] * 1000L, view.timeZone))
    }
    ChartKey(view, shape, wash, threshold)
}

/** What the chart draws, worked out once per stretch: the speed axis's top, the on-foil spans and the heart-rate range. */
private class ChartShape(val top: Int, val foil: List<Pair<Int, Int>>, val hrRange: Pair<Int, Int>?)

private fun chartShape(view: SessionView, i0: Int, i1: Int, hr: List<Int>?): ChartShape {
    val max = maxOf(view.category.foilThresholdKmh, (i0..i1).maxOf { view.speed[it] })
    val beats = hr?.let { (i0..i1).map { i -> it[i] }.filter { it > 0 } }.orEmpty()
    val hrRange = if (beats.isNotEmpty() && beats.max() > beats.min()) beats.min() to beats.max() else null
    val foil = view.runs.filter { it.points.last >= i0 && it.points.first <= i1 }.map { maxOf(it.points.first, i0) to minOf(it.points.last, i1) }
    return ChartShape((ceil(max / 5) * 5).toInt(), foil, hrRange)
}

/** The picked point: time, speed, heart rate and whether it's on foil; a hint before the first pick. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Readout(view: SessionView, i: Int?, hr: List<Int>?) {
    if (i == null) {
        Text("Drag along the chart to read a moment and see it on the map.", color = Yaft.ink2, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        return
    }
    FlowRow(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.Bottom) {
        Text(timeOfDaySeconds(view.startedAt + view.seconds[i] * 1000L, view.timeZone), color = Yaft.ink2, fontWeight = FontWeight.SemiBold, style = Yaft.num, modifier = Modifier.align(Alignment.Bottom))
        Value(kmh(view.speed[i]), "km/h")
        hr?.getOrNull(i)?.takeIf { it > 0 }?.let { Value("$it", "bpm") }
        Text(if (view.onFoil.getOrElse(i) { false }) "On foil" else "Off foil", color = Yaft.ink2, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Bottom))
    }
}

@Composable
private fun Value(value: String, unit: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, style = Yaft.num)
        Text(" $unit", color = Yaft.ink2, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 2.dp))
    }
}

@Composable
private fun AxisText(text: String) = Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Yaft.ink2, style = Yaft.num)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChartKey(view: SessionView, shape: ChartShape, wash: Color, threshold: Double) {
    FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        KeyItem("Speed") { Swatch { drawLine(Yaft.ink, Offset(0f, center.y), Offset(size.width, center.y), 2.dp.toPx()) } }
        shape.hrRange?.let { (lo, hi) -> KeyItem("Heart rate, $lo–$hi bpm") { Swatch { drawLine(Yaft.reach, Offset(0f, center.y), Offset(size.width, center.y), 2.dp.toPx()) } } }
        KeyItem("On foil") {
            Box(Modifier.size(22.dp, 12.dp).clip(RoundedCornerShape(3.dp)).background(wash).border(1.5.dp, Yaft.ink, RoundedCornerShape(3.dp)))
        }
        KeyItem("${threshold.roundToInt()} km/h, the on-foil line") {
            Swatch {
                drawLine(Yaft.ink3, Offset(0f, center.y), Offset(size.width, center.y), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
            }
        }
    }
}

@Composable
private fun KeyItem(text: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        swatch()
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, style = Yaft.num)
    }
}

@Composable
private fun Swatch(draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) = Canvas(Modifier.size(22.dp, 10.dp), onDraw = draw)
