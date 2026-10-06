package site.yaft.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import site.yaft.app.data.SessionStore
import site.yaft.app.record.Recorder
import site.yaft.app.track.Category
import site.yaft.app.ui.AccountScreen
import site.yaft.app.ui.ReadyScreen
import site.yaft.app.ui.RecordingScreen
import site.yaft.app.ui.SessionScreen
import site.yaft.app.ui.SessionsScreen
import site.yaft.app.ui.Yaft
import site.yaft.app.ui.YaftTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { YaftTheme { App(application as YaftApp) } }
    }
}

/** Screens are a route string: "ready", "sessions", "session/<id>" or "account". */
@Composable
private fun App(app: YaftApp) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app", android.content.Context.MODE_PRIVATE) }
    var route by rememberSaveable { mutableStateOf("ready") }
    var category by rememberSaveable { mutableStateOf(Category.fromId(prefs.getString("category", null))) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    var batteryOk by rememberSaveable { mutableStateOf(true) }
    val live by Recorder.live.collectAsStateWithLifecycle()
    val finished by Recorder.finished.collectAsStateWithLifecycle()
    val sessions by SessionStore.sessions.collectAsStateWithLifecycle()
    val rider by app.account.rider.collectAsStateWithLifecycle()

    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
    val hasLocation = { ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED }
    val askPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasLocation()) {
            notice = null
            Recorder.start(context, category)
        } else {
            notice = "yaft needs precise location to record a session. Allow it in the app's settings."
        }
    }

    LifecycleResumeEffect(Unit) {
        batteryOk = (context.getSystemService(PowerManager::class.java)).isIgnoringBatteryOptimizations(context.packageName)
        // A recording left behind by a killed app picks up where it was.
        if (Recorder.live.value == null && SessionStore.unfinishedRecording() != null && hasLocation()) Recorder.resume(context)
        onPauseOrDispose {}
    }
    LaunchedEffect(finished) {
        finished?.let {
            route = "session/$it"
            Recorder.consumeFinished()
        }
    }

    BackHandler(enabled = route != "ready") {
        route = if (route.startsWith("session/")) "sessions" else "ready"
    }

    Box(Modifier.fillMaxSize().background(if (route == "ready" || route.startsWith("session/")) Yaft.hue(live?.category ?: currentCategory(route, category)) else Yaft.ground).safeDrawingPadding()) {
        val l = live
        when {
            l != null -> RecordingScreen(l, onStop = { Recorder.stop(context) })
            route == "sessions" -> SessionsScreen(sessions, onOpen = { route = "session/$it" }, onBack = { route = "ready" })
            route == "account" -> AccountScreen(app.account, rider, onBack = { route = "ready" })
            route.startsWith("session/") -> {
                val meta = sessions.firstOrNull { it.id == route.removePrefix("session/") }
                if (meta == null) route = "sessions"
                else SessionScreen(meta, app.account.siteUrl, rider != null, app.uploader, onBack = { route = "sessions" }, onSignIn = { route = "account" })
            }
            else -> ReadyScreen(
                category = category,
                onCategory = {
                    category = it
                    prefs.edit { putString("category", it.id) }
                },
                onStart = { askPermissions.launch(permissions) },
                onSessions = { route = "sessions" },
                onAccount = { route = "account" },
                notice = notice,
                batteryHint = if (batteryOk) null else { { context.startActivity(batteryIntent(context.packageName)) } },
            )
        }
    }
}

private fun currentCategory(route: String, fallback: Category): Category =
    if (route.startsWith("session/")) SessionStore.get(route.removePrefix("session/"))?.category ?: fallback else fallback

/** Asks Android to stop pausing yaft in the background (allowed on F-Droid; Play restricts it). */
@SuppressLint("BatteryLife")
private fun batteryIntent(pkg: String) =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:$pkg".toUri())
