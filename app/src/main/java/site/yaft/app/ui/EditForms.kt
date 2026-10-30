package site.yaft.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import site.yaft.app.data.GearList
import site.yaft.app.data.GearLoad
import site.yaft.app.data.GearSlot
import site.yaft.app.data.SessionView
import site.yaft.app.data.loadGear
import site.yaft.app.net.Account
import site.yaft.app.net.EditResult
import site.yaft.app.net.SessionEdit
import site.yaft.app.net.saveSessionEdit
import site.yaft.app.track.Category

/**
 * The forms for a session on yaft: what the rider rode, the gear, the wind,
 * and their notes and rating. Each saves on its own, needs a connection, and
 * hands the view the website sends back to [onSaved].
 */
@Composable
fun EditForms(view: SessionView, account: Account, onSaved: (SessionView) -> Unit) {
    CategoryForm(view, account, onSaved)
    GearForm(view, account, onSaved)
    if (view.category.usesWind) WindForm(view, account, onSaved)
    NotesForm(view, account, onSaved)
}

@Composable
private fun CategoryForm(view: SessionView, account: Account, onSaved: (SessionView) -> Unit) {
    var picked by remember(view.category) { mutableStateOf(view.category) }
    var menu by remember { mutableStateOf(false) }
    Form("What you rode", view, account, onSaved, changed = picked != view.category, edit = { SessionEdit.Ridden(picked) }) {
        Text("yaft redoes the analysis for it.", color = Yaft.ink2, fontSize = 14.sp)
        Box {
            InkButton("${picked.label}  ▾", { menu = true }, Modifier.fillMaxWidth().padding(top = 8.dp), filled = false)
            DropdownMenu(menu, { menu = false }) {
                Category.entries.forEach { c ->
                    DropdownMenuItem(text = { Text(c.label) }, onClick = { picked = c; menu = false })
                }
            }
        }
    }
}

/**
 * The gear per slot of the session's category, picked from the rider's gear on
 * yaft (adding gear is on the website). The slots follow the saved category. A
 * split session keeps its gear on its parts, so it's shown here read-only.
 */
