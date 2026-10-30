package site.yaft.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import site.yaft.app.data.ColorMode
import site.yaft.app.data.SessionView
import site.yaft.app.data.ViewLoad
import site.yaft.app.data.loadSessionView
import site.yaft.app.net.Account

/** A session's view as it loads (null while loading), how to load it again, and how to show the view an edit came back with. */
data class SessionViewState(val load: ViewLoad?, val retry: () -> Unit, val update: (SessionView) -> Unit)

/** Loads session [id]'s view from yaft while [enabled]. */
@Composable
fun rememberSessionView(account: Account, id: String, enabled: Boolean = true): SessionViewState {
    var attempt by rememberSaveable(id) { mutableIntStateOf(0) }
    var edited by remember(id) { mutableStateOf<SessionView?>(null) }
    val load by produceState<ViewLoad?>(null, id, enabled, attempt) {
        value = null
        edited = null
        if (enabled) value = loadSessionView(account, id)
    }
    return SessionViewState(edited?.let { ViewLoad.Ready(it) } ?: load, { attempt++ }, { edited = it })
}

/**
 * A session as the website analyses it: the track with its colour switch and
 * key, the speed and heart-rate chart, the numbers sheet and the runs or
 * flights, then the edit forms. Tapping a run highlights it on the track,
 * narrows the chart to it and scrolls [scroll] back up to it. Dragging along
 * the chart moves a dot on the map. An edit hands the new view to [onEdited].
 */
@Composable
fun ColumnScope.FullSession(view: SessionView, scroll: ScrollState, account: Account, onEdited: (SessionView) -> Unit) {
    val scope = rememberCoroutineScope()
    var selected by rememberSaveable(view.id) { mutableStateOf<Int?>(null) }
    val run = view.runs.firstOrNull { it.number == selected }
    var picked by rememberSaveable(view.id) { mutableStateOf(ColorMode.FOIL) }
    // An edit can bring a view without the picked mode (a new server, or none): fall back to on foil.
    val colorBy = picked.takeIf { it in view.colors.modes } ?: ColorMode.FOIL
    // The chart's point; picking another run starts afresh, so the dot never sits outside the chart.
    var cursor by remember(view, selected) { mutableStateOf<Int?>(null) }
    Leaf(Modifier.fillMaxWidth(), padding = 0.dp) {
        TrackMap(view, run, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)), colorBy, cursor)
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ColorSwitch(view, colorBy, { picked = it })
            MapKey(view, colorBy)
        }
    }
    Leaf(Modifier.fillMaxWidth()) {
        SessionChart(view, run?.points ?: view.lonLat.indices, cursor) { cursor = it }
    }
    Leaf(Modifier.fillMaxWidth()) {
        Text(dateTime(view.startedAt, view.timeZone) + (view.place?.let { " · $it" } ?: ""), fontWeight = FontWeight.Bold, style = Yaft.num)
        Spacer(Modifier.height(8.dp))
        Text(view.summary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        view.device?.let { Text("Recorded with $it", color = Yaft.ink2, modifier = Modifier.padding(top = 8.dp)) }
    }
    Numbers(view)
    if (view.runs.isNotEmpty()) Runs(view, selected) {
        selected = if (selected == it) null else it
        if (selected != null) scope.launch { scroll.animateScrollTo(0) }
    }
    EditForms(view, account, onEdited)
}

