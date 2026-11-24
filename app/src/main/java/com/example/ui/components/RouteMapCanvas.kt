package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.text.style.TextOverflow
import com.example.data.model.RoutePoint
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.EmeraldVibrant
import com.example.ui.theme.ForestGreenDeep
import com.example.ui.theme.MintBright
import kotlinx.serialization.json.Json
import java.util.Locale

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
    mapHeight: Dp = 210.dp,
    showSplitsTable: Boolean = true
) {
    if (routePoints.isEmpty()) return

    val kmSplits = remember(routePoints) { computeDetailedKmSplits(routePoints) }

    var mapStyle by remember { mutableStateOf(MoveMapStyle.STREET) }
    var fitTriggerTick by remember { mutableIntStateOf(0) }
    var showFullscreenMap by remember { mutableStateOf(false) }

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

    if (showFullscreenMap) {
        Dialog(
            onDismissRequest = { showFullscreenMap = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = ForestGreenDeep
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    RealStaticRouteMap(
                        routePoints = routePoints,
                        mapStyle = mapStyle,
                        fitTriggerTick = fitTriggerTick,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Top bar overlay
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = ForestGreenDeep.copy(alpha = 0.88f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.Route, contentDescription = null, tint = MintBright, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "แผนที่เส้นทางจริง (${routePoints.size} จุด) • เพซ $avgPaceLabel",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = { mapStyle = mapStyle.next() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(ForestGreenDeep.copy(alpha = 0.88f))
                            ) {
                                Icon(Icons.Rounded.Layers, contentDescription = "สลับรูปแบบแผนที่", tint = MintBright)
                            }
                            IconButton(
                                onClick = { fitTriggerTick++ },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(ForestGreenDeep.copy(alpha = 0.88f))
                            ) {
                                Icon(Icons.Rounded.CenterFocusStrong, contentDescription = "ปรับภาพพอดีเส้นทาง", tint = Color.White)
                            }
                            IconButton(
                                onClick = { showFullscreenMap = false },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(ForestGreenDeep.copy(alpha = 0.88f))
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "ปิดแผนที่เต็มจอ", tint = Color.White)
                            }
                        }
                    }

                    // Bottom legend overlay
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ForestGreenDeep.copy(alpha = 0.88f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "• เขียว: เดิน/ช้า  • ฟ้า: วิ่ง  • ส้ม: สปรินต์ (${mapStyle.thaiLabel})",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.86f)
                            )
                            Text(
                                text = String.format(Locale.US, "สูงสุด %.1f กม./ชม.", maxSpeed),
                                style = MaterialTheme.typography.labelMedium,
                                color = AmberWarm,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = ForestGreenDeep,
        modifier = modifier
            .fillMaxWidth()
            .testTag("real_route_map_card")
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
                        text = "แผนที่เส้นทางจริง (${routePoints.size} จุด)",
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
            ) {
                RealStaticRouteMap(
                    routePoints = routePoints,
                    mapStyle = mapStyle,
                    fitTriggerTick = fitTriggerTick,
                    modifier = Modifier.fillMaxSize()
                )

                // Compact top-right map action pills
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = { mapStyle = mapStyle.next() },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(ForestGreenDeep.copy(alpha = 0.82f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Layers,
                            contentDescription = "เปลี่ยนสไตล์แผนที่ (${mapStyle.thaiLabel})",
                            tint = MintBright,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { fitTriggerTick++ },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(ForestGreenDeep.copy(alpha = 0.82f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CenterFocusStrong,
                            contentDescription = "จัดมุมกล้องพอดีเส้นทาง",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { showFullscreenMap = true },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(ForestGreenDeep.copy(alpha = 0.82f))
                            .testTag("expand_route_map_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Fullscreen,
                            contentDescription = "ขยายแผนที่เต็มจอ",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "• เขียว: เดิน/ช้า  • ฟ้า: วิ่ง  • ส้ม: สปรินต์",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.78f)
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = String.format(Locale.US, "สูงสุด %.1f กม./ชม.", maxSpeed),
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberWarm,
                    fontWeight = FontWeight.Bold
                )
            }

            if (showSplitsTable && kmSplits.isNotEmpty()) {
                KmSplitsBreakdownCard(splits = kmSplits)
            }
        }
    }
}

@Composable
fun KmSplitsBreakdownCard(
    splits: List<KmSplitStat>,
    modifier: Modifier = Modifier
) {
    if (splits.isEmpty()) return

    var expanded by remember(splits.size) { mutableStateOf(false) }
    val visibleSplits = if (expanded || splits.size <= 5) splits else splits.take(5)
    val fastestPace = remember(splits) { splits.minOfOrNull { it.paceSecondsPerKm } ?: 300 }
    val slowestPace = remember(splits) { splits.maxOfOrNull { it.paceSecondsPerKm } ?: fastestPace }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.08f),
        modifier = modifier
            .fillMaxWidth()
            .testTag("km_splits_breakdown_card")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        imageVector = Icons.Rounded.Speed,
                        contentDescription = null,
                        tint = MintBright,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "ตารางสปลิทแต่ละกิโลเมตร (${splits.size} ช่วง)",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "เร็วสุด ${formatPacePerKm(fastestPace)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MintBright,
                    fontWeight = FontWeight.Bold
                )
            }

            visibleSplits.forEach { split ->
                val speedRatio = if (slowestPace <= fastestPace) {
                    0.85f
                } else {
                    val normalized = (slowestPace - split.paceSecondsPerKm).toFloat() /
                        (slowestPace - fastestPace).coerceAtLeast(1).toFloat()
                    (0.35f + normalized * 0.65f).coerceIn(0.28f, 1f)
                }
                val barColor = when {
                    split.isFastest -> MintBright
                    split.isPartialLeg -> Color(0xFF38BDF8)
                    speedRatio >= 0.65f -> EmeraldVibrant
                    else -> AmberWarm
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = split.labelThai,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (split.isFastest) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MintBright.copy(alpha = 0.22f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Bolt,
                                            contentDescription = null,
                                            tint = MintBright,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = "เร็วสุด",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MintBright,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.1f กม./ชม.", split.avgSpeedKmh),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.72f)
                            )
                            Text(
                                text = formatPacePerKm(split.paceSecondsPerKm),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (split.isFastest) MintBright else Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { speedRatio },
                        color = barColor,
                        trackColor = Color.White.copy(alpha = 0.14f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp))
                    )
                }
            }

            if (splits.size > 5) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { expanded = !expanded }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = MintBright,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (expanded) "ซ่อนบางช่วง" else "ดูครบทั้ง ${splits.size} ช่วง",
                        style = MaterialTheme.typography.labelMedium,
                        color = MintBright,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun formatPacePerKm(secPerKm: Int): String {
    val safe = secPerKm.coerceIn(1, 3599)
    return String.format(Locale.US, "%d:%02d น./กม.", safe / 60, safe % 60)
}
