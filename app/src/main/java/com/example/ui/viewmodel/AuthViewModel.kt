package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.MoveCloudRepository
import com.example.data.firebase.UserCloudProfile
import com.example.data.local.MoveRepository
import com.example.data.local.SettingsStore
import com.example.data.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

enum class AuthModeTab(val thaiLabel: String) {
    LOGIN("เข้าสู่ระบบ"),
    REGISTER("สมัครสมาชิกใหม่")
}

sealed interface AuthGateState {
    data object CheckingSession : AuthGateState
    data class SignedOut(val modeTab: AuthModeTab = AuthModeTab.LOGIN) : AuthGateState
    data class NeedsOnboarding(
        val uid: String,
        val defaultName: String,
        val email: String?,
        val photoUrl: String?
    ) : AuthGateState
    data class Authenticated(
        val uid: String,
        val displayName: String,
        val email: String?,
        val photoUrl: String?
    ) : AuthGateState
}

class AuthViewModel(
    private val cloudRepository: MoveCloudRepository,
    private val localRepository: MoveRepository,
    private val settingsStore: SettingsStore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _gateState = MutableStateFlow<AuthGateState>(AuthGateState.CheckingSession)
    val gateState: StateFlow<AuthGateState> = _gateState.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var preferredTab: AuthModeTab = AuthModeTab.LOGIN

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (user == null) {
            _isBusy.value = false
            _gateState.value = AuthGateState.SignedOut(preferredTab)
        } else {
            resolveAuthenticatedUser(
                uid = user.uid,
                googleName = user.displayName,
                email = user.email,
                photoUrl = user.photoUrl?.toString()
            )
        }
    }

    init {
        auth.addAuthStateListener(authStateListener)
    }

    fun selectModeTab(tab: AuthModeTab) {
        preferredTab = tab
        if (_gateState.value is AuthGateState.SignedOut) {
            _gateState.value = AuthGateState.SignedOut(tab)
        }
        _errorMessage.value = null
    }

    fun signInWithGoogleIdToken(
        idToken: String,
        isRegisterFlow: Boolean,
        prefilledName: String? = null,
        prefilledWeight: Float? = null,
        prefilledHeight: Float? = null,
        prefilledGoalMinutes: Int? = null
    ) {
        if (_isBusy.value) return
        preferredTab = if (isRegisterFlow) AuthModeTab.REGISTER else AuthModeTab.LOGIN
        _isBusy.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = auth.signInWithCredential(credential).await()
                val user = authResult.user ?: error("ไม่พบข้อมูลบัญชี Google หลังยืนยันตัวตน")

                if (isRegisterFlow && !prefilledName.isNullOrBlank()) {
                    // Registration tab already collected profile metrics cleanly; persist immediately
                    saveRegistrationAndSync(
                        uid = user.uid,
                        displayName = prefilledName,
                        email = user.email,
                        photoUrl = user.photoUrl?.toString(),
                        weightKg = prefilledWeight ?: 65f,
                        heightCm = prefilledHeight ?: 170f,
                        dailyGoalMinutes = prefilledGoalMinutes ?: 0
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Google credential sign-in failed", e)
                _isBusy.value = false
                _errorMessage.value = e.localizedMessage ?: "เข้าสู่ระบบไม่สำเร็จ กรุณาลองอีกครั้ง"
            }
        }
    }

    private fun resolveAuthenticatedUser(
        uid: String,
        googleName: String?,
        email: String?,
        photoUrl: String?
    ) {
        _gateState.value = AuthGateState.CheckingSession
        viewModelScope.launch {
            val profileResult = cloudRepository.getCloudProfileOnce(uid)
            val cloudProfile = profileResult.getOrNull()

            if (cloudProfile == null || !cloudProfile.onboardingCompleted) {
                if (preferredTab == AuthModeTab.REGISTER && _isBusy.value) {
                    // Registration callback will complete saving
                    return@launch
                }
                _isBusy.value = false
                _gateState.value = AuthGateState.NeedsOnboarding(
                    uid = uid,
                    defaultName = googleName?.takeIf { it.isNotBlank() } ?: "นักกีฬา MOVE",
                    email = email,
                    photoUrl = photoUrl
                )
            } else {
                hydrateLocalFromCloud(uid, cloudProfile)
                _isBusy.value = false
                _gateState.value = AuthGateState.Authenticated(
                    uid = uid,
                    displayName = cloudProfile.displayName,
                    email = email ?: cloudProfile.email,
                    photoUrl = photoUrl
                )
            }
        }
    }

    fun completeRegistration(
        displayName: String,
        weightKg: Float,
        heightCm: Float,
        dailyGoalMinutes: Int
    ) {
        val current = auth.currentUser ?: return
        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            saveRegistrationAndSync(
                uid = current.uid,
                displayName = displayName,
                email = current.email,
                photoUrl = current.photoUrl?.toString(),
                weightKg = weightKg,
                heightCm = heightCm,
                dailyGoalMinutes = dailyGoalMinutes
            )
        }
    }

    private suspend fun saveRegistrationAndSync(
        uid: String,
        displayName: String,
        email: String?,
        photoUrl: String?,
        weightKg: Float,
        heightCm: Float,
        dailyGoalMinutes: Int
    ) {
        val safeName = displayName.trim().take(80).ifBlank { "นักกีฬา MOVE" }
        val safeWeight = weightKg.coerceIn(25f, 250f)
        val safeHeight = heightCm.coerceIn(120f, 220f)
        val safeGoal = dailyGoalMinutes.coerceIn(0, 300)

        val localProfile = localRepository.getUserProfileOnce()
        val res = cloudRepository.registerOrUpdateProfile(
            userId = uid,
            displayName = safeName,
            email = email,
            weightKg = safeWeight,
            heightCm = safeHeight,
            dailyGoalMinutes = safeGoal,
            currentStreak = localProfile.currentStreak,
            longestStreak = localProfile.longestStreak,
            lastActiveDate = localProfile.lastActiveDate,
            onboardingCompleted = true
        )

        res.onSuccess {
            localRepository.updateProfile(safeName, safeWeight)
            localRepository.updateDailyGoal(safeGoal)
            settingsStore.updateSettings { it.copy(userHeightCm = safeHeight) }
            syncWorkoutsBidirectional(uid)
            _isBusy.value = false
            _gateState.value = AuthGateState.Authenticated(
                uid = uid,
                displayName = safeName,
                email = email,
                photoUrl = photoUrl
            )
        }.onFailure { err ->
            _isBusy.value = false
            _errorMessage.value = "บันทึกข้อมูลสมาชิกไม่สำเร็จ: ${err.localizedMessage ?: "ลองใหม่อีกครั้ง"}"
            _gateState.value = AuthGateState.NeedsOnboarding(
                uid = uid,
                defaultName = safeName,
                email = email,
                photoUrl = photoUrl
            )
        }
    }

    private suspend fun hydrateLocalFromCloud(uid: String, cloudProfile: UserCloudProfile) {
        runCatching {
            localRepository.updateProfile(
                newName = cloudProfile.displayName,
                weightKg = cloudProfile.weightKg.toFloat()
            )
            localRepository.updateDailyGoal(cloudProfile.dailyGoalMinutes.toInt())
            settingsStore.updateSettings {
                it.copy(userHeightCm = cloudProfile.heightCm.toFloat().coerceIn(120f, 220f))
            }
            syncWorkoutsBidirectional(uid)
        }
    }

    private suspend fun syncWorkoutsBidirectional(uid: String) {
        val cloudWorkouts = cloudRepository.getCloudWorkoutsOnce(uid).getOrElse { emptyList() }
        val localWorkouts = localRepository.getAllWorkoutsOnce()

        if (cloudWorkouts.isNotEmpty()) {
            localRepository.importWorkoutsFromBackup(cloudWorkouts, importedProfile = null)
        }
        val cloudIds = cloudWorkouts.map { it.id }.toSet()
        for (localItem in localWorkouts) {
            if (localItem.id !in cloudIds && localItem.durationSeconds >= 5) {
                cloudRepository.saveWorkout(uid, localItem)
            }
        }
    }

    fun signOut(onClearCredentialState: suspend () -> Unit = {}) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                runCatching { onClearCredentialState() }
                runCatching { localRepository.clearLocalAccountData() }
                auth.signOut()
            }
            _errorMessage.value = null
            _gateState.value = AuthGateState.SignedOut(AuthModeTab.LOGIN)
        }
    }

    fun setErrorMessage(msg: String?) {
        _errorMessage.value = msg
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authStateListener)
    }

    private companion object {
        const val TAG = "AuthViewModel"
    }
}
