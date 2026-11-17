package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppSettings
import com.example.data.local.DetectionSensitivity
import com.example.data.local.MoveRepository
import com.example.data.local.SettingsStore
import com.example.data.local.ThemeMode
import com.example.data.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val repository: MoveRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsStore.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = AppSettings()
        )

    val profile: StateFlow<UserProfile> = repository.observeUserProfile()
        .map { it ?: UserProfile() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = UserProfile()
        )

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsStore.updateSettings { it.copy(themeMode = mode) }
        }
    }

    fun setDefaultStretchSeconds(seconds: Int) {
        viewModelScope.launch {
            settingsStore.updateSettings { it.copy(defaultStretchSeconds = seconds.coerceIn(10, 90)) }
        }
    }

    fun setEnableStretchBeep(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.updateSettings { it.copy(enableStretchBeep = enabled) }
        }
    }

    fun setEnableHapticFeedback(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.updateSettings { it.copy(enableHapticFeedback = enabled) }
        }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.updateSettings { it.copy(keepScreenOn = enabled) }
        }
    }

    fun setAutoNotifyWhenIdle(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.updateSettings { it.copy(autoNotifyWhenIdle = enabled) }
        }
    }

    fun setDetectionSensitivity(sensitivity: DetectionSensitivity) {
        viewModelScope.launch {
            settingsStore.updateSettings { it.copy(detectionSensitivity = sensitivity) }
        }
    }

    fun updateBodyMetrics(
        displayName: String,
        weightKg: Float,
        heightCm: Float,
        dailyGoalMinutes: Int
    ) {
        viewModelScope.launch {
            val safeWeight = weightKg.coerceIn(25f, 250f)
            val safeHeight = heightCm.coerceIn(120f, 220f)
            val safeGoal = dailyGoalMinutes.coerceIn(15, 240)
            repository.updateProfile(displayName, safeWeight)
            repository.updateDailyGoal(safeGoal)
            settingsStore.updateSettings { it.copy(userHeightCm = safeHeight) }
            _statusMessage.value = "บันทึกข้อมูลร่างกายและการตั้งค่าแล้ว"
        }
    }

    fun resetSettingsToDefaults() {
        viewModelScope.launch {
            settingsStore.resetToDefaults()
            _statusMessage.value = "รีเซ็ตการตั้งค่าแอปเป็นค่าเริ่มต้นแล้ว"
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
