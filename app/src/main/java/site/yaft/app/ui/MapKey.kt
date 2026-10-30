package site.yaft.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import site.yaft.app.data.ColorMode
import site.yaft.app.data.SessionView

/** Whether the on-foil colours are up/reach/down phases, which the switch then calls Direction, as the website does. */
private fun SessionView.direction() = phase?.any { it != 0 } == true

/**
 * What the track is coloured by: On foil (or Direction), Speed, and Heart rate
 * when the session has it on foil. Hidden when the server offers on foil only.
 */
@Composable
fun ColorSwitch(view: SessionView, colorBy: ColorMode, onColorBy: (ColorMode) -> Unit, modifier: Modifier = Modifier) {
    val modes = view.colors.modes
    if (modes.size < 2) return
    val shape = RoundedCornerShape(10.dp)
    Row(modifier.fillMaxWidth().clip(shape).border(2.dp, Yaft.ink, shape).selectableGroup()) {
        modes.forEachIndexed { k, mode ->
            val on = mode == colorBy
            if (k > 0) Box(Modifier.width(2.dp).height(48.dp).background(Yaft.ink))
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .background(if (on) Yaft.ink else Yaft.paper)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onColorBy(mode) })
                    .padding(horizontal = 6.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(view, mode), color = if (on) Yaft.paper else Yaft.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
            }
        }
    }
}

private fun label(view: SessionView, mode: ColorMode) = if (mode == ColorMode.FOIL && view.direction()) "Direction" else mode.label

/** The map's key: the ramp's range for speed or heart rate, else the on-foil colours; off foil is dashed in every mode. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapKey(view: SessionView, colorBy: ColorMode, modifier: Modifier = Modifier) {
    val ramp = view.colors.ramp(colorBy)
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            ramp != null -> {
                val low = ramp.low.roundToInt()
                val high = ramp.high.roundToInt()
                Row(
                    Modifier.clearAndSetSemantics { contentDescription = "${label(view, colorBy)} on foil, $low to $high ${ramp.unit}" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    KeyText("$low")
                    Box(Modifier.width(72.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Brush.horizontalGradient(Yaft.ramp)).border(1.dp, Yaft.ink, RoundedCornerShape(5.dp)))
                    KeyText("$high ${ramp.unit}")
                }
            }
            view.direction() -> {
                KeyItem("Upwind") { KeyLine(Yaft.ink) }
                KeyItem("Reach") { KeyLine(Yaft.reach) }
                KeyItem("Downwind") { KeyLine(Yaft.hue(view.category)) }
                KeyItem("Transition") { KeyLine(Yaft.paper) }
            }
            else -> KeyItem("On foil") { KeyLine(Yaft.hue(view.category)) }
        }
        KeyItem("Off foil") {
            Canvas(Modifier.size(22.dp, 10.dp)) {
                val y = size.height / 2
                drawLine(
                    Yaft.ink.copy(alpha = 0.55f), Offset(0f, y), Offset(size.width, y),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())),
                )
            }
        }
    }
}

/** A key entry: its swatch, then what it means. The map's key and the chart's share it. */
@Composable
fun KeyItem(text: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        swatch()
        KeyText(text)
    }
}

@Composable
private fun KeyText(text: String) = Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, style = Yaft.num)

/** An on-foil line as the map draws it: an ink casing round the colour. */
@Composable
private fun KeyLine(color: Color) {
    Canvas(Modifier.size(22.dp, 10.dp)) {
        val y = size.height / 2
        drawLine(Yaft.ink, Offset(2.dp.toPx(), y), Offset(size.width - 2.dp.toPx(), y), strokeWidth = 7.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color, Offset(2.dp.toPx(), y), Offset(size.width - 2.dp.toPx(), y), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
    }
}
