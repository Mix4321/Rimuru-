package com.example.ui.viewmodel

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.camera.PoseSkeletonSnapshot
import com.example.data.firebase.WorkoutDraftStore
import com.example.data.local.AppSettings
import com.example.data.local.MoveRepository
import com.example.data.local.SaveWorkoutOutcome
import com.example.data.model.ActiveWorkoutDraft
import com.example.data.model.SportType
import com.example.data.model.TrackingMode
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutSession
import com.example.domain.DateStreakUtils
import com.example.domain.GhostRivalSnapshot
import com.example.domain.MoveAnalyticsEngine
import com.example.sensors.GpsTrackingSnapshot
import com.example.sensors.SensorMotionSnapshot
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
    LIGHT("เบา", 0.78),
    MODERATE("ปานกลาง", 1.0),
    INTENSE("เข้มข้น", 1.28)
}

data class WorkoutUiState(
    val sessionId: String = "",
    val sportType: SportType = SportType.RUNNING,
    val userWeightKg: Float = 65f,
    val startedAtMillis: Long = 0L,
    val elapsedSeconds: Int = 0,
    val motionActiveSeconds: Int = 0,
    val steps: Int = 0,
    val distanceMeters: Int = 0,
    val repsCount: Int = 0,
    val caloriesExact: Double = 0.0,
    val isRunning: Boolean = false,
    val hasStartedOnce: Boolean = false,
    val trackingMode: TrackingMode = TrackingMode.SENSOR_GPS,
    val cameraPermissionGranted: Boolean = false,
    val locationPermissionGranted: Boolean = false,
    val activityPermissionGranted: Boolean = false,
    val permissionsEvaluated: Boolean = false,
    val useFrontCamera: Boolean = true,
    val poseSnapshot: PoseSkeletonSnapshot = PoseSkeletonSnapshot(),
    val sensorSnapshot: SensorMotionSnapshot = SensorMotionSnapshot(),
    val gpsSnapshot: GpsTrackingSnapshot = GpsTrackingSnapshot(),
    val isCurrentlyMoving: Boolean = false,
    val currentIntensity: Float = 0f,
    val showAdjustCameraTip: Boolean = false,
    val ghostRival: GhostRivalSnapshot = GhostRivalSnapshot(),
    val symmetryPercent: Int = 100,
    val formScorePercent: Int = 0,
    val symmetryHint: String = "",
    val fatigueWarning: Boolean = false,
    val fatigueHint: String = "",
    val manualEffort: ManualEffortLevel = ManualEffortLevel.MODERATE,
    val isSaving: Boolean = false,
    val saveOutcome: SaveWorkoutOutcome? = null,
    val savedSession: WorkoutSession? = null,
    val errorMessage: String? = null
) {
    val caloriesBurned: Int get() = caloriesExact.roundToInt()
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
    private var strideMetersFractionBuffer: Double = 0.0
    private var ticksSinceDraftSave: Int = 0
    private var currentSettings: AppSettings = AppSettings()

    fun updateAppSettings(settings: AppSettings) {
        currentSettings = settings
    }

    private fun effectiveStrideMeters(sport: SportType): Double = when (sport) {
        SportType.WALKING -> currentSettings.walkStrideMeters
        SportType.RUNNING, SportType.FOOTBALL, SportType.BASKETBALL -> currentSettings.runStrideMeters
        else -> sport.strideMeters
    }

    fun startFreshSession(
        sportType: SportType,
        initialMode: TrackingMode = sportType.defaultTrackingMode
    ) {
        timerJob?.cancel()
        val now = System.currentTimeMillis()
        lastMotionSeenMillis = 0L
        strideMetersFractionBuffer = 0.0
        _uiState.value = WorkoutUiState(
            sessionId = UUID.randomUUID().toString(),
            sportType = sportType,
            startedAtMillis = now,
            isRunning = false,
            hasStartedOnce = false,
            trackingMode = initialMode
        )
        loadProfileAndGhostRival(sportType)
    }

    fun restoreFromDraft(draft: ActiveWorkoutDraft) {
        timerJob?.cancel()
        lastMotionSeenMillis = 0L
        strideMetersFractionBuffer = 0.0

        _uiState.value = WorkoutUiState(
            sessionId = draft.sessionId,
            sportType = draft.sportType,
            startedAtMillis = draft.startedAtMillis,
            elapsedSeconds = draft.elapsedSeconds,
            motionActiveSeconds = draft.motionActiveSeconds,
            steps = draft.steps,
            distanceMeters = draft.distanceMeters,
            repsCount = draft.repsCount,
            caloriesExact = draft.caloriesExact,
            isRunning = false,
            hasStartedOnce = true,
            trackingMode = draft.trackingMode
        )
        loadProfileAndGhostRival(draft.sportType)
    }

    private fun loadProfileAndGhostRival(sportType: SportType) {
        viewModelScope.launch {
            runCatching { repository.getUserProfileOnce() }
                .onSuccess { profile ->
                    _uiState.update { it.copy(userWeightKg = profile.weightKg.coerceIn(25f, 250f)) }
                }
            runCatching { repository.getAllWorkoutsOnce() }
                .onSuccess { workouts ->
                    val rival = MoveAnalyticsEngine.findBestGhostRival(workouts, sportType)
                    _uiState.update { it.copy(ghostRival = rival) }
                }
        }
    }

    fun updatePermissionStates(
        cameraGranted: Boolean,
        locationGranted: Boolean,
        activityGranted: Boolean,
        autoStartIfFirstTime: Boolean = false
    ) {
        _uiState.update { state ->
            val adjustedMode = if (state.trackingMode == TrackingMode.CAMERA_AI && !cameraGranted && autoStartIfFirstTime) {
                if (state.sportType == SportType.GENERAL) TrackingMode.MANUAL else TrackingMode.SENSOR_GPS
            } else {
                state.trackingMode
            }
            state.copy(
                cameraPermissionGranted = cameraGranted,
                locationPermissionGranted = locationGranted,
                activityPermissionGranted = activityGranted,
                permissionsEvaluated = true,
                trackingMode = adjustedMode,
                showAdjustCameraTip = false
            )
        }
        if (autoStartIfFirstTime && !_uiState.value.hasStartedOnce) {
            startOrResumeTimer()
        }
    }

    fun switchTrackingMode(mode: TrackingMode) {
        _uiState.update { state ->
            if (mode == TrackingMode.CAMERA_AI && !state.cameraPermissionGranted) {
                return@update state.copy(
                    errorMessage = "กรุณาอนุญาตสิทธิ์กล้องก่อนสลับเป็นโหมดกล้อง AI"
                )
            }
            state.copy(
                trackingMode = mode,
                showAdjustCameraTip = false,
                isCurrentlyMoving = false,
                currentIntensity = 0f,
                errorMessage = null
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
        _uiState.update {
            it.copy(
                isRunning = false,
                isCurrentlyMoving = false,
                currentIntensity = 0f,
                showAdjustCameraTip = false
            )
        }
        persistDraftAsync()
    }

    fun onMotionFrame(snapshot: PoseSkeletonSnapshot) {
        val now = System.currentTimeMillis()
        if (snapshot.isMoving) {
            lastMotionSeenMillis = now
        }
        _uiState.update { state ->
            val canAccumulate = state.isRunning && state.trackingMode == TrackingMode.CAMERA_AI
            val addSteps = if (canAccumulate && state.sportType.supportsSteps) snapshot.stepDelta else 0
            val addReps = if (canAccumulate && state.sportType.supportsReps) snapshot.repDelta else 0
            val addMeters = if (canAccumulate && state.sportType.supportsDistance && addSteps > 0) {
                accumulateMetersFromSteps(addSteps, effectiveStrideMeters(state.sportType))
            } else {
                0
            }

            state.copy(
                poseSnapshot = snapshot,
                isCurrentlyMoving = if (state.trackingMode == TrackingMode.CAMERA_AI) snapshot.isMoving else state.isCurrentlyMoving,
                currentIntensity = if (state.trackingMode == TrackingMode.CAMERA_AI) snapshot.motionIntensity else state.currentIntensity,
                symmetryPercent = if (state.trackingMode == TrackingMode.CAMERA_AI && snapshot.isBodyVisible) snapshot.symmetryPercent else state.symmetryPercent,
                formScorePercent = if (state.trackingMode == TrackingMode.CAMERA_AI && snapshot.isBodyVisible) snapshot.formScorePercent else state.formScorePercent,
                symmetryHint = if (state.trackingMode == TrackingMode.CAMERA_AI) snapshot.symmetryHint else "",
                fatigueWarning = if (state.trackingMode == TrackingMode.CAMERA_AI) snapshot.fatigueWarning else false,
                fatigueHint = if (state.trackingMode == TrackingMode.CAMERA_AI) snapshot.fatigueHint else "",
                steps = state.steps + addSteps,
                repsCount = state.repsCount + addReps,
                distanceMeters = state.distanceMeters + addMeters,
                showAdjustCameraTip = if (snapshot.isMoving && snapshot.isBodyVisible) false else state.showAdjustCameraTip
            )
        }
    }

    fun onSensorStepDetected(deltaSteps: Int) {
        if (deltaSteps <= 0) return
        val now = System.currentTimeMillis()
        _uiState.update { state ->
            if (!state.isRunning || state.trackingMode != TrackingMode.SENSOR_GPS) return@update state
            lastMotionSeenMillis = now
            val addSteps = if (state.sportType.supportsSteps) deltaSteps else 0
            // When GPS doesn't have an accurate outdoor fix yet (e.g. treadmill / indoor), derive real distance from real hardware steps
            val addMeters = if (state.sportType.supportsDistance && !state.gpsSnapshot.hasFix && deltaSteps > 0) {
                accumulateMetersFromSteps(deltaSteps, effectiveStrideMeters(state.sportType).coerceAtLeast(0.72))
            } else {
                0
            }
            state.copy(
                steps = state.steps + addSteps,
                distanceMeters = state.distanceMeters + addMeters,
                isCurrentlyMoving = true
            )
        }
    }

    fun onSensorJumpRepDetected(deltaReps: Int) {
        if (deltaReps <= 0) return
        val now = System.currentTimeMillis()
        _uiState.update { state ->
            if (!state.isRunning || state.trackingMode != TrackingMode.SENSOR_GPS) return@update state
            if (!state.sportType.supportsReps) return@update state
            lastMotionSeenMillis = now
            state.copy(
                repsCount = state.repsCount + deltaReps,
                isCurrentlyMoving = true
            )
        }
    }

    fun onSensorMotionStateChanged(snapshot: SensorMotionSnapshot) {
        val now = System.currentTimeMillis()
        _uiState.update { state ->
            if (state.trackingMode == TrackingMode.SENSOR_GPS && state.isRunning && snapshot.isMoving) {
                lastMotionSeenMillis = now
            }
            val effectiveMoving = if (state.trackingMode == TrackingMode.SENSOR_GPS) {
                snapshot.isMoving || state.gpsSnapshot.currentSpeedKmh >= 1.2f || (now - lastMotionSeenMillis) <= 2200L
            } else {
                state.isCurrentlyMoving
            }
            state.copy(
                sensorSnapshot = snapshot,
                isCurrentlyMoving = effectiveMoving,
                currentIntensity = if (state.trackingMode == TrackingMode.SENSOR_GPS) snapshot.motionIntensity else state.currentIntensity
            )
        }
    }

    fun onGpsDistanceAdded(deltaMeters: Int) {
        if (deltaMeters <= 0) return
        val now = System.currentTimeMillis()
        _uiState.update { state ->
            if (!state.isRunning || state.trackingMode != TrackingMode.SENSOR_GPS || !state.sportType.supportsDistance) {
                return@update state
            }
            lastMotionSeenMillis = now
            state.copy(
                distanceMeters = state.distanceMeters + deltaMeters,
                isCurrentlyMoving = true
            )
        }
    }

    fun onGpsStateChanged(snapshot: GpsTrackingSnapshot) {
        val now = System.currentTimeMillis()
        _uiState.update { state ->
            if (state.trackingMode == TrackingMode.SENSOR_GPS && state.isRunning && snapshot.currentSpeedKmh >= 1.2f) {
                lastMotionSeenMillis = now
            }
            state.copy(gpsSnapshot = snapshot)
        }
    }

    private fun accumulateMetersFromSteps(steps: Int, strideMeters: Double): Int {
        if (steps <= 0 || strideMeters <= 0.0) return 0
        strideMetersFractionBuffer += steps * strideMeters
        val whole = strideMetersFractionBuffer.toInt()
        if (whole >= 1) {
            strideMetersFractionBuffer -= whole
        }
        return whole
    }

    private fun startTickerLoop() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var lastElapsedReal = SystemClock.elapsedRealtime()
            var msCarry = 0L

            while (true) {
                delay(500L)
                val current = _uiState.value
                if (!current.isRunning) break

                val nowReal = SystemClock.elapsedRealtime()
                msCarry += (nowReal - lastElapsedReal).coerceIn(0L, 5000L)
                lastElapsedReal = nowReal

                val wholeSeconds = (msCarry / 1000L).toInt()
                if (wholeSeconds <= 0) continue
                msCarry %= 1000L

                val nowWall = System.currentTimeMillis()
                val sport = current.sportType
                val mode = current.trackingMode
                val isMovingRecently = when (mode) {
                    TrackingMode.CAMERA_AI -> current.poseSnapshot.isBodyVisible &&
                        (current.poseSnapshot.isMoving || (nowWall - lastMotionSeenMillis) <= 2200L)
                    TrackingMode.SENSOR_GPS -> current.sensorSnapshot.isMoving ||
                        current.gpsSnapshot.currentSpeedKmh >= 1.2f ||
                        (nowWall - lastMotionSeenMillis) <= 2200L
                    TrackingMode.MANUAL -> true
                }

                val idleMillis = if (lastMotionSeenMillis > 0L) nowWall - lastMotionSeenMillis else current.elapsedSeconds * 1000L
                val shouldShowCameraTip = currentSettings.autoNotifyWhenIdle &&
                    mode == TrackingMode.CAMERA_AI &&
                    (!current.poseSnapshot.isBodyVisible || idleMillis >= 4000L)

                // Scientific MET calorie formula: kcal/min = MET * weight(kg) * 3.5 / 200
                val baseKcalPerSec = (sport.metValue * current.userWeightKg * 3.5 / 200.0) / 60.0
                val intensityMultiplier = when (mode) {
                    TrackingMode.CAMERA_AI -> {
                        if (isMovingRecently) (0.75 + current.poseSnapshot.motionIntensity * 0.55).coerceIn(0.75, 1.35)
                        else 0.0
                    }
                    TrackingMode.SENSOR_GPS -> {
                        if (isMovingRecently) (0.78 + current.sensorSnapshot.motionIntensity * 0.55).coerceIn(0.78, 1.38)
                        else 0.0
                    }
                    TrackingMode.MANUAL -> current.manualEffort.multiplier
                }

                val calDelta = baseKcalPerSec * intensityMultiplier * wholeSeconds

                _uiState.update { s ->
                    s.copy(
                        elapsedSeconds = s.elapsedSeconds + wholeSeconds,
                        motionActiveSeconds = s.motionActiveSeconds + if (isMovingRecently) wholeSeconds else 0,
                        caloriesExact = s.caloriesExact + calDelta,
                        isCurrentlyMoving = isMovingRecently,
                        showAdjustCameraTip = shouldShowCameraTip
                    )
                }

                ticksSinceDraftSave += wholeSeconds
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
            repsCount = state.repsCount,
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
                trackingMode = state.trackingMode,
                startedAtMillis = state.startedAtMillis,
                elapsedSeconds = state.elapsedSeconds,
                motionActiveSeconds = state.motionActiveSeconds,
                steps = state.steps,
                distanceMeters = state.distanceMeters,
                repsCount = state.repsCount,
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
