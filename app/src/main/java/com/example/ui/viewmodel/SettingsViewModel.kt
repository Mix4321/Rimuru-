package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.backup.MoveBackupManager
import com.example.data.firebase.MoveCloudRepository
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
    private val repository: MoveRepository,
    private val cloudRepository: MoveCloudRepository? = null,
    private val currentUserId: String = ""
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

    val totalWorkoutsCount: StateFlow<Int> = repository.observeWorkouts()
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = 0
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

    fun setEnableVoiceCoach(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.updateSettings { it.copy(enableVoiceCoach = enabled) }
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
            val safeGoal = dailyGoalMinutes.coerceIn(0, 240)
            repository.updateProfile(displayName, safeWeight)
            repository.updateDailyGoal(safeGoal)
            settingsStore.updateSettings { it.copy(userHeightCm = safeHeight) }
            if (cloudRepository != null && currentUserId.isNotBlank()) {
                val updated = repository.getUserProfileOnce()
                cloudRepository.registerOrUpdateProfile(
                    userId = currentUserId,
                    displayName = updated.displayName,
                    email = null,
                    weightKg = safeWeight,
                    heightCm = safeHeight,
                    dailyGoalMinutes = safeGoal,
                    currentStreak = updated.currentStreak,
                    longestStreak = updated.longestStreak,
                    lastActiveDate = updated.lastActiveDate,
                    onboardingCompleted = true
                )
            }
            _statusMessage.value = "บันทึกข้อมูลร่างกายและการตั้งค่าแล้ว"
        }
    }

    suspend fun buildJsonBackup(): String {
        val currentProfile = repository.getUserProfileOnce()
        val workouts = repository.getAllWorkoutsOnce()
        return MoveBackupManager.exportToJson(currentProfile, workouts)
    }

    suspend fun buildCsvExport(): String {
        val workouts = repository.getAllWorkoutsOnce()
        return MoveBackupManager.exportToCsv(workouts)
    }

    fun importBackupJson(rawJson: String, onCompleted: () -> Unit = {}) {
        viewModelScope.launch {
            val parsed = MoveBackupManager.parseBackupJson(rawJson)
            parsed.onSuccess { payload ->
                repository.importWorkoutsFromBackup(payload.workouts, payload.profile)
                    .onSuccess { importedCount ->
                        if (cloudRepository != null && currentUserId.isNotBlank()) {
                            for (session in payload.workouts) {
                                if (session.durationSeconds >= 5) {
                                    cloudRepository.saveWorkout(currentUserId, session)
                                }
                            }
                            val updatedProfile = repository.getUserProfileOnce()
                            cloudRepository.registerOrUpdateProfile(
                                userId = currentUserId,
                                displayName = updatedProfile.displayName,
                                email = null,
                                weightKg = updatedProfile.weightKg,
                                heightCm = null,
                                dailyGoalMinutes = updatedProfile.dailyGoalMinutes,
                                currentStreak = updatedProfile.currentStreak,
                                longestStreak = updatedProfile.longestStreak,
                                lastActiveDate = updatedProfile.lastActiveDate,
                                onboardingCompleted = true
                            )
                        }
                        _statusMessage.value = "นำเข้าข้อมูลสำเร็จ ($importedCount รายการใหม่)"
                        onCompleted()
                    }
                    .onFailure { err ->
                        _statusMessage.value = "นำเข้าข้อมูลไม่สำเร็จ: ${err.localizedMessage ?: "ไฟล์ไม่ถูกต้อง"}"
                    }
            }.onFailure {
                _statusMessage.value = "รูปแบบไฟล์สำรอง JSON ไม่ถูกต้อง"
            }
        }
    }

    fun notifyMessage(message: String) {
        _statusMessage.value = message
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
