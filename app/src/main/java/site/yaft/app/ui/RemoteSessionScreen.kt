package site.yaft.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import site.yaft.app.data.RemoteSession

/** A session that's on yaft but wasn't recorded on this phone: read-only, with a link to the full analysis. */
@Composable
fun RemoteSessionScreen(s: RemoteSession, siteUrl: String, onBack: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(Yaft.hue(s.category))) {
        TopBar(s.category.label, onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Leaf(Modifier.fillMaxWidth(), padding = 0.dp) {
                TrackCanvas(s.lonLat, s.onFoil, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)))
            }
            Leaf(Modifier.fillMaxWidth()) {
                Text(dateTime(s.startedAt, s.timeZone) + (s.place?.let { " · $it" } ?: ""), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(s.summary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Row {
                    Stat("Distance", km(s.distanceM), Modifier.weight(1f))
                    Stat("Time", duration(s.durationS), Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    Stat("On foil", duration(s.foilTimeS), Modifier.weight(1f))
                    Stat("Top speed (3 s)", s.top3Kmh?.let { "${kmh(it)} km/h" } ?: "–", Modifier.weight(1f))
                }
                s.device?.let { Text("Recorded with $it", color = Yaft.ink2, modifier = Modifier.padding(top = 12.dp)) }
            }
            InkButton("Open on yaft", { context.startActivity(Intent(Intent.ACTION_VIEW, "$siteUrl/sessions/${s.id}".toUri())) }, Modifier.fillMaxWidth())
        }
    }
}
