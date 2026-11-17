package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.camera.PoseSkeletonSnapshot
import com.example.data.firebase.WorkoutDraftStore
import com.example.data.local.MoveRepository
import com.example.data.local.SaveWorkoutOutcome
import com.example.data.model.ActiveWorkoutDraft
import com.example.data.model.SportType
import com.example.data.model.TrackingMode
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutSession
import com.example.domain.DateStreakUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

enum class ManualEffortLevel(val thaiLabel: String, val multiplier: Double) {
    LIGHT("เบา", 0.8),
    MODERATE("ปานกลาง", 1.0),
    INTENSE("เข้มข้น", 1.25)
}

data class WorkoutUiState(
    val sessionId: String = "",
    val sportType: SportType = SportType.RUNNING,
    val startedAtMillis: Long = 0L,
    val elapsedSeconds: Int = 0,
    val motionActiveSeconds: Int = 0,
    val steps: Int = 0,
    val distanceMeters: Int = 0,
    val caloriesExact: Double = 0.0,
    val isRunning: Boolean = false,
    val hasStartedOnce: Boolean = false,
    val trackingMode: TrackingMode = TrackingMode.CAMERA_AI,
    val cameraPermissionGranted: Boolean = false,
    val cameraPermissionAsked: Boolean = false,
    val useFrontCamera: Boolean = true,
    val poseSnapshot: PoseSkeletonSnapshot = PoseSkeletonSnapshot(),
    val isCurrentlyMoving: Boolean = false,
    val showAdjustCameraTip: Boolean = false,
    val manualEffort: ManualEffortLevel = ManualEffortLevel.MODERATE,
    val isSaving: Boolean = false,
    val saveOutcome: SaveWorkoutOutcome? = null,
    val savedSession: WorkoutSession? = null,
    val errorMessage: String? = null
) {
    val caloriesBurned: Int get() = caloriesExact.roundToInt().coerceAtLeast(if (elapsedSeconds > 0) 1 else 0)
    val canSaveWorkout: Boolean get() = elapsedSeconds >= DateStreakUtils.MIN_WORKOUT_SECONDS
    val remainingToMinSeconds: Int get() = (DateStreakUtils.MIN_WORKOUT_SECONDS - elapsedSeconds).coerceAtLeast(0)
    val minRequirementProgress: Float
        get() = (elapsedSeconds.toFloat() / DateStreakUtils.MIN_WORKOUT_SECONDS.toFloat()).coerceIn(0f, 1f)
}

