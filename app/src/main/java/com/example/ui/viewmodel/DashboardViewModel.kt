package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.WorkoutDraftStore
import com.example.data.local.MoveRepository
import com.example.data.model.ActiveWorkoutDraft
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutSession
import com.example.domain.AdaptiveDailyPlan
import com.example.domain.AthleteProgression
import com.example.domain.ChartBarPoint
import com.example.domain.DailyGoalSummary
import com.example.domain.DateStreakUtils
import com.example.domain.MoveAnalyticsEngine
import com.example.domain.MuscleRecoveryReport
import com.example.domain.StreakStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardData(
    val profile: UserProfile,
    val workouts: List<WorkoutSession>,
    val dailyGoal: DailyGoalSummary,
    val streak: StreakStatus,
    val muscleRecovery: MuscleRecoveryReport,
    val adaptivePlan: AdaptiveDailyPlan,
    val progression: AthleteProgression,
    val last7DaysBars: List<ChartBarPoint>,
    val last4WeeksBars: List<ChartBarPoint>,
    val pendingDraft: ActiveWorkoutDraft?
)

class DashboardViewModel(
    private val repository: MoveRepository,
    private val draftStore: WorkoutDraftStore
) : ViewModel() {

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    val uiState: StateFlow<UiState<DashboardData>> = combine(
        repository.observeUserProfile(),
        repository.observeWorkouts(),
        draftStore.observeDraft(UserProfile.LOCAL_PROFILE_ID)
    ) { profileOrNull, workouts, draft ->
        val safeProfile = profileOrNull ?: UserProfile()
        val streak = DateStreakUtils.calculateStreak(
            workouts = workouts,
            storedLongestStreak = safeProfile.longestStreak
        )
        val dailyGoal = DateStreakUtils.calculateDailyGoalSummary(
            workouts = workouts,
            goalMinutes = safeProfile.dailyGoalMinutes
        )
        val muscleRecovery = MoveAnalyticsEngine.calculateMuscleRecovery(workouts)
        val adaptivePlan = MoveAnalyticsEngine.buildAdaptiveDailyPlan(muscleRecovery, workouts)
        val progression = MoveAnalyticsEngine.buildAthleteProgression(workouts, streak)
        UiState.Success(
            DashboardData(
                profile = safeProfile,
                workouts = workouts,
                dailyGoal = dailyGoal,
                streak = streak,
                muscleRecovery = muscleRecovery,
                adaptivePlan = adaptivePlan,
                progression = progression,
                last7DaysBars = DateStreakUtils.buildLast7DaysBars(workouts),
                last4WeeksBars = DateStreakUtils.buildLast4WeeksBars(workouts),
                pendingDraft = draft
            )
        ) as UiState<DashboardData>
    }
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Error observing MOVE local database", error)
            emit(UiState.Error(error.localizedMessage ?: "ไม่สามารถโหลดข้อมูลกิจกรรมได้"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    init {
        viewModelScope.launch {
            repository.ensureUserProfile()
        }
    }

    fun updateProfile(name: String, weightKg: Float) {
        viewModelScope.launch {
            repository.updateProfile(name, weightKg)
                .onSuccess { _actionMessage.value = "บันทึกข้อมูลโปรไฟล์แล้ว" }
                .onFailure { err ->
                    _actionMessage.value = "อัปเดตโปรไฟล์ไม่สำเร็จ: ${err.localizedMessage ?: "ลองใหม่อีกครั้ง"}"
                }
        }
    }

    fun updateDailyGoal(minutes: Int) {
        viewModelScope.launch {
            repository.updateDailyGoal(minutes)
                .onSuccess {
                    _actionMessage.value = if (minutes <= 0) {
                        "เปลี่ยนเป็นโหมดเวลาอิสระแล้ว"
                    } else {
                        "บันทึกเป้าหมายรายวัน $minutes นาทีแล้ว"
                    }
                }
                .onFailure { err ->
                    _actionMessage.value = "บันทึกเป้าหมายไม่สำเร็จ: ${err.localizedMessage ?: "ลองใหม่อีกครั้ง"}"
                }
        }
    }

    fun deleteWorkout(workoutId: String) {
        viewModelScope.launch {
            repository.deleteWorkout(workoutId)
                .onSuccess { _actionMessage.value = "ลบรายการออกกำลังกายแล้ว" }
                .onFailure { err ->
                    _actionMessage.value = "ลบรายการไม่สำเร็จ: ${err.localizedMessage ?: "ลองใหม่อีกครั้ง"}"
                }
        }
    }

    fun discardPendingDraft() {
        viewModelScope.launch {
            draftStore.clearDraft()
            _actionMessage.value = "ยกเลิกกิจกรรมที่ค้างอยู่แล้ว"
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    private companion object {
        const val TAG = "DashboardVM"
    }
}
