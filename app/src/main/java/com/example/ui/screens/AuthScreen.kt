package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.ui.theme.EmeraldVibrant
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.ForestGreenDeep
import com.example.ui.theme.ForestGreenMedium
import com.example.ui.theme.MintBright
import com.example.ui.viewmodel.AuthModeTab
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val AUTH_TAG = "MoveAuthScreen"

@Composable
fun AuthScreen(
    selectedTab: AuthModeTab,
    isBusy: Boolean,
    errorMessage: String?,
    onSelectTab: (AuthModeTab) -> Unit,
    onGoogleIdTokenReceived: (
        idToken: String,
        isRegisterFlow: Boolean,
        prefilledName: String?,
        prefilledWeight: Float?,
        prefilledHeight: Float?,
        prefilledGoalMinutes: Int?
    ) -> Unit,
    onGoogleWebOAuthRequested: (
        activity: Activity,
        isRegisterFlow: Boolean,
        prefilledName: String?,
        prefilledWeight: Float?,
        prefilledHeight: Float?,
        prefilledGoalMinutes: Int?
    ) -> Unit = { _, _, _, _, _, _ -> },
    onAuthError: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activityContext = remember(context) { context.findActivity() ?: context }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val credentialManager = remember(activityContext) { CredentialManager.create(activityContext) }

    var registerName by remember { mutableStateOf("") }
    var registerWeight by remember { mutableStateOf("65") }
    var registerHeight by remember { mutableStateOf("170") }
    var registerGoalMinutes by remember { mutableIntStateOf(0) }
    var attemptedSilentSignIn by remember { mutableStateOf(false) }
    var silentSignInJob by remember { mutableStateOf<Job?>(null) }
    var isLaunchingSheet by remember { mutableStateOf(false) }
    val effectiveBusy = isBusy || isLaunchingSheet

    LaunchedEffect(errorMessage) {
        if (!errorMessage.isNullOrBlank()) {
            scrollState.animateScrollTo(0)
        }
    }

    // Silent auto-login on cold launch if user already authorized this device
    LaunchedEffect(Unit) {
        if (!attemptedSilentSignIn && !effectiveBusy) {
            attemptedSilentSignIn = true
            val serverClientId = resolveDefaultWebClientId(context)
            if (serverClientId.isNotBlank()) {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(true)
                    .setServerClientId(serverClientId)
                    .setAutoSelectEnabled(true)
                    .build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()
                silentSignInJob = launch {
                    runCatching {
                        val response = credentialManager.getCredential(activityContext, request)
                        if (!isLaunchingSheet && !isBusy) {
                            extractGoogleIdToken(response.credential)?.let { token ->
                                onGoogleIdTokenReceived(token, false, null, null, null, null)
                            }
                        }
                    }
                }
            }
        }
    }

    fun launchWebOAuthFallback(isRegister: Boolean) {
        if (effectiveBusy) return
        val activity = activityContext as? Activity
        if (activity == null) {
            onAuthError("ไม่พบ Activity สำหรับเปิดหน้าต่างเข้าสู่ระบบ Google")
            return
        }
        silentSignInJob?.cancel()
        onAuthError(null)
        onGoogleWebOAuthRequested(
            activity,
            isRegister,
            registerName.takeIf { isRegister && it.isNotBlank() },
            if (isRegister) registerWeight.toFloatOrNull() else null,
            if (isRegister) registerHeight.toFloatOrNull() else null,
            if (isRegister) registerGoalMinutes else null
        )
    }

    fun deliverCredentialToken(idToken: String, isRegister: Boolean) {
        onGoogleIdTokenReceived(
            idToken,
            isRegister,
            registerName.takeIf { isRegister && it.isNotBlank() },
            if (isRegister) registerWeight.toFloatOrNull() else null,
            if (isRegister) registerHeight.toFloatOrNull() else null,
            if (isRegister) registerGoalMinutes else null
        )
    }

    fun launchInteractiveGoogleAuth(isRegister: Boolean) {
        if (effectiveBusy) return
        silentSignInJob?.cancel()
        scope.launch {
            isLaunchingSheet = true
            onAuthError(null)
            try {
                val serverClientId = resolveDefaultWebClientId(context)
                if (serverClientId.isBlank()) {
                    onAuthError("ไม่พบการตั้งค่า Firebase (google-services.json) สำหรับชุดติดตั้งนี้")
                    return@launch
                }
                val signInOption = GetSignInWithGoogleOption.Builder(serverClientId).build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(signInOption)
                    .build()
                val response = credentialManager.getCredential(activityContext, request)
                val idToken = extractGoogleIdToken(response.credential)
                if (idToken != null) {
                    deliverCredentialToken(idToken, isRegister)
                } else {
                    onAuthError("ไม่พบโทเค็นจากบัญชี Google กรุณาลองใหม่อีกครั้ง")
                }
            } catch (e: GetCredentialCancellationException) {
                val rawMsg = (e.errorMessage?.toString() ?: e.message ?: "").trim()
                Log.w(AUTH_TAG, "Google Sign-In cancelled or dismissed: $rawMsg", e)
                val hint = when {
                    rawMsg.contains("Account reauth failed", ignoreCase = true) ||
                        rawMsg.contains("28444") ->
                        "ลายเซ็น SHA-1 ของ APK นี้ไม่ตรงกับ Google Sign-In ใน Firebase ($rawMsg) กรุณาใช้ APK ที่เซ็นด้วย keystore เดิมหรือเพิ่ม SHA-1 ใน Firebase"
                    rawMsg.isNotBlank() && !rawMsg.contains("cancelled by the user", ignoreCase = true) ->
                        "ยกเลิกหรือไม่สามารถยืนยันบัญชี Google ($rawMsg) กรุณาลองเลือกบัญชีอีกครั้ง หรือใช้วิธีเข้าสู่ระบบผ่านเบราว์เซอร์ด้านล่าง"
                    else ->
                        "การเลือกบัญชี Google ถูกยกเลิกหรือระบบปฏิเสธคำขอ หากเลือกบัญชีแล้วเด้งออก ให้กดปุ่ม \"เข้าสู่ระบบผ่านเบราว์เซอร์\" ด้านล่าง"
                }
                onAuthError(hint)
            } catch (e: NoCredentialException) {
                Log.w(AUTH_TAG, "Primary Sign-In found no credential, retrying with GetGoogleIdOption", e)
                try {
                    val serverClientId = resolveDefaultWebClientId(context)
                    val fallbackOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(serverClientId)
                        .setAutoSelectEnabled(false)
                        .build()
                    val fallbackRequest = GetCredentialRequest.Builder()
                        .addCredentialOption(fallbackOption)
                        .build()
                    val response = credentialManager.getCredential(activityContext, fallbackRequest)
                    val idToken = extractGoogleIdToken(response.credential)
                    if (idToken != null) {
                        deliverCredentialToken(idToken, isRegister)
                    } else {
                        onAuthError("ไม่พบโทเค็นจากบัญชี Google กรุณาลองเข้าสู่ระบบผ่านเบราว์เซอร์")
                    }
                } catch (fallbackEx: Exception) {
                    Log.w(AUTH_TAG, "Secondary GetGoogleIdOption failed", fallbackEx)
                    onAuthError("ไม่พบบัญชี Google ที่พร้อมใช้งานจากระบบเครื่อง กรุณากดเข้าสู่ระบบผ่านเบราว์เซอร์ด้านล่าง")
                }
            } catch (e: GetCredentialException) {
                Log.w(AUTH_TAG, "Credential Manager error: ${e.type} - ${e.errorMessage}", e)
                val detail = e.errorMessage?.toString()?.takeIf { it.isNotBlank() }
                    ?: e.localizedMessage
                    ?: e.type
                if (detail.contains("28444") || detail.contains("Developer console", ignoreCase = true)) {
                    onAuthError("ลายเซ็น SHA-1 ของ APK ยังไม่ตรงกับ Firebase ($detail) กรุณาใช้ APK จาก AI Studio หรือเพิ่ม DEBUG_KEYSTORE_BASE64 ใน GitHub Secrets")
                } else {
                    onAuthError("ไม่สามารถเชื่อมต่อ Google Sign-In ($detail) กรุณาลองเข้าสู่ระบบผ่านเบราว์เซอร์ด้านล่าง")
                }
            } catch (e: Exception) {
                Log.w(AUTH_TAG, "Unexpected auth error", e)
                onAuthError("เกิดข้อผิดพลาดในการเข้าสู่ระบบ: ${e.localizedMessage ?: "กรุณาลองอีกครั้ง"}")
            } finally {
                isLaunchingSheet = false
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ForestGreenDeep,
                        ForestGreen,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Hero Logo & Artwork
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White.copy(alpha = 0.14f),
                    border = BorderStroke(1.5.dp, MintBright.copy(alpha = 0.5f)),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.DirectionsRun,
                            contentDescription = "MOVE",
                            tint = MintBright,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Text(
                    text = "MOVE",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Smart Sports & Activity Tracker",
                    style = MaterialTheme.typography.titleSmall,
                    color = MintBright
                )
                Text(
                    text = "AI ตรวจจับท่าออกกำลังกาย นับครั้งอัตโนมัติ และซิงก์สถิติอย่างปลอดภัยบนคลาวด์",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.84f),
                    textAlign = TextAlign.Center
                )
            }

            // Main Auth Card (Login / Register tabs)
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_mode_tabs")
                    ) {
                        AuthModeTab.entries.forEachIndexed { index, tab ->
                            SegmentedButton(
                                selected = selectedTab == tab,
                                onClick = { onSelectTab(tab) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = AuthModeTab.entries.size
                                ),
                                icon = {
                                    Icon(
                                        imageVector = if (tab == AuthModeTab.LOGIN) {
                                            Icons.Rounded.Login
                                        } else {
                                            Icons.Rounded.PersonAdd
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier.testTag("auth_tab_${tab.name}")
                            ) {
                                Text(tab.thaiLabel, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_error_banner")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = errorMessage,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                OutlinedButton(
                                    onClick = { launchWebOAuthFallback(isRegister = selectedTab == AuthModeTab.REGISTER) },
                                    enabled = !effectiveBusy,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.45f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("google_web_oauth_banner_button")
                                ) {
                                    Text(
                                        text = "เข้าสู่ระบบด้วย Google ผ่านเบราว์เซอร์",
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    AnimatedContent(
                        targetState = selectedTab,
                        label = "auth_tab_content"
                    ) { tab ->
                        if (tab == AuthModeTab.LOGIN) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "ยินดีต้อนรับกลับมา!",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "เข้าสู่ระบบเพื่อเรียกคืนประวัติการออกกำลังกาย Streak เลเวล XP และโปรไฟล์ของคุณจากคลาวด์",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Image(
                                    painter = painterResource(id = R.drawable.img_hero_banner),
                                    contentDescription = stringResource(R.string.welcome_hero_desc),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(112.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                )

                                AuthFeatureHighlights()

                                Button(
                                    onClick = { launchInteractiveGoogleAuth(isRegister = false) },
                                    enabled = !effectiveBusy,
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("google_sign_in_button")
                                ) {
                                    if (effectiveBusy) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "กำลังเข้าสู่ระบบ...",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        Icon(Icons.Rounded.Login, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "เข้าสู่ระบบด้วย Google",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                OutlinedButton(
                                    onClick = { launchWebOAuthFallback(isRegister = false) },
                                    enabled = !effectiveBusy,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("google_web_oauth_login_button")
                                ) {
                                    Text(
                                        text = "เลือกบัญชีแล้วเข้าไม่ได้? ลองผ่านเบราว์เซอร์",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "ยังไม่มีบัญชี?",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TextButton(onClick = { onSelectTab(AuthModeTab.REGISTER) }) {
                                        Text("สมัครสมาชิกใหม่", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            // REGISTER TAB: Collect athlete profile info + sign up via Google Identity
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "สร้างบัญชีนักกีฬาใหม่",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "กรอกข้อมูลเบื้องต้นเพื่อคำนวณแคลอรีและระยะก้าวให้แม่นยำ แล้วเชื่อมกับบัญชี Google เพื่อสมัครสมาชิกทันที",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                OutlinedTextField(
                                    value = registerName,
                                    onValueChange = { registerName = it.take(50) },
                                    label = { Text("ชื่อที่ใช้แสดง (ไม่บังคับ)") },
                                    placeholder = { Text("เช่น ก้อง MOVE") },
                                    leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("register_name_input")
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = registerWeight,
                                        onValueChange = { raw ->
                                            registerWeight = raw.filter { it.isDigit() || it == '.' }.take(5)
                                        },
                                        label = { Text("น้ำหนัก (กก.)") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("register_weight_input")
                                    )
                                    OutlinedTextField(
                                        value = registerHeight,
                                        onValueChange = { raw ->
                                            registerHeight = raw.filter { it.isDigit() }.take(3)
                                        },
                                        label = { Text("ส่วนสูง (ซม.)") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("register_height_input")
                                    )
                                }

                                Text(
                                    text = if (registerGoalMinutes <= 0) {
                                        "เป้าหมายรายวัน: เวลาอิสระ (แนะนำ — ไม่กำหนดขั้นต่ำ)"
                                    } else {
                                        "เป้าหมายรายวัน: $registerGoalMinutes นาที/วัน"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(0, 20, 45).forEach { goal ->
                                        FilterChip(
                                            selected = registerGoalMinutes == goal,
                                            onClick = { registerGoalMinutes = goal },
                                            label = {
                                                Text(if (goal == 0) "เวลาอิสระ" else "${goal}น.")
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                Button(
                                    onClick = { launchInteractiveGoogleAuth(isRegister = true) },
                                    enabled = !effectiveBusy,
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldVibrant),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .testTag("google_register_button")
                                ) {
                                    if (effectiveBusy) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "กำลังสมัครสมาชิก...",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        Icon(Icons.Rounded.PersonAdd, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "สมัครสมาชิกด้วย Google",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                OutlinedButton(
                                    onClick = { launchWebOAuthFallback(isRegister = true) },
                                    enabled = !effectiveBusy,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("google_web_oauth_register_button")
                                ) {
                                    Text(
                                        text = "เลือกบัญชีแล้วเข้าไม่ได้? ลองผ่านเบราว์เซอร์",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "มีบัญชีอยู่แล้ว?",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TextButton(onClick = { onSelectTab(AuthModeTab.LOGIN) }) {
                                        Text("เข้าสู่ระบบ", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AccountRegistrationScreen(
    defaultName: String,
    email: String?,
    isBusy: Boolean,
    errorMessage: String?,
    onCompleteRegistration: (name: String, weightKg: Float, heightCm: Float, dailyGoalMinutes: Int) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var nameInput by remember(defaultName) { mutableStateOf(defaultName) }
    var weightInput by remember { mutableStateOf("65") }
    var heightInput by remember { mutableStateOf("170") }
    var goalMinutes by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ForestGreenDeep, ForestGreenMedium, MaterialTheme.colorScheme.background)
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .widthIn(max = 520.dp)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FitnessCenter,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column {
                        Text(
                            text = "ตั้งค่าบัญชีสมาชิกใหม่",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (!email.isNullOrBlank()) {
                            Text(
                                text = email,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Text(
                    text = "กรอกข้อมูลร่างกายเพื่อตั้งค่าโปรไฟล์และซิงก์บัญชีนักกีฬาของคุณบน Cloud Firestore",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it.take(50) },
                    label = { Text("ชื่อที่แสดงในแอป") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { raw -> weightInput = raw.filter { it.isDigit() || it == '.' }.take(5) },
                        label = { Text("น้ำหนัก (กก.)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = heightInput,
                        onValueChange = { raw -> heightInput = raw.filter { it.isDigit() }.take(3) },
                        label = { Text("ส่วนสูง (ซม.)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = if (goalMinutes <= 0) {
                        "รูปแบบเป้าหมาย: เวลาอิสระ (ไม่มีขั้นต่ำ)"
                    } else {
                        "เป้าหมายรายวัน: $goalMinutes นาที/วัน"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Slider(
                    value = goalMinutes.toFloat(),
                    onValueChange = { goalMinutes = ((it / 5f).roundToInt() * 5).coerceIn(0, 180) },
                    valueRange = 0f..180f
                )

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = {
                        onCompleteRegistration(
                            nameInput,
                            weightInput.toFloatOrNull() ?: 65f,
                            heightInput.toFloatOrNull() ?: 170f,
                            goalMinutes
                        )
                    },
                    enabled = !isBusy,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("complete_registration_button")
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ยืนยันสมัครสมาชิกและเริ่มใช้งาน",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                OutlinedButton(
                    onClick = onSignOut,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("เปลี่ยนบัญชี Google อื่น")
                }
            }
        }
    }
}

@Composable
private fun AuthFeatureHighlights() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = EmeraldVibrant, modifier = Modifier.size(18.dp))
                Text("AI จับท่าและนับจำนวนครั้งแบบเรียลไทม์", style = MaterialTheme.typography.bodySmall)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.CloudDone, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(18.dp))
                Text("ซิงก์ประวัติ ก้าว Streak และเลเวลขึ้นคลาวด์อัตโนมัติ", style = MaterialTheme.typography.bodySmall)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(18.dp))
                Text("เข้าสู่ระบบปลอดภัยด้วย Google Account", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun resolveDefaultWebClientId(context: Context): String {
    val resId = context.resources.getIdentifier(
        "default_web_client_id",
        "string",
        context.packageName
    )
    return if (resId != 0) context.getString(resId).trim() else ""
}

private fun extractGoogleIdToken(credential: androidx.credentials.Credential): String? {
    if (credential is GoogleIdTokenCredential) {
        return credential.idToken
    }
    return if (credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
        runCatching {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        }.onFailure { err ->
            Log.w(AUTH_TAG, "Failed to parse GoogleIdTokenCredential", err)
        }.getOrNull()
    } else {
        Log.w(AUTH_TAG, "Unexpected credential type: ${credential::class.java.simpleName} / ${credential.type}")
        null
    }
}
