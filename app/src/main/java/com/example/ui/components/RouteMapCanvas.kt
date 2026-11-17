package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.RoutePoint
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.EmeraldVibrant
import com.example.ui.theme.ForestGreenDeep
import com.example.ui.theme.ForestGreenMedium
import com.example.ui.theme.MintBright
import kotlinx.serialization.json.Json
import java.util.Locale
import kotlin.math.max

object RouteJsonCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    fun encode(points: List<RoutePoint>): String {
        if (points.isEmpty()) return ""
        return runCatching { json.encodeToString(points) }.getOrDefault("")
    }

    fun decode(raw: String): List<RoutePoint> {
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<RoutePoint>>(raw) }.getOrDefault(emptyList())
    }
}

@Composable
fun RouteMapCard(
    routePoints: List<RoutePoint>,
    distanceMeters: Int,
    durationSeconds: Int,
    modifier: Modifier = Modifier,
    mapHeight: Dp = 176.dp
) {
    if (routePoints.size < 2) return

    val maxSpeed = remember(routePoints) {
        routePoints.maxOfOrNull { it.speedKmh } ?: 0f
    }
    val avgPaceLabel = remember(distanceMeters, durationSeconds) {
        if (distanceMeters >= 50 && durationSeconds > 0) {
            val secPerKm = (durationSeconds * 1000.0 / distanceMeters).toInt()
            val min = secPerKm / 60
            val sec = secPerKm % 60
            String.format(Locale.US, "%d:%02d น./กม.", min, sec)
        } else {
            "--"
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = ForestGreenDeep,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Route,
                        contentDescription = null,
                        tint = MintBright,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "เส้นทาง GPS (${routePoints.size} จุด)",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "เพซเฉลี่ย $avgPaceLabel",
                    style = MaterialTheme.typography.labelMedium,
                    color = MintBright,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(mapHeight)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(ForestGreenMedium.copy(alpha = 0.42f), ForestGreenDeep)
                        )
                    )
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                    val minLat = routePoints.minOf { it.lat }
                    val maxLat = routePoints.maxOf { it.lat }
                    val minLng = routePoints.minOf { it.lng }
                    val maxLng = routePoints.maxOf { it.lng }

                    val latSpan = max(1e-5, maxLat - minLat)
                    val lngSpan = max(1e-5, maxLng - minLng)
                    val scale = minOf(size.width / lngSpan.toFloat(), size.height / latSpan.toFloat()) * 0.88f
                    val xOffset = (size.width - (lngSpan.toFloat() * scale)) / 2f
                    val yOffset = (size.height - (latSpan.toFloat() * scale)) / 2f

                    // Subtle radar grid lines
                    val gridColor = Color.White.copy(alpha = 0.08f)
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    for (i in 1..3) {
                        val y = size.height * (i / 4f)
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f, pathEffect = dashEffect)
                        val x = size.width * (i / 4f)
                        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5f, pathEffect = dashEffect)
                    }

                    val mapped = routePoints.map { pt ->
                        val px = xOffset + ((pt.lng - minLng).toFloat() * scale)
                        val py = size.height - (yOffset + ((pt.lat - minLat).toFloat() * scale))
                        Offset(px, py) to pt.speedKmh
                    }

                    for (i in 0 until mapped.lastIndex) {
                        val (p1, _) = mapped[i]
                        val (p2, speed) = mapped[i + 1]
                        val segColor = when {
                            speed >= 11f -> AmberWarm
                            speed >= 6f -> MintBright
                            else -> EmeraldVibrant
                        }
                        drawLine(
                            color = segColor,
                            start = p1,
                            end = p2,
                            strokeWidth = 8f,
                            cap = StrokeCap.Round
                        )
                    }

                    // Start & Finish nodes
                    val startPt = mapped.first().first
                    val endPt = mapped.last().first
                    drawCircle(color = MintBright, radius = 9f, center = startPt)
                    drawCircle(color = Color.White, radius = 4f, center = startPt)
                    drawCircle(color = AmberWarm, radius = 11f, center = endPt)
                    drawCircle(color = Color.White, radius = 11f, center = endPt, style = Stroke(width = 3f))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "• เขียว: เดิน/ช้า   • สว่าง: เร็ว   • ทอง: สปรินต์",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.72f)
                )
                Text(
                    text = String.format(Locale.US, "สูงสุด %.1f กม./ชม.", maxSpeed),
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberWarm,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
