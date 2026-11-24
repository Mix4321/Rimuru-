package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.DetectionSensitivity
import com.example.data.local.ThemeMode
import com.example.domain.AchievementBadge
import com.example.domain.DateStreakUtils
import com.example.ui.components.GlassPulseCard
import com.example.ui.components.auroraGlowBackdrop
import com.example.ui.components.bounceClickable
import com.example.ui.theme.CoralError
import com.example.ui.theme.DeepForestGreen
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.NeonLime
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianSurface
import com.example.ui.viewmodel.DashboardData
import com.example.ui.viewmodel.SettingsViewModel
import com.example.widget.MoveWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class ProfileSettingsGroup(val label: String) {
    DISPLAY("ธีม & หน้าจอ"),
    AI_COACH("AI & ยืดเหยียด"),
    BACKUP("สำรอง & สิทธิ์")
}

@Composable
fun ProfileScreen(
    data: DashboardData,
    settingsViewModel: SettingsViewModel,
    userEmail: String? = null,
    userPhotoUrl: String? = null,
    onOpenAllBadges: () -> Unit,
    onOpenFullHistory: () -> Unit,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val profile by settingsViewModel.profile.collectAsStateWithLifecycle()

    var selectedSettingsGroup by remember { mutableStateOf(ProfileSettingsGroup.DISPLAY) }
    var showEditGoalsDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        val content = settingsViewModel.buildJsonBackup()
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            stream.write(content.toByteArray(Charsets.UTF_8))
                        } ?: error("Open output failed")
                    }.isSuccess
                }
                settingsViewModel.notifyMessage(
                    if (ok) "ส่งออกไฟล์สำรองข้อมูล JSON เรียบร้อยแล้ว" else "ส่งออกไฟล์ JSON ไม่สำเร็จ"
                )
            }
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        val csv = settingsViewModel.buildCsvExport()
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            stream.write(csv.toByteArray(Charsets.UTF_8))
                        } ?: error("Open output failed")
                    }.isSuccess
                }
                settingsViewModel.notifyMessage(
                    if (ok) "ส่งออกไฟล์ CSV เรียบร้อยแล้ว" else "ส่งออกไฟล์ CSV ไม่สำเร็จ"
                )
            }
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val raw = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    }.getOrNull()
                }
                if (raw.isNullOrBlank()) {
                    settingsViewModel.notifyMessage("ไม่สามารถอ่านไฟล์ที่เลือกได้")
                } else {
                    settingsViewModel.importBackupJson(raw) {
                        MoveWidgetProvider.refreshAllWidgets(context)
                    }
                }
            }
        }
    }

    var hasCameraPerm by remember { mutableStateOf(checkProfilePerm(context, Manifest.permission.CAMERA)) }
    var hasLocationPerm by remember { mutableStateOf(checkProfilePerm(context, Manifest.permission.ACCESS_FINE_LOCATION)) }
    var hasActivityPerm by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                checkProfilePerm(context, Manifest.permission.ACTIVITY_RECOGNITION)
            } else true
        )
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasCameraPerm = checkProfilePerm(context, Manifest.permission.CAMERA)
        hasLocationPerm = checkProfilePerm(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            checkProfilePerm(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        hasActivityPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            checkProfilePerm(context, Manifest.permission.ACTIVITY_RECOGNITION)
        } else true
    }

    if (showEditGoalsDialog) {
        var nameInput by remember(profile.displayName) { mutableStateOf(profile.displayName) }
        var weightInput by remember(profile.weightKg) { mutableStateOf(profile.weightKg.toInt().toString()) }
        var heightInput by remember(settings.userHeightCm) { mutableStateOf(settings.userHeightCm.toInt().toString()) }
        var goalMinutes by remember(profile.dailyGoalMinutes) { mutableIntStateOf(profile.dailyGoalMinutes) }

        AlertDialog(
            onDismissRequest = { showEditGoalsDialog = false },
            title = { Text("ข้อมูลนักกีฬาและเป้าหมาย") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("ชื่อผู้ใช้") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = weightInput,
                            onValueChange = { raw -> weightInput = raw.filter { it.isDigit() || it == '.' }.take(5) },
                            label = { Text("น้ำหนัก (กก.)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = heightInput,
                            onValueChange = { raw -> heightInput = raw.filter { it.isDigit() }.take(3) },
                            label = { Text("ส่วนสูง (ซม.)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = if (goalMinutes <= 0) "เป้าหมาย: โหมดเวลาอิสระ" else "เป้าหมายรายวัน: $goalMinutes นาที/วัน",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = goalMinutes.toFloat(),
                        onValueChange = { goalMinutes = ((it / 5f).roundToInt() * 5).coerceIn(0, 180) },
                        valueRange = 0f..180f
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEditGoalsDialog = false
                        settingsViewModel.updateBodyMetrics(
                            displayName = nameInput,
                            weightKg = weightInput.toFloatOrNull() ?: profile.weightKg,
                            heightCm = heightInput.toFloatOrNull() ?: settings.userHeightCm,
                            dailyGoalMinutes = goalMinutes
                        )
                        MoveWidgetProvider.refreshAllWidgets(context)
                    }
                ) {
                    Text("บันทึก")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditGoalsDialog = false }) { Text("ยกเลิก") }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("รีเซ็ตการตั้งค่าแอป?") },
            text = { Text("คืนค่าเริ่มต้นของการตั้งค่าแอปโดยไม่ลบประวัติการออกกำลังกาย") },
            confirmButton = {
                Button(onClick = {
                    showResetDialog = false
                    settingsViewModel.resetSettingsToDefaults()
                }) { Text("ยืนยัน") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("ยกเลิก") }
            }
        )
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("ออกจากระบบ?") },
            text = { Text("สถิติทั้งหมดถูกซิงก์บน Cloud เรียบร้อยแล้ว สามารถกลับมาเข้าสู่ระบบได้ทุกเมื่อ") },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutDialog = false
                        onSignOutClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralError),
                    modifier = Modifier.testTag("confirm_sign_out_button")
                ) { Text("ออกจากระบบ") }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) { Text("ยกเลิก") }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(ObsidianBlack, DeepForestGreen.copy(alpha = 0.22f), ObsidianBlack)
                )
            ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Athlete Profile Information Hero Card
        item {
            GlassPulseCard(
                highlightGlow = true,
                cornerRadius = 28.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .auroraGlowBackdrop(primaryGlow = EmeraldPulse, secondaryGlow = NeonLime)
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = ObsidianSurface,
                                border = BorderStroke(2.dp, Brush.linearGradient(listOf(EmeraldPulse, NeonLime))),
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (!userPhotoUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = userPhotoUrl,
                                            contentDescription = "รูปโปรไฟล์",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(Icons.Rounded.Person, contentDescription = null, tint = NeonLime, modifier = Modifier.size(32.dp))
                                    }
                                }
                            }

                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = profile.displayName,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!userEmail.isNullOrBlank()) {
                                    Text(
                                        text = userEmail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.72f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Rounded.CloudDone, contentDescription = null, tint = NeonLime, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = "Level ${data.progression.level} • ${data.progression.rankTitleThai}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = NeonLime,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = { showEditGoalsDialog = true },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, EmeraldPulse.copy(alpha = 0.5f)),
                                modifier = Modifier.testTag("settings_edit_body_button")
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = "แก้ไข", tint = NeonLime, modifier = Modifier.size(16.dp))
                            }
                        }

                        LinearProgressIndicator(
                            progress = { data.progression.levelProgressFraction },
                            color = NeonLime,
                            trackColor = Color.White.copy(alpha = 0.14f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(999.dp))
                        )
                    }
                }
            }
        }

        // 2. Fitness Goals Card
        item {
            GlassPulseCard(cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Flag, contentDescription = null, tint = NeonLime)
                            Text("FITNESS GOALS", style = MaterialTheme.typography.labelSmall, color = NeonLime, fontWeight = FontWeight.Bold)
                        }
                        Surface(
                            onClick = { showEditGoalsDialog = true },
                            shape = RoundedCornerShape(50),
                            color = DeepForestGreen.copy(alpha = 0.85f),
                            border = BorderStroke(1.2.dp, NeonLime.copy(alpha = 0.75f))
                        ) {
                            Text(
                                text = "ปรับเป้าหมาย",
                                color = NeonLime,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GoalPillBox(
                            label = "Daily Target",
                            value = if (profile.dailyGoalMinutes <= 0) "อิสระ" else "${profile.dailyGoalMinutes} นาที",
                            modifier = Modifier.weight(1f)
                        )
                        GoalPillBox(
                            label = "Weight",
                            value = "${profile.weightKg.toInt()} กก.",
                            modifier = Modifier.weight(1f)
                        )
                        GoalPillBox(
                            label = "Height",
                            value = "${settings.userHeightCm.toInt()} ซม.",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 3. Achievement Badges Showcase
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = NeonLime, modifier = Modifier.size(20.dp))
                        Text(
                            text = "เหรียญความสำเร็จ (${data.progression.unlockedCount}/${data.progression.badges.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        onClick = onOpenAllBadges,
                        shape = RoundedCornerShape(50),
                        color = EmeraldPulse.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, EmeraldPulse.copy(alpha = 0.6f)),
                        modifier = Modifier.testTag("profile_all_badges_button")
                    ) {
                        Text(
                            text = "ดูทั้งหมด",
                            color = NeonLime,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(data.progression.badges, key = { it.id }) { badge ->
                        BadgeMiniCard(badge = badge, onClick = onOpenAllBadges)
                    }
                }
            }
        }

        // 4. Recent Workout History Preview
        item {
            GlassPulseCard(
                cornerRadius = 24.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceClickable(onClick = onOpenFullHistory)
                    .testTag("profile_history_preview_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.History, contentDescription = null, tint = EmeraldPulse)
                        Column {
                            Text(
                                text = "ประวัติการฝึก (${data.workouts.size} กิจกรรม)",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            val lastWorkout = data.workouts.firstOrNull()
                            Text(
                                text = if (lastWorkout != null) {
                                    "ล่าสุด: ${lastWorkout.sport.thaiName} • ${lastWorkout.caloriesBurned} kcal"
                                } else {
                                    "แตะเพื่อดูสถิติทั้งหมดใน Progress"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = NeonLime)
                }
            }
        }

        // 5. Personal Settings
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "PERSONAL SETTINGS",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldPulse,
                    fontWeight = FontWeight.Bold
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_topic_tabs")
                ) {
                    ProfileSettingsGroup.entries.forEachIndexed { idx, tab ->
                        SegmentedButton(
                            selected = selectedSettingsGroup == tab,
                            onClick = { selectedSettingsGroup = tab },
                            shape = SegmentedButtonDefaults.itemShape(index = idx, count = ProfileSettingsGroup.entries.size),
                            modifier = Modifier.testTag("settings_tab_${tab.name}")
                        ) {
                            Text(tab.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }
                }
            }
        }

        when (selectedSettingsGroup) {
            ProfileSettingsGroup.DISPLAY -> {
                item {
                    GlassPulseCard(cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.DarkMode, contentDescription = null, tint = NeonLime)
                                Text("ธีมและหน้าจอ", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                ThemeMode.entries.forEachIndexed { idx, mode ->
                                    SegmentedButton(
                                        selected = settings.themeMode == mode,
                                        onClick = { settingsViewModel.setThemeMode(mode) },
                                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = ThemeMode.entries.size),
                                        modifier = Modifier.testTag("theme_${mode.name}")
                                    ) {
                                        Text(mode.thaiLabel, maxLines = 1)
                                    }
                                }
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                            ProfileSwitchRow(
                                icon = Icons.Rounded.ScreenLockPortrait,
                                title = "เปิดหน้าจอค้างไว้ระหว่างฝึก",
                                subtitle = "ป้องกันหน้าจอดับเองขณะจับเวลาหรือใช้กล้อง AI",
                                checked = settings.keepScreenOn,
                                onCheckedChange = settingsViewModel::setKeepScreenOn,
                                testTag = "switch_keep_screen_on"
                            )
                        }
                    }
                }
            }

            ProfileSettingsGroup.AI_COACH -> {
                item {
                    GlassPulseCard(cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.Sensors, contentDescription = null, tint = NeonLime)
                                Text("AI & เซนเซอร์การเคลื่อนไหว", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                DetectionSensitivity.entries.forEachIndexed { idx, sens ->
                                    SegmentedButton(
                                        selected = settings.detectionSensitivity == sens,
                                        onClick = { settingsViewModel.setDetectionSensitivity(sens) },
                                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = DetectionSensitivity.entries.size),
                                        modifier = Modifier.testTag("sensitivity_${sens.name}")
                                    ) {
                                        Text(sens.thaiLabel, maxLines = 1)
                                    }
                                }
                            }
                            ProfileSwitchRow(
                                icon = Icons.Rounded.RecordVoiceOver,
                                title = "เสียงโค้ช AI ระหว่างฝึก (Thai TTS)",
                                subtitle = "ขานจำนวนครั้ง เตือนเพซ และพักรอบ HIIT",
                                checked = settings.enableVoiceCoach,
                                onCheckedChange = settingsViewModel::setEnableVoiceCoach,
                                testTag = "switch_voice_coach"
                            )
                            ProfileSwitchRow(
                                icon = Icons.Rounded.PauseCircle,
                                title = "หยุดชั่วคราวอัตโนมัติ (Auto-Pause)",
                                subtitle = "พักเวลาเมื่อหยุดนิ่งเกิน 5 วินาที",
                                checked = settings.autoPauseEnabled,
                                onCheckedChange = settingsViewModel::setAutoPauseEnabled,
                                testTag = "switch_auto_pause"
                            )
                            ProfileSwitchRow(
                                icon = Icons.Rounded.NotificationsActive,
                                title = "แจ้งเตือนเมื่อหลุดเฟรมกล้อง AI",
                                subtitle = "เตือนเมื่อไม่พบท่าทางเกิน 4 วินาที",
                                checked = settings.autoNotifyWhenIdle,
                                onCheckedChange = settingsViewModel::setAutoNotifyWhenIdle,
                                testTag = "switch_auto_notify_idle"
                            )
                            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.AccessibilityNew, contentDescription = null, tint = EmeraldPulse)
                                Text("เวลายืดกล้ามเนื้อ: ${settings.defaultStretchSeconds} วินาที", color = Color.White, style = MaterialTheme.typography.titleSmall)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(15, 20, 30, 45, 60).forEach { sec ->
                                    FilterChip(
                                        selected = settings.defaultStretchSeconds == sec,
                                        onClick = { settingsViewModel.setDefaultStretchSeconds(sec) },
                                        label = { Text("${sec}s") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NeonLime,
                                            selectedLabelColor = ObsidianBlack
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            ProfileSwitchRow(
                                icon = Icons.Rounded.VolumeUp,
                                title = "เสียงสัญญาณเปลี่ยนท่า",
                                subtitle = "แจ้งเตือนเบาๆ เมื่อสลับข้าง/เปลี่ยนท่า",
                                checked = settings.enableStretchBeep,
                                onCheckedChange = settingsViewModel::setEnableStretchBeep,
                                testTag = "switch_stretch_beep"
                            )
                            ProfileSwitchRow(
                                icon = Icons.Rounded.Vibration,
                                title = "สั่นตอบสนองเมื่อจบเซต",
                                subtitle = "สั่นเบาๆ เมื่อครบเวลาหรือสลับข้าง",
                                checked = settings.enableHapticFeedback,
                                onCheckedChange = settingsViewModel::setEnableHapticFeedback,
                                testTag = "switch_haptic_feedback"
                            )
                        }
                    }
                }
            }

            ProfileSettingsGroup.BACKUP -> {
                item {
                    GlassPulseCard(cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.Backup, contentDescription = null, tint = NeonLime)
                                Text("สิทธิ์อุปกรณ์ & สำรองข้อมูล", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = "กล้อง: ${if (hasCameraPerm) "อนุญาตแล้ว" else "ยังไม่อนุญาต"} • GPS: ${if (hasLocationPerm) "อนุญาตแล้ว" else "ยังไม่อนุญาต"} • ก้าวเดิน: ${if (hasActivityPerm) "อนุญาตแล้ว" else "ยังไม่อนุญาต"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.72f)
                            )
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null)
                                    )
                                    context.startActivity(intent)
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("ตั้งค่าสิทธิ์ Android", color = Color.White)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { exportJsonLauncher.launch("move_backup_${System.currentTimeMillis() / 1000}.json") },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldPulse,
                                        contentColor = ObsidianBlack
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .shadow(
                                            elevation = 8.dp,
                                            shape = RoundedCornerShape(16.dp),
                                            ambientColor = NeonLime,
                                            spotColor = EmeraldPulse
                                        )
                                        .testTag("export_json_button")
                                ) {
                                    Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("JSON", maxLines = 1, fontWeight = FontWeight.ExtraBold)
                                }
                                OutlinedButton(
                                    onClick = { exportCsvLauncher.launch("move_workouts_${System.currentTimeMillis() / 1000}.csv") },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f).testTag("export_csv_button")
                                ) {
                                    Icon(Icons.Rounded.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("CSV", maxLines = 1)
                                }
                            }
                            OutlinedButton(
                                onClick = { importJsonLauncher.launch(arrayOf("application/json", "text/*")) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().testTag("import_json_button")
                            ) {
                                Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("นำเข้าไฟล์สำรอง JSON")
                            }
                            OutlinedButton(
                                onClick = { showResetDialog = true },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().testTag("reset_settings_button")
                            ) {
                                Icon(Icons.Rounded.RestartAlt, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("รีเซ็ตการตั้งค่าเริ่มต้น")
                            }
                        }
                    }
                }
            }
        }

        // Sign Out CTA
        item {
            OutlinedButton(
                onClick = { showSignOutDialog = true },
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, CoralError.copy(alpha = 0.55f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, tint = CoralError)
                Spacer(modifier = Modifier.width(8.dp))
                Text("ออกจากระบบ / เปลี่ยนบัญชี", color = CoralError, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun GoalPillBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, EmeraldPulse.copy(alpha = 0.22f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = value, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.ExtraBold)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.65f))
        }
    }
}

@Composable
private fun BadgeMiniCard(
    badge: AchievementBadge,
    onClick: () -> Unit
) {
    GlassPulseCard(
        cornerRadius = 20.dp,
        accentBorder = if (badge.unlocked) NeonLime.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.14f),
        modifier = Modifier
            .width(156.dp)
            .bounceClickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (badge.iconKey) {
                        "STREAK", "STREAK7" -> Icons.Rounded.LocalFireDepartment
                        "BURN" -> Icons.Rounded.Bolt
                        "DISTANCE" -> Icons.Rounded.Route
                        "REPS" -> Icons.Rounded.Repeat
                        else -> Icons.Rounded.MilitaryTech
                    },
                    contentDescription = null,
                    tint = if (badge.unlocked) NeonLime else Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.size(22.dp)
                )
                Icon(
                    imageVector = if (badge.unlocked) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = if (badge.unlocked) EmeraldPulse else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = badge.titleThai,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (badge.unlocked) "Unlocked" else badge.progressLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (badge.unlocked) NeonLime else EmeraldPulse
            )
        }
    }
}

@Composable
private fun ProfileSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = EmeraldPulse, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.Medium)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.65f))
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.testTag(testTag))
    }
}

private fun checkProfilePerm(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
