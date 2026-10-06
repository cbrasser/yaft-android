package site.yaft.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** A hard ink shadow behind a rounded shape, like --shadow on the website. */
fun Modifier.hardShadow(offset: Dp, radius: Dp) = drawBehind {
    drawRoundRect(Yaft.ink, topLeft = Offset(offset.toPx(), offset.toPx()), size = size, cornerRadius = CornerRadius(radius.toPx()))
}

/** A white leaf: 2px ink outline, 14px corners, hard shadow. Text sits on these, never on the hue. */
@Composable
fun Leaf(modifier: Modifier = Modifier, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .hardShadow(4.dp, 14.dp)
            .clip(shape)
            .background(Yaft.paper)
            .border(2.dp, Yaft.ink, shape)
            .padding(padding),
        content = content,
    )
}

@Composable
fun InkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = true,
    color: Color = if (filled) Yaft.ink else Yaft.paper,
) {
    val shape = RoundedCornerShape(10.dp)
    val fg = if (color == Yaft.ink || color == Yaft.danger) Yaft.paper else Yaft.ink
    Box(
        modifier
            .heightIn(min = 48.dp)
            .then(if (enabled) Modifier.hardShadow(2.dp, 10.dp) else Modifier)
            .clip(shape)
            .background(if (enabled) color else Yaft.groundWash)
            .border(2.dp, if (enabled) Yaft.ink else Yaft.reach, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) fg else Yaft.ink3, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

/**
 * Press and hold to confirm, so a wet thumb or a pouch can't end a session.
 * The fill grows while held; letting go early resets it.
 */
@Composable
fun HoldButton(text: String, onConfirmed: () -> Unit, modifier: Modifier = Modifier, holdMillis: Int = 1500) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .height(88.dp)
            .hardShadow(4.dp, 14.dp)
            .clip(shape)
            .background(Yaft.paper)
            .border(2.dp, Yaft.ink, shape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    val job = scope.launch {
                        progress.animateTo(1f, tween((holdMillis * (1 - progress.value)).toInt(), easing = LinearEasing))
                        onConfirmed()
                    }
                    waitForUpOrCancellation()
                    if (progress.value < 1f) {
                        job.cancel()
                        scope.launch { progress.animateTo(0f, tween(150)) }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(progress.value).background(Yaft.ink))
        Text(text, color = if (progress.value > 0.5f) Yaft.paper else Yaft.ink, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
    }
}

/** A number with its label underneath, tabular figures. */
@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, big: Boolean = false) {
    Column(modifier) {
        Text(value, style = Yaft.num.merge(Yaft.display), fontSize = if (big) 40.sp else 26.sp, color = Yaft.ink)
        Text(label, fontSize = 14.sp, color = Yaft.ink2, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun TopBar(title: String, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    androidx.compose.foundation.layout.Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Text("←", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp, top = 4.dp, bottom = 4.dp))
        }
        Text(title, style = Yaft.display, fontSize = 28.sp, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun BarLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Yaft.paper)
            .border(2.dp, Yaft.ink, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
