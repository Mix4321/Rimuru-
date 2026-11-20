package com.example.data.firebase

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.ActiveWorkoutDraft
import com.example.data.model.SportType
import com.example.data.model.TrackingMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.workoutDraftDataStore by preferencesDataStore(name = "move_active_workout")

class WorkoutDraftStore(private val context: Context) {

    private object Keys {
        val USER_ID = stringPreferencesKey("user_id")
        val SESSION_ID = stringPreferencesKey("session_id")
        val SPORT_TYPE = stringPreferencesKey("sport_type")
        val TRACKING_MODE = stringPreferencesKey("tracking_mode")
        val STARTED_AT = longPreferencesKey("started_at")
        val ELAPSED_SEC = intPreferencesKey("elapsed_sec")
        val MOTION_SEC = intPreferencesKey("motion_sec")
        val STEPS = intPreferencesKey("steps")
        val DISTANCE_METERS = intPreferencesKey("distance_meters")
        val REPS_COUNT = intPreferencesKey("reps_count")
        val CALORIES_EXACT = doublePreferencesKey("calories_exact")
        val IS_RUNNING = booleanPreferencesKey("is_running")
        val LAST_SAVED_MS = longPreferencesKey("last_saved_ms")
        val ROUTE_JSON = stringPreferencesKey("route_json")
    }

    fun observeDraft(userId: String): Flow<ActiveWorkoutDraft?> =
        context.workoutDraftDataStore.data.map { prefs ->
            val savedUid = prefs[Keys.USER_ID]
            val sessionId = prefs[Keys.SESSION_ID]
            if (savedUid != userId || sessionId.isNullOrBlank()) {
                null
            } else {
                val sport = SportType.fromNameSafe(prefs[Keys.SPORT_TYPE])
                val mode = prefs[Keys.TRACKING_MODE]?.let { TrackingMode.fromNameSafe(it) }
                    ?: sport.defaultTrackingMode
                ActiveWorkoutDraft(
                    sessionId = sessionId,
                    sportType = sport,
                    trackingMode = mode,
                    startedAtMillis = prefs[Keys.STARTED_AT] ?: System.currentTimeMillis(),
                    elapsedSeconds = prefs[Keys.ELAPSED_SEC] ?: 0,
                    motionActiveSeconds = prefs[Keys.MOTION_SEC] ?: 0,
                    steps = prefs[Keys.STEPS] ?: 0,
                    distanceMeters = prefs[Keys.DISTANCE_METERS] ?: 0,
                    repsCount = prefs[Keys.REPS_COUNT] ?: 0,
                    caloriesExact = prefs[Keys.CALORIES_EXACT] ?: 0.0,
                    isRunning = prefs[Keys.IS_RUNNING] ?: false,
                    lastSavedEpochMillis = prefs[Keys.LAST_SAVED_MS] ?: System.currentTimeMillis(),
                    routeJson = prefs[Keys.ROUTE_JSON] ?: ""
                )
            }
        }

    suspend fun saveDraft(userId: String, draft: ActiveWorkoutDraft) {
        context.workoutDraftDataStore.edit { prefs ->
            prefs[Keys.USER_ID] = userId
            prefs[Keys.SESSION_ID] = draft.sessionId
            prefs[Keys.SPORT_TYPE] = draft.sportType.name
            prefs[Keys.TRACKING_MODE] = draft.trackingMode.name
            prefs[Keys.STARTED_AT] = draft.startedAtMillis
            prefs[Keys.ELAPSED_SEC] = draft.elapsedSeconds
            prefs[Keys.MOTION_SEC] = draft.motionActiveSeconds
            prefs[Keys.STEPS] = draft.steps
            prefs[Keys.DISTANCE_METERS] = draft.distanceMeters
            prefs[Keys.REPS_COUNT] = draft.repsCount
            prefs[Keys.CALORIES_EXACT] = draft.caloriesExact
            prefs[Keys.IS_RUNNING] = draft.isRunning
            prefs[Keys.LAST_SAVED_MS] = draft.lastSavedEpochMillis
            prefs[Keys.ROUTE_JSON] = draft.routeJson
        }
    }

    suspend fun clearDraft() {
        context.workoutDraftDataStore.edit { it.clear() }
    }
}