@Composable
private fun GearForm(view: SessionView, account: Account, onSaved: (SessionView) -> Unit) {
    var load by remember { mutableStateOf<GearLoad?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    // A reload keeps the old list on screen until the new one is in.
    LaunchedEffect(attempt) { load = loadGear(account) }
    val gear = (load as? GearLoad.Ready)?.gear
    val context = LocalContext.current
    val onYaft = { context.startActivity(Intent(Intent.ACTION_VIEW, "${account.siteUrl}/sessions/${view.id}".toUri())) }

    if (view.split) {
        Leaf(Modifier.fillMaxWidth()) {
            Text("Gear", style = Yaft.display, fontSize = 22.sp, modifier = Modifier.padding(bottom = 4.dp))
            Text("This session is split into parts, each with its own gear. Change it on yaft.", color = Yaft.ink2, fontSize = 14.sp)
            if (gear != null) view.parts.forEachIndexed { i, part ->
                Column(Modifier.padding(top = 12.dp)) {
                    Text("Part ${i + 1} · from ${timeOfDay(part.startsAt, view.timeZone)}", fontWeight = FontWeight.Bold, style = Yaft.num)
                    gear.slots(view.category).forEach { slot -> GearLine(slot.label, gear.name(part.gear[slot.slot]) ?: "Not set") }
                }
            }
            InkButton("Edit on yaft", onYaft, Modifier.fillMaxWidth().padding(top = 12.dp), filled = false)
        }
        return
    }

    when (val l = load) {
        is GearLoad.Ready -> if (l.gear.items.isEmpty()) {
            Leaf(Modifier.fillMaxWidth()) {
                Text("Gear", style = Yaft.display, fontSize = 22.sp, modifier = Modifier.padding(bottom = 4.dp))
                Text("Add your boards, foils and wings on yaft, then pick them here.", color = Yaft.ink2, fontSize = 14.sp)
                InkButton("Add gear on yaft", { context.startActivity(Intent(Intent.ACTION_VIEW, "${account.siteUrl}/gear".toUri())) }, Modifier.fillMaxWidth().padding(top = 12.dp), filled = false)
                InkButton("Reload", { attempt++ }, Modifier.fillMaxWidth().padding(top = 8.dp), filled = false)
            }
        } else GearPicks(view, l.gear, account, onSaved) { attempt++ }
        else -> Leaf(Modifier.fillMaxWidth()) {
            Text("Gear", style = Yaft.display, fontSize = 22.sp, modifier = Modifier.padding(bottom = 4.dp))
            when (l) {
                null -> Text("Loading your gear…", color = Yaft.ink2)
                GearLoad.SignedOut -> Text("Sign in again to pick your gear.", color = Yaft.danger)
                is GearLoad.Failed -> {
                    Text(l.message, color = Yaft.danger)
                    InkButton("Try again", { load = null; attempt++ }, Modifier.fillMaxWidth().padding(top = 12.dp), filled = false)
                }
                is GearLoad.Ready -> Unit
            }
        }
    }
}

@Composable
private fun GearPicks(view: SessionView, gear: GearList, account: Account, onSaved: (SessionView) -> Unit, onRefused: () -> Unit) {
    val slots = gear.slots(view.category)
    // Keyed on what's saved, so saving another form keeps what's picked here.
    var picks by remember(view.gear, view.category) { mutableStateOf(view.gear) }
    val changed = slots.any { picks[it.slot] != view.gear[it.slot] }
    // Every slot of the category goes; a slot it doesn't show (a wing after switching to dockstart) stays as it is.
    val edit = { SessionEdit.Gear(slots.associate { it.slot to picks[it.slot] }) }
    // Refused gear may be gone from the list: load it again.
    Form("Gear", view, account, onSaved, changed = changed, edit = edit, onRefused = onRefused) {
        Text("Pick from your gear on yaft.", color = Yaft.ink2, fontSize = 14.sp)
        slots.forEach { slot -> SlotPicker(slot, gear, current = view.gear[slot.slot], picked = picks[slot.slot]) { picks = picks + (slot.slot to it) } }
    }
}

@Composable
private fun SlotPicker(slot: GearSlot, gear: GearList, current: String?, picked: String?, onPick: (String?) -> Unit) {
    val options = gear.options(slot, current)
    var menu by remember { mutableStateOf(false) }
    Text(slot.label, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
    if (options.isEmpty()) {
        Text("No ${slot.label.lowercase()} yet. Add one on yaft.", color = Yaft.ink2, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        return
    }
    Box {
        InkButton("${gear.name(picked) ?: "None"}  ▾", { menu = true }, Modifier.fillMaxWidth().padding(top = 4.dp), filled = false)
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(text = { Text("None") }, onClick = { onPick(null); menu = false })
            options.forEach { g -> DropdownMenuItem(text = { Text(if (g.retired) "${g.name} (retired)" else g.name) }, onClick = { onPick(g.id); menu = false }) }
        }
    }
}

@Composable
private fun GearLine(label: String, name: String) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(label, color = Yaft.ink2, modifier = Modifier.weight(1f))
        Text(name, fontWeight = FontWeight.SemiBold)
    }
}

private val COMPASS = listOf("N" to 0, "NE" to 45, "E" to 90, "SE" to 135, "S" to 180, "SW" to 225, "W" to 270, "NW" to 315)

