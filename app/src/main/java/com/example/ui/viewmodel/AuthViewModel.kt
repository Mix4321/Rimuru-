package com.example.ui.viewmodel

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.MoveCloudRepository
import com.example.data.firebase.UserCloudProfile
import com.example.data.local.MoveRepository
import com.example.data.local.SettingsStore
import com.example.data.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private var activeResolutionUid: String? = null
    private var resolutionJob: Job? = null
    private var isCompletingInlineRegistration: Boolean = false

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (user == null) {
            resolutionJob?.cancel()
            resolutionJob = null
            activeResolutionUid = null
            isCompletingInlineRegistration = false
            _isBusy.value = false
            _gateState.value = AuthGateState.SignedOut(preferredTab)
        } else {
            val currentGate = _gateState.value
            if (currentGate is AuthGateState.Authenticated && currentGate.uid == user.uid) {
                return@AuthStateListener
            }
            if (activeResolutionUid == user.uid && resolutionJob?.isActive == true) {
                return@AuthStateListener
            }
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
        auth.pendingAuthResult?.addOnFailureListener { err ->
            Log.w(TAG, "Pending OAuth sign-in failed", err)
            _isBusy.value = false
            _errorMessage.value = err.localizedMessage ?: "เข้าสู่ระบบผ่านเบราว์เซอร์ไม่สำเร็จ"
        }
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
        isCompletingInlineRegistration = isRegisterFlow
        _isBusy.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = auth.signInWithCredential(credential).await()
                val user = authResult.user ?: error("ไม่พบข้อมูลบัญชี Google หลังยืนยันตัวตน")
                finalizeSignedInUser(
                    user = user,
                    isRegisterFlow = isRegisterFlow,
                    prefilledName = prefilledName,
                    prefilledWeight = prefilledWeight,
                    prefilledHeight = prefilledHeight,
                    prefilledGoalMinutes = prefilledGoalMinutes
                )
            } catch (e: CancellationException) {
                isCompletingInlineRegistration = false
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Google credential sign-in failed", e)
                isCompletingInlineRegistration = false
                _isBusy.value = false
                _errorMessage.value = e.localizedMessage ?: "เข้าสู่ระบบไม่สำเร็จ กรุณาลองอีกครั้ง"
            }
        }
    }

    fun signInWithGoogleWebOAuth(
        activity: Activity,
        isRegisterFlow: Boolean,
        prefilledName: String? = null,
        prefilledWeight: Float? = null,
        prefilledHeight: Float? = null,
        prefilledGoalMinutes: Int? = null
    ) {
        if (_isBusy.value) return
        preferredTab = if (isRegisterFlow) AuthModeTab.REGISTER else AuthModeTab.LOGIN
        isCompletingInlineRegistration = isRegisterFlow
        _isBusy.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val pending = auth.pendingAuthResult
                val authResult = if (pending != null) {
                    pending.await()
                } else {
                    val provider = OAuthProvider.newBuilder("google.com")
                        .addCustomParameter("prompt", "select_account")
                        .build()
                    auth.startActivityForSignInWithProvider(activity, provider).await()
                }
                val user = authResult.user ?: error("ไม่พบข้อมูลบัญชี Google หลังยืนยันตัวตน")
                finalizeSignedInUser(
                    user = user,
                    isRegisterFlow = isRegisterFlow,
                    prefilledName = prefilledName,
                    prefilledWeight = prefilledWeight,
                    prefilledHeight = prefilledHeight,
                    prefilledGoalMinutes = prefilledGoalMinutes
                )
            } catch (e: CancellationException) {
                isCompletingInlineRegistration = false
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Google Web OAuth sign-in failed", e)
                isCompletingInlineRegistration = false
                _isBusy.value = false
                val msg = e.localizedMessage.orEmpty()
                _errorMessage.value = if (msg.contains("package certificate hash", ignoreCase = true) ||
                    msg.contains("CERT_HASH", ignoreCase = true)
                ) {
                    "ลายเซ็น SHA-1 ของ APK นี้ยังไม่ตรงกับที่ลงทะเบียนไว้ใน Firebase (ต้องสร้าง APK ด้วย debug.keystore เดิมของโปรเจกต์ หรือเพิ่ม SHA-1 ใน Firebase Console)"
                } else {
                    "เข้าสู่ระบบผ่านเบราว์เซอร์ไม่สำเร็จ: ${msg.ifBlank { "กรุณาลองอีกครั้ง" }}"
                }
            }
        }
    }

    private suspend fun finalizeSignedInUser(
        user: FirebaseUser,
        isRegisterFlow: Boolean,
        prefilledName: String?,
        prefilledWeight: Float?,
        prefilledHeight: Float?,
        prefilledGoalMinutes: Int?
    ) {
        if (isRegisterFlow) {
            val resolvedName = prefilledName?.takeIf { it.isNotBlank() }
                ?: user.displayName?.takeIf { it.isNotBlank() }
                ?: "นักกีฬา MOVE"
            saveRegistrationAndSync(
                uid = user.uid,
                displayName = resolvedName,
                email = user.email,
                photoUrl = user.photoUrl?.toString(),
                weightKg = prefilledWeight ?: 65f,
                heightCm = prefilledHeight ?: 170f,
                dailyGoalMinutes = prefilledGoalMinutes ?: 0
            )
            isCompletingInlineRegistration = false
        } else if (activeResolutionUid != user.uid && _gateState.value !is AuthGateState.Authenticated) {
            resolveAuthenticatedUser(
                uid = user.uid,
                googleName = user.displayName,
                email = user.email,
                photoUrl = user.photoUrl?.toString()
            )
        }
    }

    private fun resolveAuthenticatedUser(
        uid: String,
        googleName: String?,
        email: String?,
        photoUrl: String?
    ) {
        resolutionJob?.cancel()
        activeResolutionUid = uid
        _gateState.value = AuthGateState.CheckingSession
        resolutionJob = viewModelScope.launch {
            val profileResult = cloudRepository.getCloudProfileOnce(uid)
            val cloudProfile = profileResult.getOrNull()

            if (isCompletingInlineRegistration) {
                // Inline registration flow in signInWithGoogleIdToken will finalize state
                return@launch
            }

            if (profileResult.isFailure) {
                val err = profileResult.exceptionOrNull()
                Log.w(TAG, "Failed to load cloud profile on sign-in", err)
                _isBusy.value = false
                _errorMessage.value = "เชื่อมต่อข้อมูลคลาวด์ไม่สำเร็จ: ${err?.localizedMessage ?: "กรุณาลองอีกครั้ง"}"
                _gateState.value = AuthGateState.NeedsOnboarding(
                    uid = uid,
                    defaultName = googleName?.takeIf { it.isNotBlank() } ?: "นักกีฬา MOVE",
                    email = email,
                    photoUrl = photoUrl
                )
                return@launch
            }

            if (cloudProfile == null || !cloudProfile.onboardingCompleted) {
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
        resolutionJob?.cancel()
        resolutionJob = null
        activeResolutionUid = null
        isCompletingInlineRegistration = false
        viewModelScope.launch {
            withContext(NonCancellable) {
                runCatching { onClearCredentialState() }
                runCatching { localRepository.clearLocalAccountData() }
                auth.signOut()
            }
            _isBusy.value = false
            _errorMessage.value = null
            _gateState.value = AuthGateState.SignedOut(AuthModeTab.LOGIN)
        }
    }

    fun setErrorMessage(msg: String?) {
        _errorMessage.value = msg
    }

    override fun onCleared() {
        super.onCleared()
        resolutionJob?.cancel()
        auth.removeAuthStateListener(authStateListener)
    }

    private companion object {
        const val TAG = "AuthViewModel"
    }
}
