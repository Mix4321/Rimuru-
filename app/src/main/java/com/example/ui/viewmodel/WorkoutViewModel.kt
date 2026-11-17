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
import com.example.data.model.RoutePoint
import com.example.data.model.SportType
import com.example.data.model.TrackingMode
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutSession
import com.example.domain.DateStreakUtils
import com.example.domain.GhostRivalSnapshot
import com.example.domain.MoveAnalyticsEngine
import com.example.sensors.GpsTrackingSnapshot
import com.example.sensors.SensorMotionSnapshot
import com.example.ui.components.RouteJsonCodec
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

enum class WorkoutTimerStyle(val thaiLabel: String) {
    FREE("เวลาอิสระ"),
    HIIT("HIIT / สลับช่วง")
}

enum class HiitPhase(val thaiLabel: String) {
    WORK("ลุยเต็มที่!"),
    REST("พักหายใจ"),
    COMPLETED("ครบทุกรอบ HIIT แล้ว!")
}

data class HiitPreset(
    val id: String,
    val titleThai: String,
    val workSec: Int,
    val restSec: Int,
    val rounds: Int,
    val circuitNames: List<String>
)

object HiitPresets {
    val ALL = listOf(
        HiitPreset(
            id = "tabata",
            titleThai = "Tabata เร่งเผาผลาญ (20/10วิ x 6)",
            workSec = 20,
            restSec = 10,
            rounds = 6,
            circuitNames = listOf("กระโดดตบ", "สควอท", "วิดพื้น", "แพลงก์", "ลันจ์", "กระโดดเชือก")
        ),
        HiitPreset(
            id = "full_body",
            titleThai = "Full Body Circuit (40/20วิ x 6)",
            workSec = 40,
            restSec = 20,
            rounds = 6,
            circuitNames = listOf("สควอท", "วิดพื้น", "ลันจ์", "ซิทอัพ", "แพลงก์", "กระโดดตบ")
        ),
        HiitPreset(
            id = "core_blast",
            titleThai = "Core & Plank (30/15วิ x 4)",
            workSec = 30,
            restSec = 15,
            rounds = 4,
            circuitNames = listOf("แพลงก์", "ซิทอัพ", "แพลงก์", "ซิทอัพ")
        )
    )
}

data class CoachCueEvent(
    val id: Long,
    val textThai: String,
    val priority: Boolean = false,
    val isHighChime: Boolean = true
)