@Composable
private fun Numbers(view: SessionView) {
    val t = view.total
    val c = view.category
    Leaf(Modifier.fillMaxWidth()) {
        SheetTitle("Whole session")
        Group("Speed") {
            Line("Average", speed(t.stats.avgSpeed), "on foil")
            Line("Top speed", speed(t.stats.top3), "best 3 s")
            Line("Top speed", speed(t.stats.top10), "best 10 s")
        }
        Group("Distance and time") {
            Line("Distance", distance(t.stats.distance), "everything recorded")
            Line("Distance on foil", distance(t.foilDistance))
            Line("Time on foil", duration(t.foilTime), pct(t.onFoilShare))
            Line("Total time", duration(t.stats.duration))
        }
        if (view.hasHeartRate) Group("Heart rate") {
            Line("Average", bpm(t.stats.avgHr))
            Line("Max", bpm(t.stats.maxHr))
        }
        if (c.usesWind || view.wind != null) Group("Wind") {
            val w = view.wind
            Line("From", w?.let { "${compassPoint(it.deg)} ${it.deg.roundToInt()}°" } ?: "–", when (w?.source) {
                "user" -> "set by you"
                "weather" -> "weather data"
                "estimate" -> "estimated"
                else -> "couldn't estimate it"
            })
        }
        if (c.showsManeuvers) Group("Maneuvers") {
            val turns = view.turns
            if (turns == null) Line("Turns", view.turnCount.toString(), "set the wind on yaft to split tacks and jibes")
            else {
                Line("Jibes", turns.jibes.toString(), if (turns.jibes > 0) "${turns.jibesFoiled} foiled through" else null)
                if (turns.jibesFoiled > 0) Line("Clean jibes", pct(turns.cleanJibes.toDouble() / turns.jibesFoiled), "lost under 2 km/h")
                Line("Tacks", turns.tacks.toString(), if (turns.tacks > 0) "${turns.tacksFoiled} foiled through" else null)
            }
        }
        view.downwind?.let { d ->
            Group("Flights") {
                Line("Flights", view.runs.size.toString())
                Line("Crashes", d.crashes.toString())
                Line("Longest flight", d.longestFlight?.let(::duration) ?: "–")
                Line("Average flight", d.avgFlight?.let(::duration) ?: "–")
            }
        }
    }
}

/** The runs or flights; tapping one highlights it on the track, tapping it again shows the whole session. */
@Composable
private fun Runs(view: SessionView, selected: Int?, onSelect: (Int) -> Unit) {
    val noun = view.category.segmentNoun
    Leaf(Modifier.fillMaxWidth()) {
        SheetTitle("${noun}s")
        Text("Tap one to see it on the track.", color = Yaft.ink2, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
        view.runs.forEach { run ->
            val on = run.number == selected
            val shape = RoundedCornerShape(10.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(shape)
                    .background(if (on) Yaft.groundWash else Yaft.paper)
                    .border(2.dp, if (on) Yaft.ink else Yaft.groundWash, shape)
                    .clickable(role = Role.Button, onClickLabel = if (on) "Show the whole session" else "Show on the track") { onSelect(run.number) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("$noun ${run.number}", fontWeight = FontWeight.Bold, style = Yaft.num)
                    Text(timeOfDay(run.startedAt, view.timeZone), color = Yaft.ink2, fontSize = 14.sp, style = Yaft.num)
                }
                Number(duration(run.stats.duration), "time")
                Number(distance(run.stats.distance), "distance")
                Number(speed(run.stats.avgSpeed), "km/h avg")
            }
        }
    }
}

/** Shown instead of the view while it loads or when it can't be shown. */
@Composable
fun ViewProblem(load: ViewLoad?, onRetry: () -> Unit, onSignIn: () -> Unit) {
    Leaf(Modifier.fillMaxWidth()) {
        when (load) {
            null -> Text("Loading the analysis from yaft…", color = Yaft.ink2)
            ViewLoad.SignedOut -> {
                Text("Sign in again to see this session's analysis.")
                InkButton("Sign in", onSignIn, Modifier.fillMaxWidth().padding(top = 12.dp))
            }
            ViewLoad.Gone -> Text("This session isn't on yaft any more. It may have been deleted on the website.")
            is ViewLoad.Failed -> {
                Text(load.message)
                InkButton("Try again", onRetry, Modifier.fillMaxWidth().padding(top = 12.dp), filled = false)
            }
            is ViewLoad.Ready, ViewLoad.Unsupported -> Unit
        }
    }
}

@Composable
private fun SheetTitle(text: String) = Text(text, style = Yaft.display, fontSize = 22.sp, modifier = Modifier.padding(bottom = 4.dp))

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(title, fontWeight = FontWeight.Bold, color = Yaft.ink2, fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
        content()
    }
}

/** A row of the numbers sheet: label (with a quiet note) on the left, the number on the right. */
@Composable
private fun Line(label: String, value: String, note: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label)
            note?.let { Text(it, color = Yaft.ink2, fontSize = 13.sp, style = Yaft.num) }
        }
        Text(value, style = Yaft.num, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
private fun Number(value: String, label: String) {
    Column(horizontalAlignment = Alignment.End) {
        Text(value, style = Yaft.num, fontWeight = FontWeight.Bold)
        Text(label, color = Yaft.ink2, fontSize = 12.sp)
    }
}

private fun speed(v: Double?) = v?.let { "${kmh(it)} km/h" } ?: "–"
private fun bpm(v: Double?) = v?.let { "${it.roundToInt()} bpm" } ?: "–"
