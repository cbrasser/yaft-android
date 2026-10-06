package site.yaft.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.yaft.app.data.SessionMeta

@Composable
fun SessionsScreen(sessions: List<SessionMeta>, onOpen: (String) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Yaft.ground)) {
        TopBar("Sessions", onBack = onBack)
        if (sessions.isEmpty()) {
            Leaf(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("No sessions yet. Start one from the first screen; it's saved here when you stop.")
            }
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(sessions, key = { it.id }) { s ->
                Leaf(Modifier.fillMaxWidth().clickable { onOpen(s.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).background(Yaft.hue(s.category), CircleShape).border(2.dp, Yaft.ink, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(s.category.label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(if (s.uploadedAt != null) "On yaft" else "On phone", fontSize = 13.sp, color = Yaft.ink2)
                    }
                    Text(dateTime(s.startedAt, s.timeZone) + if (s.place.isNotBlank()) " · ${s.place}" else "", color = Yaft.ink2, fontSize = 14.sp)
                    Text(
                        "${km(s.distanceM)} · ${duration(s.durationS)} · ${duration(s.foilTimeS)} on foil · ${kmh(s.top3Kmh)} km/h",
                        style = Yaft.num, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}
