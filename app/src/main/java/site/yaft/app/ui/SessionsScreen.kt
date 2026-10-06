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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.yaft.app.data.RemoteSession
import site.yaft.app.data.RemoteState
import site.yaft.app.data.SessionMeta
import site.yaft.app.track.Category

/** One row: a phone recording, a yaft session, or both (an uploaded recording). */
data class ListEntry(val id: String, val startedAt: Long, val local: SessionMeta?, val remote: RemoteSession?) {
    val category: Category get() = remote?.category ?: local!!.category
}

/** Phone recordings and the rider's yaft sessions in one list, newest first. */
fun mergeSessions(local: List<SessionMeta>, remote: List<RemoteSession>): List<ListEntry> {
    val byId = remote.associateBy { it.id }
    val localIds = local.mapTo(HashSet()) { it.id }
    return (local.map { ListEntry(it.id, it.startedAt, it, byId[it.id]) } +
        remote.filter { it.id !in localIds }.map { ListEntry(it.id, it.startedAt, null, it) })
        .sortedByDescending { it.startedAt }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    entries: List<ListEntry>,
    remote: RemoteState,
    signedIn: Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (ListEntry) -> Unit,
    onSignIn: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Yaft.ground)) {
        TopBar("Sessions", onBack = onBack)
        PullToRefreshBox(isRefreshing = remote.loading, onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (!signedIn) item {
                    Leaf(Modifier.fillMaxWidth().clickable(onClick = onSignIn)) {
                        Text("Sign in to see your yaft sessions here too, and to upload new ones.")
                    }
                }
                remote.error?.let { item { Text(it, color = Yaft.ink2, modifier = Modifier.padding(horizontal = 4.dp)) } }
                if (entries.isEmpty() && !remote.loading) item {
                    Leaf(Modifier.fillMaxWidth()) { Text("No sessions yet. Start one from the first screen; it's saved here when you stop.") }
                }
                items(entries, key = { it.id }) { e -> SessionRow(e, Modifier.fillMaxWidth().clickable { onOpen(e) }) }
                if (signedIn && remote.next != null) item {
                    InkButton(if (remote.loading) "Loading…" else "Load older sessions", onLoadMore, Modifier.fillMaxWidth(), enabled = !remote.loading, filled = false)
                }
            }
        }
    }
}

@Composable
private fun SessionRow(e: ListEntry, modifier: Modifier) {
    val r = e.remote
    val l = e.local
    val zone = r?.timeZone ?: l!!.timeZone
    val place = r?.place ?: l?.place?.takeIf { it.isNotBlank() }
    val label = when {
        l != null && l.uploadedAt == null -> "Not uploaded"
        r == null -> "On yaft"
        l != null -> "Recorded here"
        else -> r.device ?: "On yaft"
    }
    Leaf(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).background(Yaft.hue(e.category), CircleShape).border(2.dp, Yaft.ink, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(e.category.label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(label, fontSize = 13.sp, color = if (l != null && l.uploadedAt == null) Yaft.ink else Yaft.ink2, fontWeight = if (l != null && l.uploadedAt == null) FontWeight.Bold else FontWeight.Normal)
        }
        Text(dateTime(e.startedAt, zone) + (place?.let { " · $it" } ?: ""), color = Yaft.ink2, fontSize = 14.sp)
        // yaft's own numbers when it has the session; the phone's estimate otherwise.
        val distance = r?.distanceM ?: l!!.distanceM
        val time = r?.durationS ?: l!!.durationS
        val foil = r?.foilTimeS ?: l!!.foilTimeS
        val top = r?.top3Kmh ?: l?.top3Kmh
        Text(
            "${km(distance)} · ${duration(time)} · ${duration(foil)} on foil" + (top?.let { " · ${kmh(it)} km/h" } ?: ""),
            style = Yaft.num, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp),
        )
    }
}
