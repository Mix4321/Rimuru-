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
import com.example.data.remote.NavigationRouteResult
import com.example.data.remote.NearbySpotCategory
import com.example.data.remote.NearbySpotsRepository
import com.example.data.remote.WorkoutSpot
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
import kotlin.math.max

private object NearbyMapInitializer {
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

private fun px(context: Context, dp: Float): Int =
    (dp * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)

private fun categoryPinColor(category: NearbySpotCategory): Int = when (category) {
    NearbySpotCategory.PARK_RUN -> AndroidColor.parseColor("#10B981")
    NearbySpotCategory.FITNESS_GYM -> AndroidColor.parseColor("#F59E0B")
    NearbySpotCategory.SPORTS_COURT -> AndroidColor.parseColor("#0EA5E9")
    NearbySpotCategory.SWIMMING -> AndroidColor.parseColor("#06B6D4")
    NearbySpotCategory.ALL -> AndroidColor.parseColor("#1F8A5B")
}

private fun createSpotPinDrawable(
    context: Context,
    fillColor: Int,
    isSelected: Boolean
): Drawable {
    val radiusDp = if (isSelected) 11f else 7.5f
    val haloPx = if (isSelected) px(context, 18f) else px(context, 11f)
    val size = haloPx * 2
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = size / 2f
    val cy = size / 2f

    if (isSelected) {
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.argb(90, AndroidColor.red(fillColor), AndroidColor.green(fillColor), AndroidColor.blue(fillColor))
        }
        canvas.drawCircle(cx, cy, haloPx.toFloat(), haloPaint)
    }

    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE }
    val coreR = px(context, radiusDp).toFloat()
    canvas.drawCircle(cx, cy, coreR + px(context, 2.5f), ringPaint)

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor }
    canvas.drawCircle(cx, cy, coreR, fillPaint)

    return BitmapDrawable(context.resources, bmp)
}

private fun createUserPulseDrawable(context: Context): Drawable {
    val haloPx = px(context, 17f)
    val size = haloPx * 2
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = size / 2f
    val cy = size / 2f

    val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(85, 52, 211, 153)
    }
    canvas.drawCircle(cx, cy, haloPx.toFloat(), haloPaint)

    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE }
    val coreR = px(context, 8.5f).toFloat()
    canvas.drawCircle(cx, cy, coreR + px(context, 2.5f), ringPaint)

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#0F3D2E")
    }
    canvas.drawCircle(cx, cy, coreR, fillPaint)

    return BitmapDrawable(context.resources, bmp)
}

private fun createSelectedSpotBadge(context: Context, title: String): Drawable {
    val clipped = if (title.length > 22) title.take(20) + "…" else title
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = context.resources.displayMetrics.density * 11.5f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val textW = textPaint.measureText(clipped)
    val hPad = px(context, 9f)
    val vPad = px(context, 5f)
    val strokePx = px(context, 1.5f).toFloat()
    val w = (textW + hPad * 2 + strokePx * 2).toInt().coerceAtLeast(px(context, 42f))
    val h = (textPaint.fontSpacing + vPad * 2 + strokePx * 2).toInt().coerceAtLeast(px(context, 24f))

    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val rect = RectF(strokePx, strokePx, w - strokePx, h - strokePx)
    val radius = h / 2f

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#0A2A1F")
    }
    canvas.drawRoundRect(rect, radius, radius, bgPaint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#34D399")
        style = Paint.Style.STROKE
        strokeWidth = strokePx
    }
    canvas.drawRoundRect(rect, radius, radius, borderPaint)

    val baseline = rect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2f)
    canvas.drawText(clipped, rect.left + hPad.toFloat(), baseline, textPaint)

    return BitmapDrawable(context.resources, bmp)
}

private fun fitBoundingBox(mapView: MapView, points: List<GeoPoint>, animate: Boolean) {
    if (points.isEmpty()) return
    if (points.size == 1) {
        mapView.controller.setZoom(16.2)
        if (animate) mapView.controller.animateTo(points.first()) else mapView.controller.setCenter(points.first())
        return
    }
    var minLat = Double.POSITIVE_INFINITY
    var maxLat = Double.NEGATIVE_INFINITY
    var minLng = Double.POSITIVE_INFINITY
    var maxLng = Double.NEGATIVE_INFINITY
    for (p in points) {
        if (p.latitude < minLat) minLat = p.latitude
        if (p.latitude > maxLat) maxLat = p.latitude
        if (p.longitude < minLng) minLng = p.longitude
        if (p.longitude > maxLng) maxLng = p.longitude
    }
    val latSpan = max(0.0022, (maxLat - minLat) * 1.32)
    val lngSpan = max(0.0022, (maxLng - minLng) * 1.32)
    val cLat = (minLat + maxLat) / 2.0
    val cLng = (minLng + maxLng) / 2.0
    val box = BoundingBox(
        cLat + latSpan / 2.0,
        cLng + lngSpan / 2.0,
        cLat - latSpan / 2.0,
        cLng - lngSpan / 2.0
    )
    mapView.post {
        runCatching { mapView.zoomToBoundingBox(box, animate, 72) }
    }
}

