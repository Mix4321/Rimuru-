package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.data.model.RoutePoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import java.io.File
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class MoveMapStyle(val thaiLabel: String) {
    STREET("แผนที่ถนน"),
    CLEAN_LIGHT("แผนที่สว่าง"),
    TOPO("ภูมิประเทศ");

    fun next(): MoveMapStyle = when (this) {
        STREET -> CLEAN_LIGHT
        CLEAN_LIGHT -> TOPO
        TOPO -> STREET
    }
}

data class KmSplitMarker(
    val kmNumber: Int,
    val lat: Double,
    val lng: Double,
    val elapsedSec: Int
)

fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val earthRadiusMeters = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2.0) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLng / 2).pow(2.0)
    val c = 2.0 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    return earthRadiusMeters * c
}

fun computeKmSplitMarkers(points: List<RoutePoint>): List<KmSplitMarker> {
    if (points.size < 2) return emptyList()
    val splits = mutableListOf<KmSplitMarker>()
    var cumulativeMeters = 0.0
    var nextKmThreshold = 1000.0

    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val curr = points[i]
        val segMeters = haversineMeters(prev.lat, prev.lng, curr.lat, curr.lng)
        if (segMeters <= 0.1) continue

        while (cumulativeMeters + segMeters >= nextKmThreshold) {
            val fraction = ((nextKmThreshold - cumulativeMeters) / segMeters).coerceIn(0.0, 1.0)
            val interpLat = prev.lat + (curr.lat - prev.lat) * fraction
            val interpLng = prev.lng + (curr.lng - prev.lng) * fraction
            val interpSec = (prev.elapsedSec + (curr.elapsedSec - prev.elapsedSec) * fraction).toInt()
            splits += KmSplitMarker(
                kmNumber = (nextKmThreshold / 1000.0).toInt(),
                lat = interpLat,
                lng = interpLng,
                elapsedSec = interpSec
            )
            nextKmThreshold += 1000.0
        }
        cumulativeMeters += segMeters
    }
    return splits
}

private object OsmdroidMapInitializer {
    @Volatile
    private var configured = false

    val cartoVoyagerSource: OnlineTileSourceBase by lazy {
        object : XYTileSource(
            "CartoVoyager",
            0,
            19,
            256,
            ".png",
            arrayOf(
                "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
                "https://b.basemaps.cartocdn.com/rastertiles/voyager/",
                "https://c.basemaps.cartocdn.com/rastertiles/voyager/"
            ),
            "© OpenStreetMap contributors © CARTO"
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                val z = MapTileIndex.getZoom(pMapTileIndex)
                val x = MapTileIndex.getX(pMapTileIndex)
                val y = MapTileIndex.getY(pMapTileIndex)
                return "$baseUrl$z/$x/$y$mImageFilenameEnding"
            }
        }
    }

    fun ensureConfigured(context: Context) {
        if (configured) return
        synchronized(this) {
            if (configured) return
            runCatching {
                val appContext = context.applicationContext
                val config = Configuration.getInstance()
                config.userAgentValue = "MoveActivityTracker/1.0 (${appContext.packageName})"
                val baseDir = File(appContext.cacheDir, "osmdroid").apply { mkdirs() }
                val tileDir = File(baseDir, "tiles").apply { mkdirs() }
                config.osmdroidBasePath = baseDir
                config.osmdroidTileCache = tileDir
            }
            configured = true
        }
    }

    fun resolveTileSource(style: MoveMapStyle): ITileSource = when (style) {
        MoveMapStyle.STREET -> TileSourceFactory.MAPNIK
        MoveMapStyle.CLEAN_LIGHT -> cartoVoyagerSource
        MoveMapStyle.TOPO -> TileSourceFactory.OpenTopo
    }
}

private fun dpToPx(context: Context, dp: Float): Int =
    (dp * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)