data class WorkoutUiState(
    val sessionId: String = "",
    val sportType: SportType = SportType.RUNNING,
    val userWeightKg: Float = 65f,
    val startedAtMillis: Long = 0L,
    val elapsedSeconds: Int = 0,
    val motionActiveSeconds: Int = 0,
    val plankHoldSeconds: Int = 0,
    val steps: Int = 0,
    val distanceMeters: Int = 0,
    val repsCount: Int = 0,
    val caloriesExact: Double = 0.0,
    val isRunning: Boolean = false,
    val hasStartedOnce: Boolean = false,
    val trackingMode: TrackingMode = TrackingMode.SENSOR_GPS,
    val timerStyle: WorkoutTimerStyle = WorkoutTimerStyle.FREE,
    val hiitWorkSeconds: Int = 30,
    val hiitRestSeconds: Int = 15,
    val hiitTotalRounds: Int = 4,
    val hiitCurrentRound: Int = 1,
    val hiitPhase: HiitPhase = HiitPhase.WORK,
    val hiitPhaseRemainingSec: Int = 30,
    val hiitCircuitLabel: String = "ฝึกต่อเนื่อง",
    val cameraPermissionGranted: Boolean = false,
    val locationPermissionGranted: Boolean = false,
    val activityPermissionGranted: Boolean = false,
    val permissionsEvaluated: Boolean = false,
    val useFrontCamera: Boolean = true,
    val poseSnapshot: PoseSkeletonSnapshot = PoseSkeletonSnapshot(),
    val sensorSnapshot: SensorMotionSnapshot = SensorMotionSnapshot(),
    val gpsSnapshot: GpsTrackingSnapshot = GpsTrackingSnapshot(),
    val routePoints: List<RoutePoint> = emptyList(),
    val latestSplitBanner: String? = null,
    val isCurrentlyMoving: Boolean = false,
    val currentIntensity: Float = 0f,
    val showAdjustCameraTip: Boolean = false,
    val ghostRival: GhostRivalSnapshot = GhostRivalSnapshot(),
    val symmetryPercent: Int = 100,
    val formScorePercent: Int = 0,
    val avgFormScorePercent: Int = 0,
    val avgSymmetryPercent: Int = 100,
    val symmetryHint: String = "",
    val fatigueWarning: Boolean = false,
    val fatigueHint: String = "",
    val manualEffort: ManualEffortLevel = ManualEffortLevel.MODERATE,
    val voiceCoachMuted: Boolean = false,
    val coachCue: CoachCueEvent? = null,
    val isSaving: Boolean = false,
    val saveOutcome: SaveWorkoutOutcome? = null,
    val savedSession: WorkoutSession? = null,
    val errorMessage: String? = null
) {
    val caloriesBurned: Int get() = caloriesExact.roundToInt()
    val canSaveWorkout: Boolean get() = elapsedSeconds >= DateStreakUtils.MIN_WORKOUT_SECONDS
    val remainingToMinSeconds: Int get() = (DateStreakUtils.MIN_WORKOUT_SECONDS - elapsedSeconds).coerceAtLeast(0)
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

    private var formScoreSum: Long = 0L
    private var formScoreSamples: Int = 0
    private var symmetrySum: Long = 0L
    private var symmetrySamples: Int = 0
    private var lastAnnouncedRepMilestone: Int = 0
    private var lastAnnouncedKmSplit: Int = 0
    private var circuitExerciseNames: List<String> = listOf("สควอท", "วิดพื้น", "แพลงก์", "กระโดดตบ")

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
        initialMode: TrackingMode = sportType.defaultTrackingMode,
        timerStyle: WorkoutTimerStyle = WorkoutTimerStyle.FREE,
        hiitWorkSec: Int = 30,
        hiitRestSec: Int = 15,
        hiitRounds: Int = 4
    ) {
        timerJob?.cancel()
        val now = System.currentTimeMillis()
        resetSessionAccumulators()
        val safeWork = hiitWorkSec.coerceIn(10, 180)
        val safeRest = hiitRestSec.coerceIn(5, 120)
        val safeRounds = hiitRounds.coerceIn(1, 20)
        _uiState.value = WorkoutUiState(
            sessionId = UUID.randomUUID().toString(),
            sportType = sportType,
            startedAtMillis = now,
            isRunning = false,
            hasStartedOnce = false,
            trackingMode = initialMode,
            timerStyle = timerStyle,
            hiitWorkSeconds = safeWork,
            hiitRestSeconds = safeRest,
            hiitTotalRounds = safeRounds,
            hiitCurrentRound = 1,
            hiitPhase = HiitPhase.WORK,
            hiitPhaseRemainingSec = safeWork,
            hiitCircuitLabel = circuitExerciseNames.firstOrNull() ?: sportType.thaiName
        )
        loadProfileAndGhostRival(sportType)
    }

    fun restoreFromDraft(draft: ActiveWorkoutDraft) {
        timerJob?.cancel()
        resetSessionAccumulators()

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

    private fun resetSessionAccumulators() {
        lastMotionSeenMillis = 0L
        strideMetersFractionBuffer = 0.0
        formScoreSum = 0L
        formScoreSamples = 0
        symmetrySum = 0L
        symmetrySamples = 0
        lastAnnouncedRepMilestone = 0
        lastAnnouncedKmSplit = 0
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

    fun setTimerStyle(style: WorkoutTimerStyle) {
        _uiState.update { state ->
            state.copy(
                timerStyle = style,
                hiitCurrentRound = 1,
                hiitPhase = HiitPhase.WORK,
                hiitPhaseRemainingSec = state.hiitWorkSeconds,
                hiitCircuitLabel = circuitExerciseNames.firstOrNull() ?: state.sportType.thaiName
            )
        }
    }

    fun applyHiitPreset(preset: HiitPreset) {
        circuitExerciseNames = preset.circuitNames
        _uiState.update { state ->
            state.copy(
                timerStyle = WorkoutTimerStyle.HIIT,
                hiitWorkSeconds = preset.workSec,
                hiitRestSeconds = preset.restSec,
                hiitTotalRounds = preset.rounds,
                hiitCurrentRound = 1,
                hiitPhase = HiitPhase.WORK,
                hiitPhaseRemainingSec = preset.workSec,
                hiitCircuitLabel = preset.circuitNames.firstOrNull() ?: state.sportType.thaiName
            )
        }
    }

    fun updateCustomHiitConfig(workSec: Int, restSec: Int, rounds: Int) {
        val w = workSec.coerceIn(10, 180)
        val r = restSec.coerceIn(5, 120)
        val n = rounds.coerceIn(1, 20)
        _uiState.update { state ->
            state.copy(
                timerStyle = WorkoutTimerStyle.HIIT,
                hiitWorkSeconds = w,
                hiitRestSeconds = r,
                hiitTotalRounds = n,
                hiitCurrentRound = 1,
                hiitPhase = HiitPhase.WORK,
                hiitPhaseRemainingSec = w
            )
        }
    }

    fun toggleVoiceCoachMute() {
        _uiState.update { it.copy(voiceCoachMuted = !it.voiceCoachMuted) }
    }

    fun consumeCoachCue() {
        _uiState.update { it.copy(coachCue = null) }
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
            val startCue = if (!it.hasStartedOnce && currentSettings.enableVoiceCoach && !it.voiceCoachMuted) {
                CoachCueEvent(
                    id = System.nanoTime(),
                    textThai = "เริ่มออกกำลังกาย ${it.sportType.thaiName}",
                    priority = true,
                    isHighChime = true
                )
            } else {
                it.coachCue
            }
            it.copy(
                isRunning = true,
                hasStartedOnce = true,
                startedAtMillis = if (it.startedAtMillis == 0L) System.currentTimeMillis() else it.startedAtMillis,
                coachCue = startCue,
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

            if (canAccumulate && snapshot.isBodyVisible) {
                formScoreSum += snapshot.formScorePercent
                formScoreSamples++
                symmetrySum += snapshot.symmetryPercent
                symmetrySamples++
            }

            val newReps = state.repsCount + addReps
            var newCue = state.coachCue
            if (addReps > 0 && currentSettings.enableVoiceCoach && !state.voiceCoachMuted) {
                val milestone = (newReps / 5) * 5
                if (newReps >= 5 && milestone > lastAnnouncedRepMilestone) {
                    lastAnnouncedRepMilestone = milestone
                    newCue = CoachCueEvent(
                        id = System.nanoTime(),
                        textThai = "$milestone ครั้งแล้ว เยี่ยมมาก!",
                        priority = false,
                        isHighChime = true
                    )
                }
            }

            val avgForm = if (formScoreSamples > 0) (formScoreSum / formScoreSamples).toInt() else state.avgFormScorePercent
            val avgSym = if (symmetrySamples > 0) (symmetrySum / symmetrySamples).toInt() else state.avgSymmetryPercent

            state.copy(
                poseSnapshot = snapshot,
                isCurrentlyMoving = if (state.trackingMode == TrackingMode.CAMERA_AI) snapshot.isMoving else state.isCurrentlyMoving,
                currentIntensity = if (state.trackingMode == TrackingMode.CAMERA_AI) snapshot.motionIntensity else state.currentIntensity,
                symmetryPercent = if (state.trackingMode == TrackingMode.CAMERA_AI && snapshot.isBodyVisible) snapshot.symmetryPercent else state.symmetryPercent,
                formScorePercent = if (state.trackingMode == TrackingMode.CAMERA_AI && snapshot.isBodyVisible) snapshot.formScorePercent else state.formScorePercent,
                avgFormScorePercent = avgForm,
                avgSymmetryPercent = avgSym,
                symmetryHint = if (state.trackingMode == TrackingMode.CAMERA_AI) snapshot.symmetryHint else "",
                fatigueWarning = if (state.trackingMode == TrackingMode.CAMERA_AI) snapshot.fatigueWarning else false,
                fatigueHint = if (state.trackingMode == TrackingMode.CAMERA_AI) snapshot.fatigueHint else "",
                steps = state.steps + addSteps,
                repsCount = newReps,
                distanceMeters = state.distanceMeters + addMeters,
                coachCue = newCue,
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
            val addMeters = if (state.sportType.supportsDistance && !state.gpsSnapshot.hasFix && deltaSteps > 0) {
                accumulateMetersFromSteps(deltaSteps, effectiveStrideMeters(state.sportType).coerceAtLeast(0.72))
            } else {
                0
            }
            val updatedDist = state.distanceMeters + addMeters
            evaluateKmSplitCue(state, updatedDist).copy(
                steps = state.steps + addSteps,
                distanceMeters = updatedDist,
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
            val updatedDist = state.distanceMeters + deltaMeters
            evaluateKmSplitCue(state, updatedDist).copy(
                distanceMeters = updatedDist,
                isCurrentlyMoving = true
            )
        }
    }

    fun onGpsRoutePointRecorded(lat: Double, lng: Double, speedKmh: Float) {
        _uiState.update { state ->
            if (!state.isRunning || state.trackingMode != TrackingMode.SENSOR_GPS || !state.sportType.supportsDistance) {
                return@update state
            }
            val next = RoutePoint(
                lat = lat,
                lng = lng,
                speedKmh = speedKmh,
                elapsedSec = state.elapsedSeconds
            )
            val updated = if (state.routePoints.size >= 300) {
                // Keep evenly thinned trail + newest point
                state.routePoints.filterIndexed { i, _ -> i % 2 == 0 } + next
            } else {
                state.routePoints + next
            }
            state.copy(routePoints = updated)
        }
    }

    private fun evaluateKmSplitCue(state: WorkoutUiState, newDistanceMeters: Int): WorkoutUiState {
        val kmCompleted = newDistanceMeters / 1000
        if (kmCompleted <= 0 || kmCompleted <= lastAnnouncedKmSplit) return state
        lastAnnouncedKmSplit = kmCompleted
        val paceSecPerKm = if (newDistanceMeters > 0) (state.elapsedSeconds * 1000 / newDistanceMeters) else 0
        val paceMin = paceSecPerKm / 60
        val paceRemSec = paceSecPerKm % 60
        val banner = "ครบ $kmCompleted กม. • เพซเฉลี่ย $paceMin:${paceRemSec.toString().padStart(2, '0')} น./กม."
        val cue = if (currentSettings.enableVoiceCoach && !state.voiceCoachMuted) {
            CoachCueEvent(
                id = System.nanoTime(),
                textThai = "ครบ $kmCompleted กิโลเมตรแล้ว เพซเฉลี่ย $paceMin นาที $paceRemSec วินาที",
                priority = true,
                isHighChime = true
            )
        } else {
            state.coachCue
        }
        return state.copy(
            latestSplitBanner = banner,
            coachCue = cue
        )
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
                val isHoldingPlank = mode == TrackingMode.CAMERA_AI && current.poseSnapshot.isPlankHoldingValid
                val isMovingRecently = when (mode) {
                    TrackingMode.CAMERA_AI -> current.poseSnapshot.isBodyVisible &&
                        (current.poseSnapshot.isMoving || isHoldingPlank || (nowWall - lastMotionSeenMillis) <= 2200L)
                    TrackingMode.SENSOR_GPS -> current.sensorSnapshot.isMoving ||
                        current.gpsSnapshot.currentSpeedKmh >= 1.2f ||
                        (nowWall - lastMotionSeenMillis) <= 2200L
                    TrackingMode.MANUAL -> true
                }

                val idleMillis = if (lastMotionSeenMillis > 0L) nowWall - lastMotionSeenMillis else current.elapsedSeconds * 1000L
                val shouldShowCameraTip = currentSettings.autoNotifyWhenIdle &&
                    mode == TrackingMode.CAMERA_AI &&
                    (!current.poseSnapshot.isBodyVisible || (!isHoldingPlank && idleMillis >= 4000L))

                val baseKcalPerSec = (sport.metValue * current.userWeightKg * 3.5 / 200.0) / 60.0
                val intensityMultiplier = when (mode) {
                    TrackingMode.CAMERA_AI -> {
                        if (isHoldingPlank) 0.85
                        else if (isMovingRecently) (0.75 + current.poseSnapshot.motionIntensity * 0.55).coerceIn(0.75, 1.35)
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
                    var nextRound = s.hiitCurrentRound
                    var nextPhase = s.hiitPhase
                    var nextRemain = s.hiitPhaseRemainingSec
                    var nextCircuit = s.hiitCircuitLabel
                    var cue = s.coachCue

                    if (s.timerStyle == WorkoutTimerStyle.HIIT && s.hiitPhase != HiitPhase.COMPLETED) {
                        nextRemain -= wholeSeconds
                        if (nextRemain <= 0) {
                            when (s.hiitPhase) {
                                HiitPhase.WORK -> {
                                    if (s.hiitCurrentRound >= s.hiitTotalRounds) {
                                        nextPhase = HiitPhase.COMPLETED
                                        nextRemain = 0
                                        if (currentSettings.enableVoiceCoach && !s.voiceCoachMuted) {
                                            cue = CoachCueEvent(System.nanoTime(), "ครบทุกรอบ HIIT แล้ว ยอดเยี่ยมมาก!", priority = true, isHighChime = true)
                                        }
                                    } else {
                                        nextPhase = HiitPhase.REST
                                        nextRemain = s.hiitRestSeconds
                                        val upcoming = circuitExerciseNames.getOrNull(s.hiitCurrentRound % circuitExerciseNames.size) ?: s.sportType.thaiName
                                        nextCircuit = "เตรียมท่าถัดไป: $upcoming"
                                        if (currentSettings.enableVoiceCoach && !s.voiceCoachMuted) {
                                            cue = CoachCueEvent(System.nanoTime(), "พัก ${s.hiitRestSeconds} วินาที เตรียมท่า $upcoming", priority = true, isHighChime = false)
                                        }
                                    }
                                }
                                HiitPhase.REST -> {
                                    nextRound = s.hiitCurrentRound + 1
                                    nextPhase = HiitPhase.WORK
                                    nextRemain = s.hiitWorkSeconds
                                    val curEx = circuitExerciseNames.getOrNull((nextRound - 1) % circuitExerciseNames.size) ?: s.sportType.thaiName
                                    nextCircuit = curEx
                                    if (currentSettings.enableVoiceCoach && !s.voiceCoachMuted) {
                                        cue = CoachCueEvent(System.nanoTime(), "รอบที่ $nextRound เริ่ม $curEx!", priority = true, isHighChime = true)
                                    }
                                }
                                HiitPhase.COMPLETED -> Unit
                            }
                        }
                    }

                    s.copy(
                        elapsedSeconds = s.elapsedSeconds + wholeSeconds,
                        motionActiveSeconds = s.motionActiveSeconds + if (isMovingRecently) wholeSeconds else 0,
                        plankHoldSeconds = s.plankHoldSeconds + if (isHoldingPlank) wholeSeconds else 0,
                        caloriesExact = s.caloriesExact + calDelta,
                        isCurrentlyMoving = isMovingRecently,
                        showAdjustCameraTip = shouldShowCameraTip,
                        hiitCurrentRound = nextRound,
                        hiitPhase = nextPhase,
                        hiitPhaseRemainingSec = nextRemain.coerceAtLeast(0),
                        hiitCircuitLabel = nextCircuit,
                        coachCue = cue
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
                it.copy(errorMessage = "กรุณาจับเวลาอย่างน้อย ${DateStreakUtils.MIN_WORKOUT_SECONDS} วินาที จึงจะบันทึกกิจกรรมได้")
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
            endedAtMillis = endMillis,
            avgFormScore = state.avgFormScorePercent,
            avgSymmetryPercent = state.avgSymmetryPercent,
            routeJson = RouteJsonCodec.encode(state.routePoints),
            workoutSubType = if (state.timerStyle == WorkoutTimerStyle.HIIT) "HIIT" else "FREE"
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
