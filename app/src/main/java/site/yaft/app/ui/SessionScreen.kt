package site.yaft.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import site.yaft.app.data.SessionMeta
import site.yaft.app.data.SessionStore
import site.yaft.app.net.UploadResult
import site.yaft.app.net.Uploader
import site.yaft.app.track.Category

@Composable
fun SessionScreen(
    meta: SessionMeta,
    siteUrl: String,
    signedIn: Boolean,
    uploader: Uploader,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val points by produceState(emptyList(), meta.id) { value = withContext(Dispatchers.IO) { SessionStore.loadPoints(meta.id) } }
    var place by remember(meta.id) { mutableStateOf(meta.place) }
    var categoryMenu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var duplicateOf by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val uploaded = meta.uploadedAt != null

    // Save the spot name as it's typed (it goes up with the session).
    LaunchedEffect(place) { if (place != meta.place) withContext(Dispatchers.IO) { SessionStore.save(meta.copy(place = place.take(80))) } }

    fun upload(allowDuplicate: Boolean) {
        busy = true
        message = null
        scope.launch {
            when (val r = uploader.upload(meta.copy(place = place.trim()), allowDuplicate)) {
                UploadResult.Done -> message = "Saved to yaft."
                is UploadResult.Duplicate -> duplicateOf = r.otherId
                UploadResult.SignedOut -> onSignIn()
                is UploadResult.Failed -> message = r.message
            }
            busy = false
        }
    }
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))

    Column(Modifier.fillMaxSize().background(Yaft.hue(meta.category))) {
        TopBar(meta.category.label, onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Leaf(Modifier.fillMaxWidth(), padding = 0.dp) {
                TrackCanvas(points, meta.category, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)))
            }
            Leaf(Modifier.fillMaxWidth()) {
                Text(dateTime(meta.startedAt, meta.timeZone), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row {
                    Stat("Distance", km(meta.distanceM), Modifier.weight(1f))
                    Stat("Time", duration(meta.durationS), Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    Stat("On foil", duration(meta.foilTimeS), Modifier.weight(1f))
                    Stat("Top speed (3 s)", "${kmh(meta.top3Kmh)} km/h", Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Text("yaft.site works out runs, flights, turns and wind when you upload.", color = Yaft.ink2)
            }
            Leaf(Modifier.fillMaxWidth()) {
                if (!uploaded) {
                    Text("What you rode", fontWeight = FontWeight.Bold)
                    Box {
                        InkButton("${meta.category.label}  ▾", { categoryMenu = true }, Modifier.fillMaxWidth().padding(top = 8.dp), filled = false)
                        DropdownMenu(categoryMenu, { categoryMenu = false }) {
                            Category.entries.forEach { c ->
                                DropdownMenuItem(text = { Text(c.label) }, onClick = {
                                    categoryMenu = false
                                    scope.launch(Dispatchers.IO) { SessionStore.setCategory(meta.id, c) }
                                })
                            }
                        }
                    }
                    OutlinedTextField(place, { place = it.take(80) }, label = { Text("Spot (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                } else {
                    Text("This session is on yaft. Change its category, wind and gear there.")
                }
                Spacer(Modifier.height(16.dp))
                when {
                    uploaded -> InkButton("Open on yaft", { open("$siteUrl/sessions/${meta.id}") }, Modifier.fillMaxWidth())
                    !signedIn -> InkButton("Sign in to upload", onSignIn, Modifier.fillMaxWidth())
                    else -> InkButton(if (busy) "Uploading…" else "Upload to yaft", { upload(false) }, Modifier.fillMaxWidth(), enabled = !busy)
                }
                message?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InkButton("Share GPX", {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", SessionStore.gpxFile(meta.id))
                    val send = Intent(Intent.ACTION_SEND).setType("application/gpx+xml").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(send, null))
                }, Modifier.weight(1f), filled = false)
                InkButton("Delete", { confirmDelete = true }, Modifier.weight(1f), filled = false)
            }
        }
    }

    duplicateOf?.let { other ->
        AlertDialog(
            onDismissRequest = { duplicateOf = null },
            title = { Text("Already on yaft?") },
            text = { Text("This looks like a session you already have: it starts at the same time and lasts about as long.") },
            confirmButton = { TextButton({ duplicateOf = null; upload(true) }) { Text("Upload anyway") } },
            dismissButton = { TextButton({ duplicateOf = null; open("$siteUrl/sessions/$other") }) { Text("Open that one") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete from this phone?") },
            text = { Text(if (uploaded) "It stays on yaft." else "It isn't on yaft yet, so it's gone for good.") },
            confirmButton = { TextButton({ confirmDelete = false; SessionStore.delete(meta.id); onBack() }) { Text("Delete", color = Yaft.danger) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Keep") } },
        )
    }
}
