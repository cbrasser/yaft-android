package site.yaft.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import site.yaft.app.net.Account
import site.yaft.app.net.SignedIn

@Composable
fun AccountScreen(account: Account, rider: SignedIn?, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var site by remember { mutableStateOf(account.siteUrl) }
    var showServer by remember { mutableStateOf(site != Account.DEFAULT_SITE) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(Yaft.ground)) {
        TopBar("Account", onBack = onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (rider != null) {
                Leaf(Modifier.fillMaxWidth()) {
                    Text("Signed in as", color = Yaft.ink2)
                    Text(rider.email, fontWeight = FontWeight.Bold)
                    Text(account.siteUrl, color = Yaft.ink2, modifier = Modifier.padding(top = 4.dp))
                    InkButton("Sign out", { account.signOut() }, Modifier.fillMaxWidth().padding(top = 16.dp), filled = false)
                }
                return@Column
            }
            Leaf(Modifier.fillMaxWidth()) {
                Text("Sign in with your yaft account to upload sessions. Recording works without one.")
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                if (showServer) {
                    OutlinedTextField(site, { site = it }, label = { Text("Server") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                } else {
                    Text("Use another server", color = Yaft.ink2, textDecoration = TextDecoration.Underline,
                        modifier = Modifier.padding(top = 12.dp).clickable { showServer = true })
                }
                error?.let { Text(it, color = Yaft.danger, modifier = Modifier.padding(top = 12.dp)) }
                InkButton(if (busy) "Signing in…" else "Sign in", {
                    busy = true
                    error = null
                    scope.launch {
                        account.signIn(site, email, password).onFailure { error = it.message }.onSuccess { password = "" }
                        busy = false
                    }
                }, Modifier.fillMaxWidth().padding(top = 16.dp), enabled = !busy && email.isNotBlank() && password.isNotEmpty())
            }
            Text("No account yet? Sign up at yaft.site", textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, "${account.siteUrl}/signup".toUri())) })
        }
    }
}