private fun createDotMarkerDrawable(
    context: Context,
    fillColor: Int,
    ringColor: Int = AndroidColor.WHITE,
    haloColor: Int? = null,
    coreRadiusDp: Float = 8f
): Drawable {
    val haloPx = if (haloColor != null) dpToPx(context, coreRadiusDp + 7f) else dpToPx(context, coreRadiusDp + 3f)
    val size = haloPx * 2
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = size / 2f
    val cy = size / 2f

    if (haloColor != null) {
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = haloColor }
        canvas.drawCircle(cx, cy, haloPx.toFloat(), haloPaint)
    }

    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ringColor }
    val coreRadiusPx = dpToPx(context, coreRadiusDp).toFloat()
    canvas.drawCircle(cx, cy, coreRadiusPx + dpToPx(context, 2.5f), ringPaint)

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor }
    canvas.drawCircle(cx, cy, coreRadiusPx, fillPaint)

    return BitmapDrawable(context.resources, bmp)
}

private fun createBadgeDrawable(
    context: Context,
    label: String,
    bgColor: Int,
    borderColor: Int,
    textColor: Int = AndroidColor.WHITE
): Drawable {
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = context.resources.displayMetrics.density * 11f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val textWidth = textPaint.measureText(label)
    val hPad = dpToPx(context, 7f)
    val vPad = dpToPx(context, 4f)
    val strokePx = dpToPx(context, 1.5f).toFloat()
    val width = (textWidth + hPad * 2 + strokePx * 2).toInt().coerceAtLeast(dpToPx(context, 28f))
    val height = (textPaint.fontSpacing + vPad * 2 + strokePx * 2).toInt().coerceAtLeast(dpToPx(context, 20f))

    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val rect = RectF(strokePx, strokePx, width - strokePx, height - strokePx)
    val radius = height / 2f

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
    canvas.drawRoundRect(rect, radius, radius, bgPaint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = borderColor
        style = Paint.Style.STROKE
        strokeWidth = strokePx
    }
    canvas.drawRoundRect(rect, radius, radius, borderPaint)

    val baseline = rect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2f)
    canvas.drawText(label, rect.left + hPad.toFloat(), baseline, textPaint)

    return BitmapDrawable(context.resources, bmp)
}

private enum class SpeedBand(val lineColor: Int) {
    EASY(AndroidColor.parseColor("#10B981")),   // Emerald: walk / easy pace
    TEMPO(AndroidColor.parseColor("#0EA5E9")),  // Sky-cyan: steady run
    SPRINT(AndroidColor.parseColor("#F59E0B")); // Amber-gold: sprint / fast

    companion object {
        fun fromSpeedKmh(speedKmh: Float): SpeedBand = when {
            speedKmh >= 11f -> SPRINT
            speedKmh >= 6f -> TEMPO
            else -> EASY
        }
    }
}