@Composable
fun NearbyNavigationMap(
    spots: List<WorkoutSpot>,
    selectedSpot: WorkoutSpot?,
    activeRoute: NavigationRouteResult?,
    userLat: Double?,
    userLng: Double?,
    searchCenterLat: Double?,
    searchCenterLng: Double?,
    accuracyMeters: Float?,
    mapStyle: MoveMapStyle,
    followUser: Boolean,
    isNavigatingLive: Boolean,
    fitRouteTick: Int,
    recenterTick: Int,
    onSpotMarkerClick: (WorkoutSpot) -> Unit,
    onUserPanDetected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val onSpotClickLatest by rememberUpdatedState(onSpotMarkerClick)
    val onUserPanLatest by rememberUpdatedState(onUserPanDetected)

    remember(context) { NearbyMapInitializer.ensureConfigured(context) }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(NearbyMapInitializer.resolveTileSource(mapStyle))
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 4.0
            maxZoomLevel = 20.0
            controller.setZoom(14.8)
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

    var hasInitialCentered by remember { mutableStateOf(false) }

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
        mapView.setTileSource(NearbyMapInitializer.resolveTileSource(mapStyle))
    }

    LaunchedEffect(spots, selectedSpot, activeRoute, userLat, userLng, accuracyMeters, isNavigatingLive) {
        mapView.overlays.clear()

        // 1. User GPS accuracy ring
        if (userLat != null && userLng != null && accuracyMeters != null && accuracyMeters in 2f..250f) {
            val circle = Polygon(mapView).apply {
                points = Polygon.pointsAsCircle(GeoPoint(userLat, userLng), accuracyMeters.toDouble())
                fillPaint.color = AndroidColor.argb(30, 16, 185, 129)
                outlinePaint.color = AndroidColor.argb(120, 16, 185, 129)
                outlinePaint.strokeWidth = 2f
            }
            mapView.overlays.add(circle)
        }

        // 2. Navigation Route Polyline
        if (activeRoute != null && activeRoute.polyline.size >= 2) {
            val routeGeo = activeRoute.polyline.map { GeoPoint(it.lat, it.lng) }
            val casing = Polyline(mapView).apply {
                setPoints(routeGeo)
                outlinePaint.color = AndroidColor.argb(235, 255, 255, 255)
                outlinePaint.strokeWidth = 17f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
            }
            val mainLine = Polyline(mapView).apply {
                setPoints(routeGeo)
                outlinePaint.color = if (isNavigatingLive) {
                    AndroidColor.parseColor("#0EA5E9")
                } else {
                    AndroidColor.parseColor("#1F8A5B")
                }
                outlinePaint.strokeWidth = 11f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
            }
            mapView.overlays.add(casing)
            mapView.overlays.add(mainLine)
        }

        // 3. Nearby Workout Spot Markers (draw unselected first, selected last on top)
        val sortedForZOrder = spots.sortedBy { it.id == selectedSpot?.id }
        for (spot in sortedForZOrder) {
            val isSelected = spot.id == selectedSpot?.id
            val marker = Marker(mapView).apply {
                position = GeoPoint(spot.lat, spot.lng)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = spot.name
                snippet = "${spot.categoryDetailThai} • ${NearbySpotsRepository.formatSpotDistanceThai(spot.distanceMeters)}"
                icon = createSpotPinDrawable(
                    context = context,
                    fillColor = categoryPinColor(spot.category),
                    isSelected = isSelected
                )
                setOnMarkerClickListener { _, _ ->
                    onSpotClickLatest(spot)
                    true
                }
            }
            mapView.overlays.add(marker)

            if (isSelected) {
                val badge = Marker(mapView).apply {
                    position = GeoPoint(spot.lat, spot.lng)
                    setAnchor(Marker.ANCHOR_CENTER, 2.0f)
                    icon = createSelectedSpotBadge(context, spot.name)
                    setOnMarkerClickListener { _, _ ->
                        onSpotClickLatest(spot)
                        true
                    }
                }
                mapView.overlays.add(badge)
            }
        }

        // 4. Current User Marker
        if (userLat != null && userLng != null) {
            val userMarker = Marker(mapView).apply {
                position = GeoPoint(userLat, userLng)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = "ตำแหน่งของคุณ"
                icon = createUserPulseDrawable(context)
            }
            mapView.overlays.add(userMarker)
        }

        mapView.invalidate()

        val focusLat = searchCenterLat ?: userLat
        val focusLng = searchCenterLng ?: userLng
        if (focusLat != null && focusLng != null) {
            val pt = GeoPoint(focusLat, focusLng)
            if (!hasInitialCentered) {
                hasInitialCentered = true
                mapView.controller.setZoom(14.8)
                mapView.controller.setCenter(pt)
            } else if (followUser && isNavigatingLive && userLat != null && userLng != null) {
                mapView.controller.animateTo(GeoPoint(userLat, userLng))
            }
        }
    }

    LaunchedEffect(fitRouteTick) {
        if (fitRouteTick > 0) {
            val routePts = activeRoute?.polyline?.map { GeoPoint(it.lat, it.lng) }
            if (!routePts.isNullOrEmpty()) {
                fitBoundingBox(mapView, routePts, animate = true)
            } else if (selectedSpot != null && userLat != null && userLng != null) {
                fitBoundingBox(
                    mapView,
                    listOf(GeoPoint(userLat, userLng), GeoPoint(selectedSpot.lat, selectedSpot.lng)),
                    animate = true
                )
            }
        }
    }

    LaunchedEffect(recenterTick) {
        if (recenterTick > 0) {
            val targetLat = searchCenterLat ?: userLat
            val targetLng = searchCenterLng ?: userLng
            if (targetLat != null && targetLng != null) {
                mapView.controller.setZoom(if (isNavigatingLive) 17.0 else 15.0)
                mapView.controller.animateTo(GeoPoint(targetLat, targetLng))
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
