package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore by preferencesDataStore(name = "move_app_settings")

enum class ThemeMode(val thaiLabel: String) {
    SYSTEM("ตามระบบ"),
    LIGHT("โหมดสว่าง"),
    DARK("โหมดมืด");

    companion object {
        fun fromNameSafe(raw: String?): ThemeMode =
            entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}

enum class DetectionSensitivity(
    val thaiLabel: String,
    val description: String,
    val coreConfidence: Float,
    val limbConfidence: Float,
    val motionDeadzoneScale: Float,
    val gpsMaxAccuracyMeters: Float
) {
    STRICT(
        thaiLabel = "เข้มงวดสูงสุด",
        description = "กรองสัญญาณสั่นและจุดหลอนเข้มข้น ลดการนับพลาดเกือบเป็นศูนย์",
        coreConfidence = 0.72f,
        limbConfidence = 0.64f,
        motionDeadzoneScale = 1.18f,
        gpsMaxAccuracyMeters = 20f
    ),
    BALANCED(
        thaiLabel = "สมดุล (แนะนำ)",
        description = "แม่นยำสูงและตอบสนองลื่นไหลสำหรับกีฬาทุกประเภท",
        coreConfidence = 0.65f,
        limbConfidence = 0.56f,
        motionDeadzoneScale = 1.0f,
        gpsMaxAccuracyMeters = 26f
    ),
    SENSITIVE(
        thaiLabel = "ไวต่อการเคลื่อนไหว",
        description = "เหมาะกับสภาพแสงน้อยหรือท่ายืด/เคลื่อนไหวเบาๆ",
        coreConfidence = 0.58f,
        limbConfidence = 0.50f,
        motionDeadzoneScale = 0.85f,
        gpsMaxAccuracyMeters = 32f
    );

    companion object {
        fun fromNameSafe(raw: String?): DetectionSensitivity =
            entries.firstOrNull { it.name == raw } ?: BALANCED
    }
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultStretchSeconds: Int = 30,
    val enableStretchBeep: Boolean = true,
    val enableHapticFeedback: Boolean = true,
    val keepScreenOn: Boolean = true,
    val detectionSensitivity: DetectionSensitivity = DetectionSensitivity.BALANCED,
    val userHeightCm: Float = 170f,
    val autoNotifyWhenIdle: Boolean = true
) {
    val walkStrideMeters: Double get() = (userHeightCm / 100.0) * 0.415
    val runStrideMeters: Double get() = (userHeightCm / 100.0) * 0.585
}

class SettingsStore(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val STRETCH_SECONDS = intPreferencesKey("default_stretch_seconds")
        val STRETCH_BEEP = booleanPreferencesKey("enable_stretch_beep")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("enable_haptic_feedback")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val SENSITIVITY = stringPreferencesKey("detection_sensitivity")
        val HEIGHT_CM = floatPreferencesKey("user_height_cm")
        val AUTO_NOTIFY_IDLE = booleanPreferencesKey("auto_notify_idle")
    }

    val settingsFlow: Flow<AppSettings> = context.appSettingsDataStore.data.map { prefs ->
        AppSettings(
            themeMode = ThemeMode.fromNameSafe(prefs[Keys.THEME_MODE]),
            defaultStretchSeconds = (prefs[Keys.STRETCH_SECONDS] ?: 30).coerceIn(10, 90),
            enableStretchBeep = prefs[Keys.STRETCH_BEEP] ?: true,
            enableHapticFeedback = prefs[Keys.HAPTIC_FEEDBACK] ?: true,
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true,
            detectionSensitivity = DetectionSensitivity.fromNameSafe(prefs[Keys.SENSITIVITY]),
            userHeightCm = (prefs[Keys.HEIGHT_CM] ?: 170f).coerceIn(120f, 220f),
            autoNotifyWhenIdle = prefs[Keys.AUTO_NOTIFY_IDLE] ?: true
        )
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        context.appSettingsDataStore.edit { prefs ->
            val current = AppSettings(
                themeMode = ThemeMode.fromNameSafe(prefs[Keys.THEME_MODE]),
                defaultStretchSeconds = (prefs[Keys.STRETCH_SECONDS] ?: 30).coerceIn(10, 90),
                enableStretchBeep = prefs[Keys.STRETCH_BEEP] ?: true,
                enableHapticFeedback = prefs[Keys.HAPTIC_FEEDBACK] ?: true,
                keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true,
                detectionSensitivity = DetectionSensitivity.fromNameSafe(prefs[Keys.SENSITIVITY]),
                userHeightCm = (prefs[Keys.HEIGHT_CM] ?: 170f).coerceIn(120f, 220f),
                autoNotifyWhenIdle = prefs[Keys.AUTO_NOTIFY_IDLE] ?: true
            )
            val updated = transform(current)
            prefs[Keys.THEME_MODE] = updated.themeMode.name
            prefs[Keys.STRETCH_SECONDS] = updated.defaultStretchSeconds.coerceIn(10, 90)
            prefs[Keys.STRETCH_BEEP] = updated.enableStretchBeep
            prefs[Keys.HAPTIC_FEEDBACK] = updated.enableHapticFeedback
            prefs[Keys.KEEP_SCREEN_ON] = updated.keepScreenOn
            prefs[Keys.SENSITIVITY] = updated.detectionSensitivity.name
            prefs[Keys.HEIGHT_CM] = updated.userHeightCm.coerceIn(120f, 220f)
            prefs[Keys.AUTO_NOTIFY_IDLE] = updated.autoNotifyWhenIdle
        }
    }

    suspend fun resetToDefaults() {
        context.appSettingsDataStore.edit { it.clear() }
    }
}
