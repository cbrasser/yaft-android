package site.yaft.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.color
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.match
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.switchCase
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.tile.TileOperation
import org.maplibre.geojson.Point
import site.yaft.app.data.Run
import site.yaft.app.data.SessionView

/** The website's basemap: OpenFreeMap's Positron style, quieted. */
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/positron"

/** The credit the map carries (OpenStreetMap's licence asks for it), as on the website. */
private const val MAP_CREDIT = "© OpenStreetMap · OpenFreeMap"
private const val CREDIT_URL = "https://www.openstreetmap.org/copyright"

private enum class Tiles { Loading, Ready, Failed }

/**
 * The session's track on a map, styled as on the website: on foil an
 * ink-cased line in the category's hue (or the phase colours), off foil
 * dashed ink, the start as a ring. With [highlight], that run stands out and
 * the map frames it. Without a connection, or when the map or its tiles don't
 * load, it's the [TrackCanvas] on its own; the canvas also shows while the map loads.
 */
@Composable
fun TrackMap(view: SessionView, highlight: Run?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var tiles by remember(view.id) { mutableStateOf(if (online(context)) Tiles.Loading else Tiles.Failed) }
    Box(modifier.background(Yaft.mapWater)) {
        if (tiles != Tiles.Failed) MapLibreTrack(view, highlight, onReady = { tiles = Tiles.Ready }, onFail = { tiles = Tiles.Failed })
        if (tiles != Tiles.Ready) {
            TrackCanvas(view.lonLat, view.onFoil, Modifier.fillMaxSize(), highlight = highlight?.points)
            Text(
                if (tiles == Tiles.Loading) "Loading map…" else "No map tiles, so this is the track on its own.",
                color = Yaft.ink2,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            )
        } else {
            val uri = LocalUriHandler.current
            Text(
                MAP_CREDIT,
                color = Yaft.ink2,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Yaft.paper.copy(alpha = 0.85f))
                    .clickable(role = Role.Button, onClickLabel = "Open the map's copyright") { uri.openUri(CREDIT_URL) }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
private fun MapLibreTrack(view: SessionView, highlight: Run?, onReady: () -> Unit, onFail: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val track = remember(view) { mapTrack(view) }
    val mapView = remember(view.id) {
        MapLibre.getInstance(context.applicationContext)
        MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply {
            onCreate(null)
            // Inside the scrolling screen, a drag on the map moves the map.
            setOnTouchListener { v, e ->
                if (e.actionMasked == MotionEvent.ACTION_DOWN) v.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
    }
    MapLifecycle(mapView)
    // Set once the style has loaded and the track is on it.
    var map by remember(mapView) { mutableStateOf<MapLibreMap?>(null) }
    val ready by rememberUpdatedState(onReady)
    val fail by rememberUpdatedState(onFail)

    DisposableEffect(mapView) {
        var shown = false
        val giveUp = Runnable { if (!shown) fail() }
        mapView.postDelayed(giveUp, 15_000)
        mapView.addOnDidFailLoadingMapListener { fail() }
        // A basemap tile that fails before the map first shows means no map; later, only a gap.
        mapView.addOnTileActionListener { op, _, _, _, _, _, _ ->
            if (op == TileOperation.Error) mapView.post { if (!shown) fail() }
        }
        mapView.addOnDidBecomeIdleListener {
            if (!shown && map != null) {
                shown = true
                ready()
            }
        }
        mapView.getMapAsync { m ->
            m.uiSettings.apply {
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                isCompassEnabled = false
                isLogoEnabled = false
                // The credit is drawn in Compose, as text that's always visible.
                isAttributionEnabled = false
            }
            m.setStyle(Style.Builder().fromUri(STYLE_URL)) { style ->
                quietBasemap(style)
                addTrack(style, track, view)
                map = m
            }
        }
        onDispose { mapView.removeCallbacks(giveUp) }
    }

    // Frame the track, or the highlighted run with everything else faded.
    var framed by remember(mapView) { mutableStateOf(false) }
    LaunchedEffect(map, highlight) {
        val m = map ?: return@LaunchedEffect
        m.style?.fade(highlight?.number)
        val bounds = highlight?.let { boundsOf(view.lonLat, it.points) } ?: track.bounds
        val padding = with(density) { (if (highlight == null) 40.dp else 56.dp).roundToPx() }
        m.frame(bounds, padding, animate = framed && !reducedMotion(context))
        framed = true
    }

    AndroidView({ mapView }, Modifier.fillMaxSize())
}

/** Runs [mapView] in step with the screen, and destroys it when it leaves the composition. */
@Composable
private fun MapLifecycle(mapView: MapView) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> { mapView.onStop(); started = false }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }
}

/** Pale water and no points of interest, as on the website. */
private fun quietBasemap(style: Style) {
    val poi = Regex("poi|housenumber")
    for (layer in style.layers) {
        if (layer is SymbolLayer && poi.containsMatchIn(layer.id)) layer.setProperties(visibility(Property.NONE))
        if (layer is FillLayer && "water" in layer.id) layer.setProperties(fillColor(Yaft.mapWater.toArgb()))
    }
}

private fun addTrack(style: Style, track: MapTrack, view: SessionView) {
    val ink = Yaft.ink.toArgb()
    val paper = Yaft.paper.toArgb()
    val round = arrayOf(lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND))
    style.addSource(GeoJsonSource("off", track.off))
    style.addSource(GeoJsonSource("foil", track.foil))
    view.lonLat.firstOrNull()?.let { style.addSource(GeoJsonSource("start", Point.fromLngLat(it[0], it[1]))) }
    style.addLayer(LineLayer("track-off", "off").withProperties(*round, lineColor(ink), lineWidth(1.5f), lineOpacity(0.55f), lineDasharray(arrayOf(1.5f, 2f))))
    style.addLayer(LineLayer("track-casing", "foil").withProperties(*round, lineColor(ink), lineWidth(7f)))
    style.addLayer(
        LineLayer("track-foil", "foil").withProperties(
            *round,
            // Phases: up in ink, reach in grey, turns in white, down and the rest in the category's hue.
            lineColor(match(get("phase"), color(Yaft.hue(view.category).toArgb()), stop(1, color(ink)), stop(2, color(Yaft.reach.toArgb())), stop(4, color(paper)))),
            lineWidth(4f),
        ),
    )
    if (view.lonLat.isNotEmpty()) style.addLayer(CircleLayer("track-start", "start").withProperties(circleRadius(7f), circleColor(paper), circleStrokeColor(ink), circleStrokeWidth(3f)))
}

/** With [run], fades everything but that run, as the website does. */
private fun Style.fade(run: Int?) {
    fun opacity(full: Float, faded: Float): Expression =
        if (run == null) literal(full) else switchCase(eq(get("run"), literal(run)), literal(full), literal(faded))
    getLayer("track-foil")?.setProperties(lineOpacity(opacity(1f, 0.25f)))
    getLayer("track-casing")?.setProperties(lineOpacity(opacity(1f, 0.25f)))
    getLayer("track-off")?.setProperties(lineOpacity(opacity(0.55f, 0.15f)))
}

private fun MapLibreMap.frame(b: Bounds, padding: Int, animate: Boolean) {
    val camera = getCameraForLatLngBounds(LatLngBounds.from(b.north, b.east, b.south, b.west), intArrayOf(padding, padding, padding, padding)) ?: return
    val update = CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(camera).zoom(minOf(camera.zoom, 17.0)).build())
    if (animate) animateCamera(update, 600) else moveCamera(update)
}

private fun online(context: Context): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
    return cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}

private fun reducedMotion(context: Context) =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