private fun addRouteOverlaysToMap(
    mapView: MapView,
    context: Context,
    routePoints: List<RoutePoint>,
    liveLat: Double? = null,
    liveLng: Double? = null,
    accuracyMeters: Float? = null,
    showKmSplits: Boolean = true,
    isLiveSession: Boolean = false
) {
    mapView.overlays.clear()

    // 1. Live GPS accuracy circle
    if (isLiveSession && liveLat != null && liveLng != null && accuracyMeters != null && accuracyMeters in 1f..200f) {
        val circlePoints = Polygon.pointsAsCircle(
            GeoPoint(liveLat, liveLng),
            accuracyMeters.toDouble()
        )
        val accuracyCircle = Polygon(mapView).apply {
            points = circlePoints
            fillPaint.color = AndroidColor.argb(34, 16, 185, 129)
            outlinePaint.color = AndroidColor.argb(128, 16, 185, 129)
            outlinePaint.strokeWidth = 2.5f
        }
        mapView.overlays.add(accuracyCircle)
    }

    // 2. Trail polyline (white casing + pace-colored bands)
    if (routePoints.size >= 2) {
        val allGeo = routePoints.map { GeoPoint(it.lat, it.lng) }
        val casingLine = Polyline(mapView).apply {
            setPoints(allGeo)
            outlinePaint.color = AndroidColor.argb(225, 255, 255, 255)
            outlinePaint.strokeWidth = 15f
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
        }
        mapView.overlays.add(casingLine)

        var currentBand = SpeedBand.fromSpeedKmh(routePoints[1].speedKmh)
        val segmentChunk = mutableListOf(allGeo[0], allGeo[1])

        for (i in 1 until routePoints.lastIndex) {
            val nextBand = SpeedBand.fromSpeedKmh(routePoints[i + 1].speedKmh)
            if (nextBand == currentBand) {
                segmentChunk.add(allGeo[i + 1])
            } else {
                val poly = Polyline(mapView).apply {
                    setPoints(segmentChunk.toList())
                    outlinePaint.color = currentBand.lineColor
                    outlinePaint.strokeWidth = 9.5f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                }
                mapView.overlays.add(poly)
                segmentChunk.clear()
                segmentChunk.add(allGeo[i])
                segmentChunk.add(allGeo[i + 1])
                currentBand = nextBand
            }
        }
        if (segmentChunk.size >= 2) {
            val poly = Polyline(mapView).apply {
                setPoints(segmentChunk.toList())
                outlinePaint.color = currentBand.lineColor
                outlinePaint.strokeWidth = 9.5f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
            }
            mapView.overlays.add(poly)
        }
    }

    // 3. 1km Milestone Badges
    if (showKmSplits && routePoints.size >= 2) {
        val splits = computeKmSplitMarkers(routePoints).take(40)
        for (split in splits) {
            val kmMarker = Marker(mapView).apply {
                position = GeoPoint(split.lat, split.lng)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = "กิโลเมตรที่ ${split.kmNumber}"
                icon = createBadgeDrawable(
                    context = context,
                    label = "${split.kmNumber} กม.",
                    bgColor = AndroidColor.parseColor("#0A2E1E"),
                    borderColor = AndroidColor.parseColor("#74C69D")
                )
            }
            mapView.overlays.add(kmMarker)
        }
    }

    // 4. Start Marker
    if (routePoints.isNotEmpty()) {
        val start = routePoints.first()
        val startMarker = Marker(mapView).apply {
            position = GeoPoint(start.lat, start.lng)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title = "จุดเริ่มต้น"
            icon = createDotMarkerDrawable(
                context = context,
                fillColor = AndroidColor.parseColor("#10B981"),
                coreRadiusDp = 7.5f
            )
        }
        mapView.overlays.add(startMarker)
    }

    // 5. End / Live Runner Marker
    val currentLatLng = when {
        isLiveSession && liveLat != null && liveLng != null -> liveLat to liveLng
        routePoints.size >= 2 -> routePoints.last().lat to routePoints.last().lng
        else -> null
    }
    if (currentLatLng != null) {
        val endMarker = Marker(mapView).apply {
            position = GeoPoint(currentLatLng.first, currentLatLng.second)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title = if (isLiveSession) "ตำแหน่งปัจจุบัน" else "จุดสิ้นสุด"
            icon = createDotMarkerDrawable(
                context = context,
                fillColor = if (isLiveSession) {
                    AndroidColor.parseColor("#1B4332")
                } else {
                    AndroidColor.parseColor("#F59E0B")
                },
                ringColor = AndroidColor.WHITE,
                haloColor = if (isLiveSession) {
                    AndroidColor.argb(95, 116, 198, 157)
                } else {
                    AndroidColor.argb(80, 245, 158, 11)
                },
                coreRadiusDp = 8.5f
            )
        }
        mapView.overlays.add(endMarker)
    }

    mapView.invalidate()
}

private fun fitMapToRoute(mapView: MapView, routePoints: List<RoutePoint>, animate: Boolean = false) {
    if (routePoints.isEmpty()) return
    if (routePoints.size == 1) {
        val pt = GeoPoint(routePoints.first().lat, routePoints.first().lng)
        mapView.controller.setZoom(17.2)
        if (animate) mapView.controller.animateTo(pt) else mapView.controller.setCenter(pt)
        return
    }
    var minLat = Double.POSITIVE_INFINITY
    var maxLat = Double.NEGATIVE_INFINITY
    var minLng = Double.POSITIVE_INFINITY
    var maxLng = Double.NEGATIVE_INFINITY
    for (p in routePoints) {
        if (p.lat < minLat) minLat = p.lat
        if (p.lat > maxLat) maxLat = p.lat
        if (p.lng < minLng) minLng = p.lng
        if (p.lng > maxLng) maxLng = p.lng
    }
    val latSpan = max(0.0012, (maxLat - minLat) * 1.25)
    val lngSpan = max(0.0012, (maxLng - minLng) * 1.25)
    val cLat = (minLat + maxLat) / 2.0
    val cLng = (minLng + maxLng) / 2.0
    val box = BoundingBox(
        cLat + latSpan / 2.0,
        cLng + lngSpan / 2.0,
        cLat - latSpan / 2.0,
        cLng - lngSpan / 2.0
    )
    mapView.post {
        runCatching {
            mapView.zoomToBoundingBox(box, animate, 64)
        }
    }
}

