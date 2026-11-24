package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.SportType
import com.example.data.model.StretchingCatalog
import com.example.data.model.TrackingMode
import com.example.ui.components.NeonPulseButton
import com.example.ui.components.StaggeredEntrance
import com.example.ui.components.bounceClickable
import com.example.ui.theme.EmeraldVibrant
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.ObsidianBlack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportSelectionScreen(
    onStartStretching: (SportType, TrackingMode) -> Unit,
    onBeginWorkout: (SportType, TrackingMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var readySport by remember { mutableStateOf<SportType?>(null) }

    if (readySport != null) {
        val sport = readySport!!
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { readySport = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            SportReadySheetContent(
                sport = sport,
                onStretchClicked = { selectedMode ->
                    readySport = null
                    onStartStretching(sport, selectedMode)
                },
                onStartClicked = { selectedMode ->
                    readySport = null
                    onBeginWorkout(sport, selectedMode)
                }
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "เลือกประเภทกีฬา",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "เลือกกิจกรรมที่คุณต้องการวันนี้ — บันทึกเวลาอิสระได้ทุกช่วงเวลา หรือใช้โหมด HIIT",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 158.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(SportType.entries, key = { _, it -> it.name }) { index, sport ->
                StaggeredEntrance(index = index) {
                    SportGridCard(
                        sport = sport,
                        isSelected = readySport == sport,
                        onClick = { readySport = sport }
                    )
                }
            }
        }
    }
}

@Composable
private fun SportGridCard(
    sport: SportType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        animationSpec = tween(durationMillis = 220),
        label = "sport_card_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        animationSpec = tween(durationMillis = 220),
        label = "sport_card_border"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 1.dp,
        animationSpec = tween(durationMillis = 220),
        label = "sport_card_border_w"
    )

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(borderWidth, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .bounceClickable(onClick = onClick)
            .testTag("sport_card_${sport.name}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val center = Offset(size.width * 0.88f, size.height * 0.14f)
                    val radius = size.maxDimension * 0.6f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                EmeraldVibrant.copy(alpha = if (isSelected) 0.18f else 0.08f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = radius
                        ),
                        center = center,
                        radius = radius
                    )
                }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(EmeraldVibrant, ForestGreen)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = sport.icon,
                        contentDescription = sport.thaiName,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = sport.thaiName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = sport.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2,
                maxLines = 2
            )

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Text(
                    text = "~${sport.kcalPerMinute.toInt()} kcal/นาที",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SportReadySheetContent(
    sport: SportType,
    onStretchClicked: (TrackingMode) -> Unit,
    onStartClicked: (TrackingMode) -> Unit
) {
    var selectedMode by remember(sport) { mutableStateOf(sport.defaultTrackingMode) }
    val stretchCount = remember(sport) { StretchingCatalog.exercisesFor(sport).size }
    val stretchFocus = remember(sport) { StretchingCatalog.summaryFocusFor(sport) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = sport.icon,
                    contentDescription = sport.thaiName,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp)
                )
            }
            Column {
                Text(
                    text = "พร้อมเริ่ม: ${sport.thaiName}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = sport.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "เลือกรูปแบบการตรวจจับจริง",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TrackingMode.entries.forEach { mode ->
                    val selected = selectedMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = { selectedMode = mode },
                        label = { Text(mode.shortLabel) },
                        leadingIcon = {
                            Icon(
                                imageVector = when (mode) {
                                    TrackingMode.CAMERA_AI -> Icons.Rounded.CameraAlt
                                    TrackingMode.SENSOR_GPS -> Icons.Rounded.Sensors
                                    TrackingMode.MANUAL -> Icons.Rounded.EditNote
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = when (selectedMode) {
                            TrackingMode.CAMERA_AI -> Icons.Rounded.CameraAlt
                            TrackingMode.SENSOR_GPS -> Icons.Rounded.Sensors
                            TrackingMode.MANUAL -> Icons.Rounded.EditNote
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = when (selectedMode) {
                            TrackingMode.CAMERA_AI -> "ตั้งมือถือให้เห็นร่างกาย AI จะจับโครงกระดูก นับครั้ง/ก้าว และเผาผลาญเมื่อเคลื่อนไหวจริงเท่านั้น"
                            TrackingMode.SENSOR_GPS -> "พกมือถือติดตัว ระบบใช้ตัวนับก้าวฮาร์ดแวร์ วัดความเร่ง และ GPS วัดระยะทางจริง"
                            TrackingMode.MANUAL -> "จับเวลาและประเมินแคลอรีตามระดับความหนักที่เลือก (สำหรับกิจกรรมที่ไม่พกมือถือ)"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Rounded.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "โหมดเวลาอิสระ: จับเวลาได้ทุกช่วงและบันทึกผลได้ทันที หรือเลือก HIIT สลับช่วงได้ในหน้าฝึก",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        val trackedList = buildList {
            add("ระยะเวลา & แคลอรี (MET)")
            if (sport.supportsSteps) add("จำนวนก้าวจริง")
            if (sport.supportsReps) add("จำนวนครั้ง/กระโดด")
            if (sport.supportsDistance) add("ระยะทาง GPS / ก้าว")
        }

        Text(
            text = "ข้อมูลที่ติดตาม: ${trackedList.joinToString(" • ")}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AccessibilityNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ยืดกล้ามเนื้อเฉพาะทาง ($stretchCount ท่า • ปรับเวลาได้)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = stretchFocus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }

        NeonPulseButton(
            text = "ยืดกล้ามเนื้อก่อนเริ่ม (แนะนำ)",
            onClick = { onStretchClicked(selectedMode) },
            leadingContent = {
                Icon(Icons.Rounded.AccessibilityNew, contentDescription = null, tint = ObsidianBlack)
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("start_stretching_button")
        )

        OutlinedButton(
            onClick = { onStartClicked(selectedMode) },
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.5.dp, EmeraldVibrant.copy(alpha = 0.7f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("confirm_start_workout_button")
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = EmeraldVibrant)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "ข้ามไปเริ่มออกกำลังกายเลย",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
    }
}
