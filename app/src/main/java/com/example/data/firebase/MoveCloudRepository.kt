package com.example.data.firebase

import android.content.Context
import androidx.annotation.Keep
import com.example.R
import com.example.data.model.SportType
import com.example.data.model.TrackingMode
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutSession
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

@Keep
@IgnoreExtraProperties
data class UserCloudProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "นักกีฬา MOVE",
    val email: String? = null,
    val dailyGoalMinutes: Long = 0L,
    val weightKg: Double = 65.0,
    val heightCm: Double = 170.0,
    val currentStreak: Long = 0L,
    val longestStreak: Long = 0L,
    val lastActiveDate: String = "",
    val onboardingCompleted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class WorkoutCloudSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sportType: String = SportType.GENERAL.name,
    val durationSeconds: Long = 0L,
    val caloriesBurned: Long = 0L,
    val steps: Long = 0L,
    val distanceMeters: Long = 0L,
    val repsCount: Long = 0L,
    val trackingMode: String = TrackingMode.MANUAL.name,
    val motionActiveSeconds: Long = 0L,
    val activityDate: String = "",
    val startedAtMillis: Long = 0L,
    val endedAtMillis: Long = 0L,
    val avgFormScore: Long = 0L,
    val avgSymmetryPercent: Long = 100L,
    val routeJson: String = "",
    val workoutSubType: String = "FREE",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toLocalSession(): WorkoutSession = WorkoutSession(
        id = id,
        userId = userId,
        sportType = SportType.fromNameSafe(sportType).name,
        durationSeconds = durationSeconds.toInt().coerceAtLeast(0),
        caloriesBurned = caloriesBurned.toInt().coerceAtLeast(0),
        steps = steps.toInt().coerceAtLeast(0),
        distanceMeters = distanceMeters.toInt().coerceAtLeast(0),
        repsCount = repsCount.toInt().coerceAtLeast(0),
        trackingMode = TrackingMode.fromNameSafe(trackingMode).name,
        motionActiveSeconds = motionActiveSeconds.toInt().coerceAtLeast(0),
        activityDate = activityDate,
        startedAtMillis = startedAtMillis,
        endedAtMillis = endedAtMillis,
        avgFormScore = avgFormScore.toInt().coerceIn(0, 100),
        avgSymmetryPercent = avgSymmetryPercent.toInt().coerceIn(0, 100),
        routeJson = routeJson,
        workoutSubType = if (workoutSubType == "HIIT") "HIIT" else "FREE"
    )
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MoveCloudRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireSignedInUser(expectedUserId: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: error("กรุณาเข้าสู่ระบบด้วยบัญชี Google ก่อนดำเนินการ")
        if (expectedUserId != null) {
            check(uid == expectedUserId) { "บัญชีผู้ใช้มีการเปลี่ยนแปลง กรุณาลองใหม่อีกครั้ง" }
        }
        return uid
    }

    fun observeCloudProfile(userId: String): Flow<UserCloudProfile?> =
        db.collection("users")
            .document(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        UserCloudProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }

    fun observeCloudWorkouts(userId: String): Flow<List<WorkoutSession>> =
        db.collection("users")
            .document(userId)
            .collection("workouts")
            .orderBy("startedAtMillis", Query.Direction.DESCENDING)
            .snapshots()
            .map { querySnapshot ->
                querySnapshot.toObjects(
                    WorkoutCloudSession::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).map { it.toLocalSession() }
            }

    suspend fun getCloudProfileOnce(userId: String): Result<UserCloudProfile?> {
        val path = "users/$userId"
        return try {
            requireSignedInUser(userId)
            val snap = db.collection("users").document(userId).get().await()
            val profile = if (snap.exists()) {
                snap.toObject(
                    UserCloudProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            } else {
                null
            }
            Result.success(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun getCloudWorkoutsOnce(userId: String): Result<List<WorkoutSession>> {
        val path = "users/$userId/workouts"
        return try {
            requireSignedInUser(userId)
            val snaps = db.collection("users")
                .document(userId)
                .collection("workouts")
                .orderBy("startedAtMillis", Query.Direction.DESCENDING)
                .get()
                .await()
            val list = snaps.toObjects(
                WorkoutCloudSession::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).map { it.toLocalSession() }
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun registerOrUpdateProfile(
        userId: String,
        displayName: String,
        email: String?,
        weightKg: Float,
        heightCm: Float? = null,
        dailyGoalMinutes: Int,
        currentStreak: Int = 0,
        longestStreak: Int = 0,
        lastActiveDate: String = "",
        onboardingCompleted: Boolean = true
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireSignedInUser(userId)
            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            val safeName = displayName.trim().take(80).ifBlank { "นักกีฬา MOVE" }
            val safeWeight = weightKg.coerceIn(25f, 250f).toDouble()
            val safeHeight = heightCm?.coerceIn(120f, 220f)?.toDouble()
            val safeGoal = dailyGoalMinutes.coerceIn(0, 300)
            val safeEmail = email?.trim()?.take(160)?.takeIf { it.isNotBlank() }

            if (!existing.exists()) {
                val createPayload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to safeName,
                    "dailyGoalMinutes" to safeGoal,
                    "weightKg" to safeWeight,
                    "heightCm" to (safeHeight ?: 170.0),
                    "currentStreak" to currentStreak.coerceAtLeast(0),
                    "longestStreak" to longestStreak.coerceAtLeast(0),
                    "lastActiveDate" to lastActiveDate.take(20),
                    "onboardingCompleted" to onboardingCompleted,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (safeEmail != null) {
                    createPayload["email"] = safeEmail
                }
                docRef.set(createPayload).await()
            } else {
                val updatePayload = mutableMapOf<String, Any>(
                    "displayName" to safeName,
                    "dailyGoalMinutes" to safeGoal,
                    "weightKg" to safeWeight,
                    "onboardingCompleted" to onboardingCompleted,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (safeHeight != null) {
                    updatePayload["heightCm"] = safeHeight
                }
                if (safeEmail != null) {
                    updatePayload["email"] = safeEmail
                }
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun syncStreakFields(
        userId: String,
        currentStreak: Int,
        longestStreak: Int,
        lastActiveDate: String
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireSignedInUser(userId)
            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "currentStreak" to currentStreak.coerceIn(0, 10000),
                        "longestStreak" to longestStreak.coerceIn(0, 10000),
                        "lastActiveDate" to lastActiveDate.take(20),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveWorkout(userId: String, session: WorkoutSession): Result<Unit> {
        val safeId = session.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128)
        require(safeId.isNotBlank()) { "รหัสกิจกรรมไม่ถูกต้อง" }
        val path = "users/$userId/workouts/$safeId"
        return try {
            val uid = requireSignedInUser(userId)
            val docRef = db.collection("users").document(uid)
                .collection("workouts").document(safeId)

            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "sportType" to session.sport.name,
                "durationSeconds" to session.durationSeconds.coerceIn(5, 86400),
                "caloriesBurned" to session.caloriesBurned.coerceIn(0, 20000),
                "steps" to session.steps.coerceIn(0, 500000),
                "distanceMeters" to session.distanceMeters.coerceIn(0, 500000),
                "repsCount" to session.repsCount.coerceIn(0, 50000),
                "trackingMode" to session.mode.name,
                "motionActiveSeconds" to session.motionActiveSeconds.coerceIn(0, 86400),
                "activityDate" to session.activityDate.ifBlank { "1970-01-01" }.take(20),
                "startedAtMillis" to session.startedAtMillis.coerceAtLeast(0L),
                "endedAtMillis" to session.endedAtMillis.coerceAtLeast(0L),
                "avgFormScore" to session.avgFormScore.coerceIn(0, 100),
                "avgSymmetryPercent" to session.avgSymmetryPercent.coerceIn(0, 100),
                "routeJson" to session.routeJson.take(65536),
                "workoutSubType" to if (session.workoutSubType.equals("HIIT", ignoreCase = true)) "HIIT" else "FREE",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteWorkout(userId: String, workoutId: String): Result<Unit> {
        val safeId = workoutId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128)
        val path = "users/$userId/workouts/$safeId"
        return try {
            val uid = requireSignedInUser(userId)
            db.collection("users").document(uid)
                .collection("workouts").document(safeId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
