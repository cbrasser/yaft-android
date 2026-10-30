package site.yaft.app.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlin.math.log2
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
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.linear
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
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import site.yaft.app.data.ColorMode
import site.yaft.app.data.Run
import site.yaft.app.data.SessionView

/** The website's basemap: OpenFreeMap's Positron style, quieted. */
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/positron"

/** The credit the map carries: OpenFreeMap's, for its tiles, OpenMapTiles' schema and OpenStreetMap's data. */
private const val MAP_CREDIT = "OpenFreeMap © OpenMapTiles © OpenStreetMap"
private const val CREDIT_URL = "https://www.openstreetmap.org/copyright"

/**
 * The session's track on a map, styled as on the website: on foil an
 * ink-cased line in the category's hue (or the phase colours), or along the
 * speed or heart-rate ramp with [colorBy]; off foil dashed ink in every mode,
 * the start as a ring, and the chart's [cursor] as a dot. With [highlight],
 * that run stands out and the map frames it. Two fingers move and zoom the map, so one still scrolls the screen.
 * Without a connection, or when the map or its tiles don't load, it's the
 * [TrackCanvas] on its own; the canvas also shows while the map loads, and
 * the map tries again when the connection comes back.
 */
@Composable
fun TrackMap(view: SessionView, highlight: Run?, modifier: Modifier = Modifier, colorBy: ColorMode = ColorMode.FOIL, cursor: Int? = null) {
    val online = rememberOnline()
    var ready by remember(view) { mutableStateOf(false) }
    var failed by remember(view) { mutableStateOf(false) }
    LaunchedEffect(online) { if (online) failed = false }
    val tiled = view.lonLat.size >= 2 && !failed && (online || ready)
    Box(modifier.background(Yaft.mapWater)) {
        // A new view (another session, or this one loaded again) gets a new map.
        if (tiled) key(view) { TiledTrack(view, highlight, colorBy, cursor, onReady = { ready = true }, onFail = { failed = true; ready = false }) }
        if (!tiled || !ready) {
            val ramp = view.colors.ramp(colorBy)
            val values = if (ramp == null) null else rampValues(view, colorBy)
            TrackCanvas(
                view.lonLat,
                view.onFoil,
                Modifier.fillMaxSize(),
                highlight = highlight?.points,
                pointColor = if (ramp == null || values == null) null else { i -> values[i]?.let { Yaft.rampColor(ramp.low, ramp.high, it) } },
                cursor = cursor,
            )
            Chip(if (tiled) "Loading map…" else "No map tiles, so this is the track on its own.", Modifier.align(Alignment.BottomStart))
        } else {
            val uri = LocalUriHandler.current
            Chip(MAP_CREDIT, Modifier.align(Alignment.BottomEnd), onClick = { uri.openUri(CREDIT_URL) })
        }
    }
}

/** A small note on the map, on white. */
@Composable
private fun Chip(text: String, modifier: Modifier, onClick: (() -> Unit)? = null) {
    Text(
        text,
        color = Yaft.ink2,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Yaft.paper)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = "Open the map's copyright", onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun TiledTrack(view: SessionView, highlight: Run?, colorBy: ColorMode, cursor: Int?, onReady: () -> Unit, onFail: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val track = remember { mapTrack(view) }
    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply { onCreate(null) }
    }
    MapLifecycle(mapView)
    // Set once the style has loaded, the track is on it and the camera frames it.
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    val ready by rememberUpdatedState(onReady)
    val fail by rememberUpdatedState(onFail)
    val run by rememberUpdatedState(highlight)
    val mode by rememberUpdatedState(colorBy)
    val dot by rememberUpdatedState(cursor)
    fun MapLibreMap.show(run: Run?, animate: Boolean) {
        style?.fade(run?.number)
        val bounds = run?.let { boundsOf(view.lonLat, it.points) } ?: track.bounds
        val padding = with(density) { (if (run == null) 40.dp else 56.dp).roundToPx() }
        frame(bounds, padding, animate)
    }

    DisposableEffect(mapView) {
        var shown = false
        mapView.addOnDidFailLoadingMapListener { fail() }
        // A basemap tile that fails before the map first shows means no map; later, only a gap.
        // (A tile the server doesn't have isn't an error: MapLibre draws it empty.)
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
                // Gestures are handled in Compose (see the end of this function).
                setAllGesturesEnabled(false)
                isCompassEnabled = false
                isLogoEnabled = false
                // The credit is drawn in Compose, as text that's always visible.
                isAttributionEnabled = false
            }
            m.setStyle(Style.Builder().fromUri(STYLE_URL)) { style ->
                quietBasemap(style)
                addTrack(style, track, view)
                style.colorBy(mode)
                style.cursor(view, dot)
                m.show(run, animate = false)
                map = m
            }
        }
        onDispose {}
    }

    // A run picked (or let go) once the map shows: fade the rest and frame it.
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(highlight) {
        if (first) first = false else map?.show(highlight, animate = !reducedMotion(context))
    }

    LaunchedEffect(colorBy, map) { map?.style?.colorBy(colorBy) }
    LaunchedEffect(cursor, map) { map?.style?.cursor(view, cursor) }

    AndroidView({ mapView }, Modifier.fillMaxSize())
    // As on the website: one finger scrolls the screen, two move and zoom the map.
    Box(Modifier.fillMaxSize().pointerInput(map) {
        val m = map ?: return@pointerInput
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                if (event.changes.count { it.pressed } >= 2) {
                    val focus = event.calculateCentroid()
                    m.moveCamera(CameraUpdateFactory.zoomBy(log2(event.calculateZoom().toDouble()), android.graphics.Point(focus.x.toInt(), focus.y.toInt())))
                    val pan = event.calculatePan()
                    m.scrollBy(pan.x, pan.y)
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    })
}