class WorkoutViewModel(
    private val repository: MoveRepository,
    private val draftStore: WorkoutDraftStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkoutUiState())
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var lastMotionSeenMillis: Long = 0L
    private var lastStepDeltaBuffer: Double = 0.0
    private var lastDistDeltaBuffer: Double = 0.0
    private var ticksSinceDraftSave: Int = 0

    fun startFreshSession(sportType: SportType) {
        timerJob?.cancel()
        val now = System.currentTimeMillis()
        lastMotionSeenMillis = now
        lastStepDeltaBuffer = 0.0
        lastDistDeltaBuffer = 0.0
        _uiState.value = WorkoutUiState(
            sessionId = UUID.randomUUID().toString(),
            sportType = sportType,
            startedAtMillis = now,
            isRunning = false,
            hasStartedOnce = false,
            trackingMode = TrackingMode.CAMERA_AI
        )
    }

    fun restoreFromDraft(draft: ActiveWorkoutDraft) {
        timerJob?.cancel()
        val now = System.currentTimeMillis()
        lastMotionSeenMillis = now
        lastStepDeltaBuffer = 0.0
        lastDistDeltaBuffer = 0.0

        val bonusSeconds = if (draft.isRunning) {
            ((now - draft.lastSavedEpochMillis) / 1000L).toInt().coerceIn(0, 1800)
        } else {
            0
        }

        val sport = draft.sportType
        val restoredElapsed = draft.elapsedSeconds + bonusSeconds
        val extraCal = (sport.kcalPerMinute / 60.0) * bonusSeconds
        val extraSteps = if (sport.supportsSteps) ((sport.stepsPerMinute / 60.0) * bonusSeconds).roundToInt() else 0
        val extraDist = if (sport.supportsDistance) ((sport.metersPerMinute / 60.0) * bonusSeconds).roundToInt() else 0

        _uiState.value = WorkoutUiState(
            sessionId = draft.sessionId,
            sportType = sport,
            startedAtMillis = draft.startedAtMillis,
            elapsedSeconds = restoredElapsed,
            motionActiveSeconds = draft.motionActiveSeconds,
            steps = draft.steps + extraSteps,
            distanceMeters = draft.distanceMeters + extraDist,
            caloriesExact = draft.caloriesExact + extraCal,
            isRunning = false,
            hasStartedOnce = true,
            trackingMode = TrackingMode.CAMERA_AI
        )
    }

    fun onCameraPermissionResult(granted: Boolean) {
        _uiState.update { state ->
            state.copy(
                cameraPermissionGranted = granted,
                cameraPermissionAsked = true,
                trackingMode = if (granted) TrackingMode.CAMERA_AI else TrackingMode.MANUAL,
                showAdjustCameraTip = false
            )
        }
        if (!_uiState.value.hasStartedOnce) {
            startOrResumeTimer()
        }
    }

    fun switchTrackingMode(mode: TrackingMode) {
        _uiState.update { state ->
            val effectiveMode = if (mode == TrackingMode.CAMERA_AI && !state.cameraPermissionGranted) {
                TrackingMode.MANUAL
            } else {
                mode
            }
            state.copy(
                trackingMode = effectiveMode,
                showAdjustCameraTip = false
            )
        }
    }

    fun toggleCameraLens() {
        _uiState.update { it.copy(useFrontCamera = !it.useFrontCamera) }
    }

    fun setManualEffort(level: ManualEffortLevel) {
        _uiState.update { it.copy(manualEffort = level) }
    }

    fun startOrResumeTimer() {
        if (_uiState.value.isRunning) return
        lastMotionSeenMillis = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                isRunning = true,
                hasStartedOnce = true,
                startedAtMillis = if (it.startedAtMillis == 0L) System.currentTimeMillis() else it.startedAtMillis,
                errorMessage = null
            )
        }
        persistDraftAsync()
        startTickerLoop()
    }

    fun pauseTimer() {
        timerJob?.cancel()
        _uiState.update { it.copy(isRunning = false, showAdjustCameraTip = false) }
        persistDraftAsync()
    }

    fun onMotionFrame(snapshot: PoseSkeletonSnapshot) {
        val now = System.currentTimeMillis()
        if (snapshot.isMoving) {
            lastMotionSeenMillis = now
        }
        _uiState.update { state ->
            val bonusStep = if (state.isRunning && state.trackingMode == TrackingMode.CAMERA_AI && state.sportType.supportsSteps && snapshot.stepImpulse) 1 else 0
            state.copy(
                poseSnapshot = snapshot,
                isCurrentlyMoving = snapshot.isMoving,
                steps = state.steps + bonusStep,
                showAdjustCameraTip = if (snapshot.isMoving) false else state.showAdjustCameraTip
            )
        }
    }

    private fun startTickerLoop() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val current = _uiState.value
                if (!current.isRunning) break

                val now = System.currentTimeMillis()
                val sport = current.sportType
                val isCameraMode = current.trackingMode == TrackingMode.CAMERA_AI
                val recentlyMoved = !isCameraMode || (now - lastMotionSeenMillis) <= 2800L
                val idleMillis = now - lastMotionSeenMillis
                val shouldShowTip = isCameraMode && idleMillis >= 4000L

                val intensityFactor = when {
                    !isCameraMode -> current.manualEffort.multiplier
                    recentlyMoved -> (0.85 + current.poseSnapshot.motionIntensity * 0.45).coerceIn(0.85, 1.35)
                    else -> 0.0
                }

                val calDelta = if (intensityFactor > 0.0) (sport.kcalPerMinute / 60.0) * intensityFactor else 0.0
                var addedSteps = 0
                if (sport.supportsSteps && intensityFactor > 0.0) {
                    lastStepDeltaBuffer += (sport.stepsPerMinute / 60.0) * intensityFactor
                    if (lastStepDeltaBuffer >= 1.0) {
                        val whole = lastStepDeltaBuffer.toInt()
                        addedSteps = whole
                        lastStepDeltaBuffer -= whole
                    }
                }
                var addedMeters = 0
                if (sport.supportsDistance && intensityFactor > 0.0) {
                    lastDistDeltaBuffer += (sport.metersPerMinute / 60.0) * intensityFactor
                    if (lastDistDeltaBuffer >= 1.0) {
                        val whole = lastDistDeltaBuffer.toInt()
                        addedMeters = whole
                        lastDistDeltaBuffer -= whole
                    }
                }

                _uiState.update { s ->
                    s.copy(
                        elapsedSeconds = s.elapsedSeconds + 1,
                        motionActiveSeconds = s.motionActiveSeconds + if (recentlyMoved) 1 else 0,
                        caloriesExact = s.caloriesExact + calDelta,
                        steps = s.steps + addedSteps,
                        distanceMeters = s.distanceMeters + addedMeters,
                        isCurrentlyMoving = recentlyMoved,
                        showAdjustCameraTip = shouldShowTip
                    )
                }

                ticksSinceDraftSave++
                if (ticksSinceDraftSave >= 3) {
                    ticksSinceDraftSave = 0
                    persistDraftNow(_uiState.value)
                }
            }
        }
    }

    fun saveWorkout() {
        val state = _uiState.value
        if (state.isSaving || state.saveOutcome != null) return
        if (!state.canSaveWorkout) {
            _uiState.update {
                it.copy(errorMessage = "ต้องออกกำลังกายอย่างน้อย 15 นาที จึงจะบันทึกกิจกรรมได้")
            }
            return
        }

        timerJob?.cancel()
        val endMillis = System.currentTimeMillis()
        val startMillis = state.startedAtMillis.takeIf { it > 0L }
            ?: (endMillis - state.elapsedSeconds * 1000L)

        val session = WorkoutSession(
            id = state.sessionId,
            userId = UserProfile.LOCAL_PROFILE_ID,
            sportType = state.sportType.name,
            durationSeconds = state.elapsedSeconds,
            caloriesBurned = state.caloriesBurned.coerceAtLeast(1),
            steps = state.steps,
            distanceMeters = state.distanceMeters,
            trackingMode = state.trackingMode.name,
            motionActiveSeconds = state.motionActiveSeconds,
            activityDate = DateStreakUtils.dateStringFromMillis(startMillis),
            startedAtMillis = startMillis,
            endedAtMillis = endMillis
        )

        _uiState.update { it.copy(isRunning = false, isSaving = true, errorMessage = null) }

        viewModelScope.launch {
            val result = repository.saveCompletedWorkoutAndSyncStreak(
                sessionId = state.sessionId,
                session = session
            )
            result.onSuccess { outcome ->
                draftStore.clearDraft()
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveOutcome = outcome,
                        savedSession = session
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = err.localizedMessage ?: "บันทึกข้อมูลไม่สำเร็จ กรุณาลองอีกครั้ง"
                    )
                }
            }
        }
    }

    fun discardAndClearSession() {
        timerJob?.cancel()
        viewModelScope.launch {
            draftStore.clearDraft()
        }
        _uiState.update { it.copy(isRunning = false) }
    }

    fun persistDraftAsync() {
        val snapshot = _uiState.value
        if (!snapshot.hasStartedOnce || snapshot.saveOutcome != null) return
        viewModelScope.launch {
            persistDraftNow(snapshot)
        }
    }

    private suspend fun persistDraftNow(state: WorkoutUiState) {
        if (!state.hasStartedOnce || state.sessionId.isBlank() || state.saveOutcome != null) return
        draftStore.saveDraft(
            userId = UserProfile.LOCAL_PROFILE_ID,
            draft = ActiveWorkoutDraft(
                sessionId = state.sessionId,
                sportType = state.sportType,
                startedAtMillis = state.startedAtMillis,
                elapsedSeconds = state.elapsedSeconds,
                motionActiveSeconds = state.motionActiveSeconds,
                steps = state.steps,
                distanceMeters = state.distanceMeters,
                caloriesExact = state.caloriesExact,
                isRunning = state.isRunning,
                lastSavedEpochMillis = System.currentTimeMillis()
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
