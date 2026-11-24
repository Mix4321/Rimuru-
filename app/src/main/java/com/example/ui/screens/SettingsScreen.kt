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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.DetectionSensitivity
import com.example.data.local.ThemeMode
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldVibrant
import com.example.ui.theme.ForestGreen
import com.example.ui.viewmodel.SettingsViewModel
import com.example.widget.MoveWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

private enum class SettingsTopicTab(val label: String) {
    ACCOUNT_THEME("บัญชี & ธีม"),
    AI_STRETCH("AI & ยืดเหยียด"),
    BACKUP_PERMS("สำรอง & สิทธิ์")
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    userEmail: String? = null,
    userPhotoUrl: String? = null,
    onSignOutClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val totalWorkoutsCount by viewModel.totalWorkoutsCount.collectAsStateWithLifecycle()

    var selectedTopic by remember { mutableStateOf(SettingsTopicTab.ACCOUNT_THEME) }
    var showBodyMetricsDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showSignOutConfirmDialog by remember { mutableStateOf(false) }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        val content = viewModel.buildJsonBackup()
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            stream.write(content.toByteArray(Charsets.UTF_8))
                        } ?: error("ไม่สามารถเปิดไฟล์ปลายทางได้")
                    }.isSuccess
                }
                viewModel.notifyMessage(
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
                        val csv = viewModel.buildCsvExport()
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            stream.write(csv.toByteArray(Charsets.UTF_8))
                        } ?: error("ไม่สามารถเปิดไฟล์ปลายทางได้")
                    }.isSuccess
                }
                viewModel.notifyMessage(
                    if (ok) "ส่งออกไฟล์ CSV (เปิดใน Excel ได้ทันที) เรียบร้อยแล้ว" else "ส่งออกไฟล์ CSV ไม่สำเร็จ"
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
                        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                            it.readText()
                        }
                    }.getOrNull()
                }
                if (raw.isNullOrBlank()) {
                    viewModel.notifyMessage("ไม่สามารถอ่านไฟล์ที่เลือกได้")
                } else {
                    viewModel.importBackupJson(raw) {
                        MoveWidgetProvider.refreshAllWidgets(context)
                    }
                }
            }
        }
    }

    var hasCameraPerm by remember { mutableStateOf(checkPerm(context, Manifest.permission.CAMERA)) }
    var hasLocationPerm by remember { mutableStateOf(checkPerm(context, Manifest.permission.ACCESS_FINE_LOCATION)) }
    var hasActivityPerm by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                checkPerm(context, Manifest.permission.ACTIVITY_RECOGNITION)
            } else {
                true
            }
        )
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasCameraPerm = checkPerm(context, Manifest.permission.CAMERA)
        hasLocationPerm = checkPerm(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            checkPerm(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        hasActivityPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            checkPerm(context, Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            true
        }
    }

    if (showBodyMetricsDialog) {
        var nameInput by remember(profile.displayName) { mutableStateOf(profile.displayName) }
        var weightInput by remember(profile.weightKg) { mutableStateOf(profile.weightKg.toInt().toString()) }
        var heightInput by remember(settings.userHeightCm) { mutableStateOf(settings.userHeightCm.toInt().toString()) }
        var goalMinutes by remember(profile.dailyGoalMinutes) { mutableIntStateOf(profile.dailyGoalMinutes) }

        AlertDialog(
            onDismissRequest = { showBodyMetricsDialog = false },
            title = { Text("ข้อมูลร่างกายและเป้าหมาย") },
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
                        text = if (goalMinutes <= 0) {
                            "รูปแบบเป้าหมาย: โหมดเวลาอิสระ (ไม่กำหนดขั้นต่ำ)"
                        } else {
                            "เป้าหมายรายวัน: $goalMinutes นาที/วัน"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = goalMinutes == 0,
                            onClick = { goalMinutes = 0 },
                            label = { Text("เวลาอิสระ") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                        listOf(20, 45, 60).forEach { min ->
                            FilterChip(
                                selected = goalMinutes == min,
                                onClick = { goalMinutes = min },
                                label = { Text("${min}น.") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
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
                        showBodyMetricsDialog = false
                        viewModel.updateBodyMetrics(
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
                TextButton(onClick = { showBodyMetricsDialog = false }) {
                    Text("ยกเลิก")
                }
            }
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("รีเซ็ตการตั้งค่าแอป?") },
            text = {
                Text("การตั้งค่าธีม เวลาเตรียมยืด ความไว AI และเสียงแจ้งเตือนจะกลับเป็นค่าเริ่มต้น (ข้อมูลประวัติการออกกำลังกายของคุณจะไม่ถูกลบ)")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        viewModel.resetSettingsToDefaults()
                    }
                ) {
                    Text("ยืนยันรีเซ็ต")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("ยกเลิก")
                }
            }
        )
    }

    if (showSignOutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirmDialog = false },
            title = { Text("ออกจากระบบ?") },
            text = {
                Text("ข้อมูลทั้งหมดของคุณถูกซิงก์ไว้บน Cloud แล้ว คุณสามารถกลับมาเข้าสู่ระบบได้ทุกเมื่อ")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutConfirmDialog = false
                        onSignOutClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralError),
                    modifier = Modifier.testTag("confirm_sign_out_button")
                ) {
                    Text("ออกจากระบบ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirmDialog = false }) {
                    Text("ยกเลิก")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "ตั้งค่าแอป MOVE",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_topic_tabs")
                ) {
                    SettingsTopicTab.entries.forEachIndexed { idx, tab ->
                        SegmentedButton(
                            selected = selectedTopic == tab,
                            onClick = { selectedTopic = tab },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = idx,
                                count = SettingsTopicTab.entries.size
                            ),
                            modifier = Modifier.testTag("settings_tab_${tab.name}")
                        ) {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        when (selectedTopic) {
            SettingsTopicTab.ACCOUNT_THEME -> {
                // Google Account + Sign Out Card
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!userPhotoUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = userPhotoUrl,
                                            contentDescription = "รูปบัญชีผู้ใช้",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (!userEmail.isNullOrBlank()) {
                                        Text(
                                            text = userEmail,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CloudDone,
                                            contentDescription = null,
                                            tint = EmeraldVibrant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "ซิงก์ข้อมูลบนคลาวด์อัตโนมัติ",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EmeraldVibrant,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = { showSignOutConfirmDialog = true },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("sign_out_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                                    contentDescription = null,
                                    tint = CoralError
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ออกจากระบบ / เปลี่ยนบัญชี", color = CoralError, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Body Calibration & Profile Card
                item {
                    SettingsSectionCard(title = "ข้อมูลร่างกายและเป้าหมายรายวัน", icon = Icons.Rounded.Person) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "${profile.weightKg.toInt()} กก. • ${settings.userHeightCm.toInt()} ซม.",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                val goalDesc = if (profile.dailyGoalMinutes <= 0) {
                                    "โหมดเวลาอิสระ"
                                } else {
                                    "เป้าหมาย ${profile.dailyGoalMinutes} นาที/วัน"
                                }
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "ระยะก้าวเดิน %.2f ม. • ก้าววิ่ง %.2f ม. • %s",
                                        settings.walkStrideMeters,
                                        settings.runStrideMeters,
                                        goalDesc
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = { showBodyMetricsDialog = true },
                                modifier = Modifier.testTag("settings_edit_body_button")
                            ) {
                                Text("แก้ไข")
                            }
                        }
                    }
                }

                // Display & Theme Settings
                item {
                    SettingsSectionCard(title = "ธีมและหน้าจอ", icon = Icons.Rounded.DarkMode) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                ThemeMode.entries.forEachIndexed { idx, mode ->
                                    SegmentedButton(
                                        selected = settings.themeMode == mode,
                                        onClick = { viewModel.setThemeMode(mode) },
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index = idx,
                                            count = ThemeMode.entries.size
                                        ),
                                        modifier = Modifier.testTag("theme_${mode.name}")
                                    ) {
                                        Text(mode.thaiLabel, maxLines = 1)
                                    }
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            SettingsSwitchRow(
                                icon = Icons.Rounded.ScreenLockPortrait,
                                title = "เปิดหน้าจอค้างไว้ขณะยืดและออกกำลังกาย",
                                subtitle = "ป้องกันหน้าจอดับเองระหว่างตั้งกล้อง AI หรือจับเวลา",
                                checked = settings.keepScreenOn,
                                onCheckedChange = viewModel::setKeepScreenOn,
                                testTag = "switch_keep_screen_on"
                            )
                        }
                    }
                }
            }

            SettingsTopicTab.AI_STRETCH -> {
                // Sport Detection Precision Card
                item {
                    SettingsSectionCard(title = "ความแม่นยำการตรวจจับกีฬา (AI & เซนเซอร์)", icon = Icons.Rounded.Sensors) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "ระดับความเข้มงวดในการกรองสัญญาณ",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                DetectionSensitivity.entries.forEachIndexed { idx, sens ->
                                    SegmentedButton(
                                        selected = settings.detectionSensitivity == sens,
                                        onClick = { viewModel.setDetectionSensitivity(sens) },
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index = idx,
                                            count = DetectionSensitivity.entries.size
                                        ),
                                        modifier = Modifier.testTag("sensitivity_${sens.name}")
                                    ) {
                                        Text(sens.thaiLabel, maxLines = 1)
                                    }
                                }
                            }
                            Text(
                                text = settings.detectionSensitivity.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            SettingsSwitchRow(
                                icon = Icons.Rounded.NotificationsActive,
                                title = "แจ้งเตือนเมื่อหยุดนิ่งหรือหลุดเฟรมกล้อง",
                                subtitle = "เตือนเมื่อไม่มีการเคลื่อนไหวเกิน 4 วินาทีระหว่างฝึก",
                                checked = settings.autoNotifyWhenIdle,
                                onCheckedChange = viewModel::setAutoNotifyWhenIdle,
                                testTag = "switch_auto_notify_idle"
                            )

                            SettingsSwitchRow(
                                icon = Icons.Rounded.RecordVoiceOver,
                                title = "เสียงโค้ช AI ระหว่างออกกำลังกาย (Thai TTS)",
                                subtitle = "ขานจำนวนครั้งครบทุก 5 ครั้ง เตือนเพซทุก 1 กม. และแจ้งช่วงพัก HIIT",
                                checked = settings.enableVoiceCoach,
                                onCheckedChange = viewModel::setEnableVoiceCoach,
                                testTag = "switch_voice_coach"
                            )

                            SettingsSwitchRow(
                                icon = Icons.Rounded.PauseCircle,
                                title = "หยุดจับเวลาอัตโนมัติเมื่อหยุดนิ่ง (Auto-Pause)",
                                subtitle = "พักจับเวลาอัตโนมัติเมื่อติดไฟแดงหรือหยุดเดิน/วิ่งเกิน 5 วินาที และนับต่อเมื่อออกตัว",
                                checked = settings.autoPauseEnabled,
                                onCheckedChange = viewModel::setAutoPauseEnabled,
                                testTag = "switch_auto_pause"
                            )
                        }
                    }
                }

                // Pre-Workout Stretching Preferences
                item {
                    SettingsSectionCard(title = "ระบบยืดกล้ามเนื้อก่อนออกกำลังกาย", icon = Icons.Rounded.AccessibilityNew) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "เวลาเริ่มต้นต่อท่า: ${settings.defaultStretchSeconds} วินาที",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(15, 20, 30, 45, 60).forEach { sec ->
                                    FilterChip(
                                        selected = settings.defaultStretchSeconds == sec,
                                        onClick = { viewModel.setDefaultStretchSeconds(sec) },
                                        label = { Text("${sec}วิ") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            SettingsSwitchRow(
                                icon = Icons.Rounded.VolumeUp,
                                title = "เสียงสัญญาณแจ้งเปลี่ยนท่าและสลับข้าง",
                                subtitle = "ส่งเสียงติ๊งเบาๆ เมื่อถึงครึ่งเวลาและเมื่อเริ่มท่าถัดไป",
                                checked = settings.enableStretchBeep,
                                onCheckedChange = viewModel::setEnableStretchBeep,
                                testTag = "switch_stretch_beep"
                            )

                            SettingsSwitchRow(
                                icon = Icons.Rounded.Vibration,
                                title = "สั่นตอบสนองเมื่อเปลี่ยนท่า / จบเซต",
                                subtitle = "ช่วยให้รู้จังหวะสลับข้างโดยไม่ต้องมองหน้าจอตลอดเวลา",
                                checked = settings.enableHapticFeedback,
                                onCheckedChange = viewModel::setEnableHapticFeedback,
                                testTag = "switch_haptic_feedback"
                            )
                        }
                    }
                }
            }

            SettingsTopicTab.BACKUP_PERMS -> {
                // Device Permissions Check
                item {
                    SettingsSectionCard(title = "สถานะสิทธิ์อุปกรณ์ (กล้อง / GPS / ก้าวเดิน)", icon = Icons.Rounded.Tune) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            PermissionStatusRow(
                                icon = Icons.Rounded.CameraAlt,
                                label = "สิทธิ์กล้อง (โหมด AI ตรวจจับท่าทาง)",
                                granted = hasCameraPerm
                            )
                            PermissionStatusRow(
                                icon = Icons.Rounded.GpsFixed,
                                label = "สิทธิ์ตำแหน่ง GPS (วัดระยะทาง/ความเร็วจริง)",
                                granted = hasLocationPerm
                            )
                            PermissionStatusRow(
                                icon = Icons.Rounded.FitnessCenter,
                                label = "สิทธิ์เซนเซอร์ก้าวเดิน (นับก้าวฮาร์ดแวร์)",
                                granted = hasActivityPerm
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
                                Text("จัดการสิทธิ์แอปในการตั้งค่า Android")
                            }
                        }
                    }
                }

                // Backup & Export (SAF Zero-Permission)
                item {
                    SettingsSectionCard(title = "สำรองและส่งออกข้อมูล ($totalWorkoutsCount กิจกรรม)", icon = Icons.Rounded.Backup) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "สำรองประวัติกิจกรรมทั้งหมดเป็นไฟล์ JSON เพื่อย้ายเครื่อง หรือส่งออก CSV ไปเปิดใน Excel / Google Sheets",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { exportJsonLauncher.launch("move_backup_${System.currentTimeMillis() / 1000}.json") },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("export_json_button")
                                ) {
                                    Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("สำรอง JSON", maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = { exportCsvLauncher.launch("move_workouts_${System.currentTimeMillis() / 1000}.csv") },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("export_csv_button")
                                ) {
                                    Icon(Icons.Rounded.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("ส่งออก CSV", maxLines = 1)
                                }
                            }
                            OutlinedButton(
                                onClick = { importJsonLauncher.launch(arrayOf("application/json", "text/*")) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("import_json_button")
                            ) {
                                Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("นำเข้าและกู้คืนข้อมูลจากไฟล์ JSON")
                            }
                        }
                    }
                }

                // Reset Settings
                item {
                    OutlinedButton(
                        onClick = { showResetConfirmDialog = true },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("reset_settings_button")
                    ) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("รีเซ็ตการตั้งค่าแอปเป็นค่าเริ่มต้น")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
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
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun PermissionStatusRow(
    icon: ImageVector,
    label: String,
    granted: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (granted) EmeraldVibrant.copy(alpha = 0.14f) else AmberWarm.copy(alpha = 0.15f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (granted) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = EmeraldVibrant,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = if (granted) "อนุญาตแล้ว" else "ยังไม่อนุญาต",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (granted) EmeraldVibrant else AmberWarm
                )
            }
        }
    }
}

private fun checkPerm(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
