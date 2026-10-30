package site.yaft.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import site.yaft.app.data.SessionView
import site.yaft.app.net.Account
import site.yaft.app.net.EditResult
import site.yaft.app.net.SessionEdit
import site.yaft.app.net.saveSessionEdit
import site.yaft.app.track.Category

/**
 * The forms for a session on yaft: what the rider rode, the wind, and their
 * notes and rating. Each saves on its own, needs a connection, and hands the
 * view the website sends back to [onSaved]. Gear is set on the website.
 */
@Composable
fun EditForms(view: SessionView, account: Account, onSaved: (SessionView) -> Unit) {
    CategoryForm(view, account, onSaved)
    if (view.category.usesWind) WindForm(view, account, onSaved)
    NotesForm(view, account, onSaved)
}

@Composable
private fun CategoryForm(view: SessionView, account: Account, onSaved: (SessionView) -> Unit) {
    var picked by remember(view) { mutableStateOf(view.category) }
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

private val COMPASS = listOf("N" to 0, "NE" to 45, "E" to 90, "SE" to 135, "S" to 180, "SW" to 225, "W" to 270, "NW" to 315)

@Composable
private fun WindForm(view: SessionView, account: Account, onSaved: (SessionView) -> Unit) {
    val strength = view.category.windPowered
    var from by remember(view) { mutableStateOf(view.savedWind?.toString().orEmpty()) }
    var base by remember(view) { mutableStateOf(view.windBase?.toString().orEmpty()) }
    var gust by remember(view) { mutableStateOf(view.windGust?.toString().orEmpty()) }
    val changed = from.trim() != view.savedWind?.toString().orEmpty() ||
        strength && (base.trim() != view.windBase?.toString().orEmpty() || gust.trim() != view.windGust?.toString().orEmpty())
    Form("Wind", view, account, onSaved, changed, edit = { SessionEdit.Wind(from, base.takeIf { strength }, gust.takeIf { strength }) }) {
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
    var notes by remember(view) { mutableStateOf(view.notes) }
    var rating by remember(view) { mutableStateOf(view.rating) }
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
                    is EditResult.Invalid -> r.message to true
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