/** Whether the phone has a connection to the internet, kept up to date. */
@Composable
private fun rememberOnline(): Boolean {
    val context = LocalContext.current
    val cm = remember { context.getSystemService(ConnectivityManager::class.java) }
    var online by remember { mutableStateOf(cm?.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true) }
    DisposableEffect(cm) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
            override fun onLost(network: Network) {
                online = false
            }
        }
        cm?.registerDefaultNetworkCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { cm?.unregisterNetworkCallback(callback) }
    }
    return online
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
    // Speed and heart rate along the ramp, as the website's rampExpression; shown by colorBy.
    for (ramp in view.colors.ramps) {
        val id = ramp.mode.id
        style.addSource(GeoJsonSource("foil-$id", rampTrack(view, ramp.mode)))
        val stops = Yaft.ramp.mapIndexed { k, c -> stop(ramp.low + (ramp.high - ramp.low) * k / (Yaft.ramp.size - 1), color(c.toArgb())) }
        style.addLayer(
            LineLayer("track-$id", "foil-$id").withProperties(
                *round,
                lineColor(interpolate(linear(), get("v"), *stops.toTypedArray())),
                lineWidth(4f),
                visibility(Property.NONE),
            ),
        )
    }
    if (view.lonLat.isNotEmpty()) style.addLayer(CircleLayer("track-start", "start").withProperties(circleRadius(7f), circleColor(paper), circleStrokeColor(ink), circleStrokeWidth(3f)))
    style.addSource(GeoJsonSource("cursor", FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(CircleLayer("track-cursor", "cursor").withProperties(circleRadius(7f), circleColor(ink), circleStrokeColor(paper), circleStrokeWidth(3f)))
}

/** Shows the on-foil track in [mode]'s colours; the casing and the dashed off-foil line stay. */
private fun Style.colorBy(mode: ColorMode) {
    for (m in ColorMode.entries) {
        val layer = if (m == ColorMode.FOIL) "track-foil" else "track-${m.id}"
        getLayer(layer)?.setProperties(visibility(if (m == mode) Property.VISIBLE else Property.NONE))
    }
}

/** The chart's point as a dot on the track, or none. */
private fun Style.cursor(view: SessionView, i: Int?) {
    val c = i?.let { view.lonLat.getOrNull(it) }
    val features = c?.let { listOf(Feature.fromGeometry(Point.fromLngLat(it[0], it[1]))) }.orEmpty()
    getSourceAs<GeoJsonSource>("cursor")?.setGeoJson(FeatureCollection.fromFeatures(features))
}

/** With [run], fades everything but that run, as the website does. */
private fun Style.fade(run: Int?) {
    fun opacity(full: Float, faded: Float): Expression =
        if (run == null) literal(full) else switchCase(eq(get("run"), literal(run)), literal(full), literal(faded))
    for (layer in listOf("track-foil", "track-speed", "track-hr")) getLayer(layer)?.setProperties(lineOpacity(opacity(1f, 0.25f)))
    getLayer("track-casing")?.setProperties(lineOpacity(opacity(1f, 0.25f)))
    getLayer("track-off")?.setProperties(lineOpacity(opacity(0.55f, 0.15f)))
}

private fun MapLibreMap.frame(b: Bounds, padding: Int, animate: Boolean) {
    val camera = getCameraForLatLngBounds(LatLngBounds.from(b.north, b.east, b.south, b.west), intArrayOf(padding, padding, padding, padding)) ?: return
    val update = CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(camera).zoom(minOf(camera.zoom, 17.0)).build())
    if (animate) animateCamera(update, 600) else moveCamera(update)
}

private fun reducedMotion(context: Context) =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