@Composable
fun RealLiveTrackingMap(
    routePoints: List<RoutePoint>,
    currentLat: Double?,
    currentLng: Double?,
    accuracyMeters: Float?,
    mapStyle: MoveMapStyle,
    followUser: Boolean,
    recenterRequestTick: Int,
    onUserPanDetected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val onUserPanLatest by rememberUpdatedState(onUserPanDetected)

    remember(context) { OsmdroidMapInitializer.ensureConfigured(context) }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(OsmdroidMapInitializer.resolveTileSource(mapStyle))
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 4.0
            maxZoomLevel = 20.0
            // Sensible initial center (Bangkok) before first GPS lock arrives
            controller.setZoom(15.5)
            controller.setCenter(GeoPoint(13.7563, 100.5018))
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> v.parent?.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_MOVE -> {
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        if (event.pointerCount == 1) onUserPanLatest()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
        }
    }

    var hasCenteredOnFirstFix by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        mapView.onResume()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        mapView.onPause()
    }
    DisposableEffect(mapView) {
        onDispose {
            runCatching { mapView.onDetach() }
        }
    }

    LaunchedEffect(mapStyle) {
        mapView.setTileSource(OsmdroidMapInitializer.resolveTileSource(mapStyle))
    }

    LaunchedEffect(routePoints, currentLat, currentLng, accuracyMeters) {
        addRouteOverlaysToMap(
            mapView = mapView,
            context = context,
            routePoints = routePoints,
            liveLat = currentLat,
            liveLng = currentLng,
            accuracyMeters = accuracyMeters,
            showKmSplits = true,
            isLiveSession = true
        )

        val focusLat = currentLat ?: routePoints.lastOrNull()?.lat
        val focusLng = currentLng ?: routePoints.lastOrNull()?.lng
        if (focusLat != null && focusLng != null) {
            val target = GeoPoint(focusLat, focusLng)
            if (!hasCenteredOnFirstFix) {
                hasCenteredOnFirstFix = true
                mapView.controller.setZoom(17.5)
                mapView.controller.setCenter(target)
            } else if (followUser) {
                mapView.controller.animateTo(target)
            }
        }
    }

    LaunchedEffect(recenterRequestTick, followUser) {
        if (recenterRequestTick > 0 && followUser) {
            val focusLat = currentLat ?: routePoints.lastOrNull()?.lat
            val focusLng = currentLng ?: routePoints.lastOrNull()?.lng
            if (focusLat != null && focusLng != null) {
                mapView.controller.setZoom(17.5)
                mapView.controller.animateTo(GeoPoint(focusLat, focusLng))
            } else if (routePoints.isNotEmpty()) {
                fitMapToRoute(mapView, routePoints, animate = true)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun RealStaticRouteMap(
    routePoints: List<RoutePoint>,
    mapStyle: MoveMapStyle,
    fitTriggerTick: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    remember(context) { OsmdroidMapInitializer.ensureConfigured(context) }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(OsmdroidMapInitializer.resolveTileSource(mapStyle))
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 4.0
            maxZoomLevel = 20.0
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        mapView.onResume()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        mapView.onPause()
    }
    DisposableEffect(mapView) {
        onDispose {
            runCatching { mapView.onDetach() }
        }
    }

    LaunchedEffect(mapStyle) {
        mapView.setTileSource(OsmdroidMapInitializer.resolveTileSource(mapStyle))
    }

    LaunchedEffect(routePoints) {
        addRouteOverlaysToMap(
            mapView = mapView,
            context = context,
            routePoints = routePoints,
            showKmSplits = true,
            isLiveSession = false
        )
        fitMapToRoute(mapView, routePoints, animate = false)
    }

    LaunchedEffect(fitTriggerTick) {
        if (fitTriggerTick > 0) {
            fitMapToRoute(mapView, routePoints, animate = true)
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.fillMaxSize()
    )
}
