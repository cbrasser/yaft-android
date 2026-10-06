package site.yaft.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import site.yaft.app.record.Live
import site.yaft.app.track.Category

/** Before a session: pick what you're riding and start. */
@Composable
fun ReadyScreen(
    category: Category,
    onCategory: (Category) -> Unit,
    onStart: () -> Unit,
    onSessions: () -> Unit,
    onAccount: () -> Unit,
    notice: String?,
    batteryHint: (() -> Unit)?,
) {
    Column(Modifier.fillMaxSize().background(Yaft.hue(category))) {
        TopBar("yaft") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BarLink("Sessions", onSessions)
                BarLink("Account", onAccount)
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Leaf(Modifier.fillMaxWidth(), padding = 8.dp) {
                Text("What are you riding?", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(8.dp))
                Category.entries.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onCategory(c) }.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = c == category, onClick = { onCategory(c) }, colors = RadioButtonDefaults.colors(selectedColor = Yaft.ink))
                        Box(Modifier.size(14.dp).background(Yaft.hue(c), CircleShape).border(2.dp, Yaft.ink, CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Text(c.label, fontSize = 16.sp)
                    }
                }
            }
            if (notice != null) {
                Leaf(Modifier.fillMaxWidth()) { Text(notice) }
            }
            if (batteryHint != null) {
                Leaf(Modifier.fillMaxWidth()) {
                    Text("Android may pause recording when the screen is off. Let yaft run in the background so a session isn't cut short.")
                    Spacer(Modifier.height(12.dp))
                    InkButton("Allow in background", batteryHint, filled = false)
                }
            }
        }
        InkButton("Start session", onStart, Modifier.fillMaxWidth().padding(16.dp).height(64.dp))
    }
}

/** While recording: big numbers you can read through a pouch, and hold to stop. */
@Composable
fun RecordingScreen(live: Live, onStop: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val elapsed = (now - live.startedAt) / 1000.0

    Column(Modifier.fillMaxSize().background(Yaft.hue(live.category)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(live.category.label, style = Yaft.display, fontSize = 24.sp, modifier = Modifier.weight(1f))
            val gps = when {
                !live.hasFix && live.points == 0 -> "Waiting for GPS…"
                !live.hasFix -> "GPS lost"
                else -> "GPS ±${live.accuracyM?.toInt() ?: "?"} m"
            }
            Text(gps, fontWeight = FontWeight.Bold, style = Yaft.num, modifier = Modifier.background(Yaft.paper, CircleShape).border(2.dp, Yaft.ink, CircleShape).padding(horizontal = 12.dp, vertical = 6.dp))
        }
        Leaf(Modifier.fillMaxWidth()) {
            Text(kmh(live.speedKmh), style = Yaft.num.merge(Yaft.display), fontSize = 96.sp, lineHeight = 96.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("km/h", fontSize = 18.sp, color = Yaft.ink2, modifier = Modifier.weight(1f))
                if (live.onFoil) Text("ON FOIL", fontWeight = FontWeight.ExtraBold, color = Yaft.paper, modifier = Modifier.background(Yaft.ink, CircleShape).padding(horizontal = 12.dp, vertical = 4.dp))
            }
        }
        Leaf(Modifier.fillMaxWidth()) {
            Row {
                Stat("Time", duration(elapsed), Modifier.weight(1f), big = true)
                Stat("On foil", duration(live.foilTimeS), Modifier.weight(1f), big = true)
            }
            Spacer(Modifier.height(16.dp))
            Row {
                Stat("Distance", km(live.distanceM), Modifier.weight(1f))
                Stat("Top speed (3 s)", "${kmh(live.top3Kmh)} km/h", Modifier.weight(1f))
            }
        }
        Spacer(Modifier.weight(1f))
        if (live.stopping) {
            Leaf(Modifier.fillMaxWidth()) { Text("Saving the session…", fontWeight = FontWeight.Bold) }
        } else {
            HoldButton("Hold to stop", onStop, Modifier.fillMaxWidth())
        }
    }
}