@Composable
private fun WindForm(view: SessionView, account: Account, onSaved: (SessionView) -> Unit) {
    val strength = view.category.windPowered
    val savedFrom = view.savedWind?.toString().orEmpty()
    val savedBase = view.windBase?.toString().orEmpty()
    val savedGust = view.windGust?.toString().orEmpty()
    // Keyed on the saved values, so saving another form keeps what's typed here.
    var from by remember(savedFrom) { mutableStateOf(savedFrom) }
    var base by remember(savedBase) { mutableStateOf(savedBase) }
    var gust by remember(savedGust) { mutableStateOf(savedGust) }
    val fromChanged = from.trim() != savedFrom
    val strengthChanged = strength && (base.trim() != savedBase || gust.trim() != savedGust)
    // Only what changed goes: a new direction redoes the analysis, the strength alone doesn't need to.
    val edit = { SessionEdit.Wind(from.takeIf { fromChanged }, base.takeIf { strengthChanged }, gust.takeIf { strengthChanged }) }
    Form("Wind", view, account, onSaved, changed = fromChanged || strengthChanged, edit = edit) {
        Text("Where it blew from. Leave it empty and yaft estimates it.", color = Yaft.ink2, fontSize = 14.sp)
        OutlinedTextField(
            from, { from = it.take(5) }, label = { Text("From (degrees)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = Yaft.num,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            COMPASS.forEach { (point, deg) ->
                val on = from.trim() == deg.toString()
                Text(
                    point,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (on) Yaft.paper else Yaft.ink,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button, onClickLabel = "Wind from $point") { from = deg.toString() }
                        .background(if (on) Yaft.ink else Yaft.groundWash)
                        .padding(vertical = 10.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (strength) Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                base, { base = it.take(3) }, label = { Text("Base (kn)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = Yaft.num, modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                gust, { gust = it.take(3) }, label = { Text("Gusts (kn)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = Yaft.num, modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NotesForm(view: SessionView, account: Account, onSaved: (SessionView) -> Unit) {
    var notes by remember(view.notes) { mutableStateOf(view.notes) }
    var rating by remember(view.rating) { mutableStateOf(view.rating) }
    Form("Notes", view, account, onSaved, changed = notes.trim() != view.notes || rating != view.rating, edit = { SessionEdit.Notes(notes, rating) }) {
        Text("Only you see these.", color = Yaft.ink2, fontSize = 14.sp)
        Row(Modifier.padding(top = 4.dp)) {
            (1..5).forEach { star ->
                Text(
                    if ((rating ?: 0) >= star) "★" else "☆",
                    fontSize = 30.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        // Tapping the rating again clears it.
                        .clickable(role = Role.Button, onClickLabel = if (rating == star) "Clear the rating" else "Rate $star of 5") { rating = if (rating == star) null else star }
                        .padding(horizontal = 4.dp),
                )
            }
        }
        OutlinedTextField(notes, { notes = it }, label = { Text("How it felt") }, minLines = 3, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}

/**
 * A leaf with a form and its Save button. Saving sends [edit] once: offline or
 * refused, the rider sees why and nothing is kept to send later.
 */
@Composable
private fun Form(
    title: String,
    view: SessionView,
    account: Account,
    onSaved: (SessionView) -> Unit,
    changed: Boolean,
    edit: () -> SessionEdit,
    onRefused: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember(view.id) { mutableStateOf<Pair<String, Boolean>?>(null) }
    Leaf(Modifier.fillMaxWidth()) {
        Text(title, style = Yaft.display, fontSize = 22.sp, modifier = Modifier.padding(bottom = 4.dp))
        content()
        InkButton(if (busy) "Saving…" else "Save", {
            busy = true
            message = null
            scope.launch {
                message = when (val r = saveSessionEdit(account, view.id, edit())) {
                    is EditResult.Saved -> {
                        onSaved(r.view)
                        "Saved to yaft." to false
                    }
                    is EditResult.Invalid -> {
                        onRefused()
                        r.message to true
                    }
                    EditResult.SignedOut -> "Sign in again to save this." to true
                    EditResult.Gone -> "This session isn't on yaft any more. It may have been deleted on the website." to true
                    is EditResult.Failed -> r.message to true
                }
                busy = false
            }
        }, Modifier.fillMaxWidth().padding(top = 12.dp), enabled = changed && !busy)
        message?.let { (text, error) -> Text(text, color = if (error) Yaft.danger else Yaft.ink2, modifier = Modifier.padding(top = 8.dp)) }
    }
}
