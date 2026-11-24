package com.example

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.firebase.MoveCloudRepository
import com.example.data.firebase.WorkoutDraftStore
import com.example.data.local.AppSettings
import com.example.data.local.MoveRepository
import com.example.data.local.MoveRoomDatabase
import com.example.data.local.SettingsStore
import com.example.data.local.ThemeMode
import com.example.data.model.SportType
import com.example.data.model.TrackingMode
import com.example.data.model.UiState
import com.example.data.model.WorkoutCatalog
import com.example.ui.components.auroraGlowBackdrop
import com.example.ui.screens.AccountRegistrationScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.NearbySpotsScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ProgressScreen
import com.example.ui.screens.ProgressionBadgesScreen
import com.example.ui.screens.RecoveryAndPlanScreen
import com.example.ui.screens.SportSelectionScreen
import com.example.ui.screens.StretchingScreen
import com.example.ui.screens.WorkoutCatalogScreen
import com.example.ui.screens.WorkoutDetailScreen
import com.example.ui.screens.WorkoutScreen
import com.example.ui.theme.DeepForestGreen
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.MoveTheme
import com.example.ui.theme.NeonLime
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianSurface
import com.example.ui.viewmodel.AuthGateState
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.DashboardData
import com.example.ui.viewmodel.DashboardViewModel
import com.example.ui.viewmodel.NearbySpotsViewModel
import com.example.ui.viewmodel.SettingsViewModel
import com.example.ui.viewmodel.WorkoutTimerStyle
import com.example.ui.viewmodel.WorkoutViewModel
import kotlinx.serialization.Serializable

@Serializable
data object DashboardRoute

@Serializable
data object WorkoutCatalogRoute

@Serializable
data class WorkoutDetailRoute(val programId: String)

@Serializable
data object ProgressRoute

@Serializable
data object ProfileRoute

@Serializable
data object SportsRoute

@Serializable
data object NearbySpotsRoute

@Serializable
data object RecoveryPlanRoute

@Serializable
data object ProgressionBadgesRoute

@Serializable
data class StretchingRoute(
    val sportName: String,
    val trackingModeName: String = "",
    val isCoolDown: Boolean = false
)

@Serializable
data class WorkoutRoute(
    val sportName: String,
    val trackingModeName: String = "",
    val resumeDraft: Boolean = false,
    val isHiit: Boolean = false,
    val hiitWorkSec: Int = 30,
    val hiitRestSec: Int = 15,
    val hiitRounds: Int = 4
)

private enum class TopLevelTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val routeObj: Any,
    val tag: String
) {
    HOME(
        labelRes = R.string.nav_home,
        selectedIcon = Icons.Rounded.Home,
        unselectedIcon = Icons.Outlined.Home,
        routeObj = DashboardRoute,
        tag = "nav_tab_home"
    ),
    WORKOUT(
        labelRes = R.string.nav_workout,
        selectedIcon = Icons.Rounded.FitnessCenter,
        unselectedIcon = Icons.Outlined.FitnessCenter,
        routeObj = WorkoutCatalogRoute,
        tag = "nav_tab_workout"
    ),
    PROGRESS(
        labelRes = R.string.nav_progress,
        selectedIcon = Icons.Rounded.BarChart,
        unselectedIcon = Icons.Outlined.BarChart,
        routeObj = ProgressRoute,
        tag = "nav_tab_progress"
    ),
    PROFILE(
        labelRes = R.string.nav_profile,
        selectedIcon = Icons.Rounded.Person,
        unselectedIcon = Icons.Outlined.Person,
        routeObj = ProfileRoute,
        tag = "nav_tab_profile"
    )
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val settingsStore = remember(context) { SettingsStore(context.applicationContext) }
    val rootSettings by settingsStore.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())

    val isDarkTheme = when (rootSettings.themeMode) {
        ThemeMode.SYSTEM -> true
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val authViewModel: AuthViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val dao = MoveRoomDatabase.getInstance(app).moveDao()
                AuthViewModel(
                    cloudRepository = MoveCloudRepository(app),
                    localRepository = MoveRepository(dao),
                    settingsStore = SettingsStore(app)
                )
            }
        }
    )

    val gateState by authViewModel.gateState.collectAsStateWithLifecycle()
    val isAuthBusy by authViewModel.isBusy.collectAsStateWithLifecycle()
    val authErrorMessage by authViewModel.errorMessage.collectAsStateWithLifecycle()

    val performSignOut: () -> Unit = {
        authViewModel.signOut {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        }
    }

    MoveTheme(darkTheme = isDarkTheme) {
        when (val state = gateState) {
            is AuthGateState.CheckingSession -> {
                AuthCheckingSplash()
            }

            is AuthGateState.SignedOut -> {
                AuthScreen(
                    selectedTab = state.modeTab,
                    isBusy = isAuthBusy,
                    errorMessage = authErrorMessage,
                    onSelectTab = authViewModel::selectModeTab,
                    onGoogleIdTokenReceived = authViewModel::signInWithGoogleIdToken,
                    onGoogleWebOAuthRequested = authViewModel::signInWithGoogleWebOAuth,
                    onAuthError = authViewModel::setErrorMessage
                )
            }

            is AuthGateState.NeedsOnboarding -> {
                AccountRegistrationScreen(
                    defaultName = state.defaultName,
                    email = state.email,
                    isBusy = isAuthBusy,
                    errorMessage = authErrorMessage,
                    onCompleteRegistration = authViewModel::completeRegistration,
                    onSignOut = performSignOut
                )
            }

            is AuthGateState.Authenticated -> {
                key(state.uid) {
                    AuthenticatedGreenPulseShell(
                        authUser = state,
                        onSignOut = performSignOut
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthCheckingSplash() {
    val pulseTransition = rememberInfiniteTransition(label = "splash_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "splash_icon_pulse"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ObsidianBlack, DeepForestGreen, ObsidianBlack)
                )
            )
            .auroraGlowBackdrop(primaryGlow = NeonLime, secondaryGlow = EmeraldPulse),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = DeepForestGreen.copy(alpha = 0.72f),
                border = BorderStroke(1.5.dp, NeonLime.copy(alpha = 0.55f)),
                modifier = Modifier
                    .size(84.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.DirectionsRun,
                        contentDescription = null,
                        tint = NeonLime,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
            Text(
                text = "GREEN PULSE",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Train Hard. Live Strong.",
                style = MaterialTheme.typography.titleSmall,
                color = NeonLime
            )
            CircularProgressIndicator(
                color = EmeraldPulse,
                strokeWidth = 3.dp,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun AuthenticatedGreenPulseShell(
    authUser: AuthGateState.Authenticated,
    onSignOut: () -> Unit
) {
    val dashboardViewModel: DashboardViewModel = viewModel(
        key = "dashboard_${authUser.uid}",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val dao = MoveRoomDatabase.getInstance(app).moveDao()
                DashboardViewModel(
                    repository = MoveRepository(dao),
                    draftStore = WorkoutDraftStore(app),
                    cloudRepository = MoveCloudRepository(app),
                    currentUserId = authUser.uid
                )
            }
        }
    )

    val settingsViewModel: SettingsViewModel = viewModel(
        key = "settings_${authUser.uid}",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val dao = MoveRoomDatabase.getInstance(app).moveDao()
                SettingsViewModel(
                    settingsStore = SettingsStore(app),
                    repository = MoveRepository(dao),
                    cloudRepository = MoveCloudRepository(app),
                    currentUserId = authUser.uid
                )
            }
        }
    )

    val nearbySpotsViewModel: NearbySpotsViewModel = viewModel(
        key = "nearby_${authUser.uid}",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                NearbySpotsViewModel(application = app)
            }
        }
    )

    val appSettings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val settingsMessage by settingsViewModel.statusMessage.collectAsStateWithLifecycle()

    val dashboardState by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    val actionMessage by dashboardViewModel.actionMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(actionMessage) {
        val msg = actionMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            dashboardViewModel.clearActionMessage()
        }
    }

    LaunchedEffect(settingsMessage) {
        val msg = settingsMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            settingsViewModel.clearStatusMessage()
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination
    val hideBottomNavigation = currentDest?.hasRoute<WorkoutRoute>() == true ||
        currentDest?.hasRoute<WorkoutDetailRoute>() == true ||
        currentDest?.hasRoute<StretchingRoute>() == true ||
        currentDest?.hasRoute<RecoveryPlanRoute>() == true ||
        currentDest?.hasRoute<ProgressionBadgesRoute>() == true

    fun navigateTopLevel(tab: TopLevelTab) {
        navController.navigate(tab.routeObj) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 640.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useRail && !hideBottomNavigation) {
                    Column {
                        HorizontalDivider(color = EmeraldPulse.copy(alpha = 0.22f))
                        NavigationBar(
                            containerColor = ObsidianSurface,
                            tonalElevation = 0.dp
                        ) {
                            TopLevelTab.entries.forEach { tab ->
                                val selected = currentDest?.hierarchy?.any {
                                    when (tab) {
                                        TopLevelTab.HOME -> it.hasRoute<DashboardRoute>()
                                        TopLevelTab.WORKOUT -> it.hasRoute<WorkoutCatalogRoute>() || it.hasRoute<SportsRoute>() || it.hasRoute<NearbySpotsRoute>()
                                        TopLevelTab.PROGRESS -> it.hasRoute<ProgressRoute>()
                                        TopLevelTab.PROFILE -> it.hasRoute<ProfileRoute>()
                                    }
                                } == true
                                val iconScale by animateFloatAsState(
                                    targetValue = if (selected) 1.15f else 1f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    label = "nav_icon_scale"
                                )
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(tab) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes),
                                            modifier = Modifier.graphicsLayer {
                                                scaleX = iconScale
                                                scaleY = iconScale
                                            }
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(tab.labelRes),
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = ObsidianBlack,
                                        selectedTextColor = NeonLime,
                                        indicatorColor = NeonLime,
                                        unselectedIconColor = Color.White.copy(alpha = 0.6f),
                                        unselectedTextColor = Color.White.copy(alpha = 0.6f)
                                    ),
                                    modifier = Modifier.testTag(tab.tag)
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail && !hideBottomNavigation) {
                    NavigationRail(
                        containerColor = ObsidianSurface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.size(12.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.DirectionsRun,
                            contentDescription = "Green Pulse",
                            tint = NeonLime,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "PULSE",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonLime,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.size(20.dp))
                        TopLevelTab.entries.forEach { tab ->
                            val selected = currentDest?.hierarchy?.any {
                                when (tab) {
                                    TopLevelTab.HOME -> it.hasRoute<DashboardRoute>()
                                    TopLevelTab.WORKOUT -> it.hasRoute<WorkoutCatalogRoute>() || it.hasRoute<SportsRoute>() || it.hasRoute<NearbySpotsRoute>()
                                    TopLevelTab.PROGRESS -> it.hasRoute<ProgressRoute>()
                                    TopLevelTab.PROFILE -> it.hasRoute<ProfileRoute>()
                                }
                            } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ObsidianBlack,
                                    selectedTextColor = NeonLime,
                                    indicatorColor = NeonLime,
                                    unselectedIconColor = Color.White.copy(alpha = 0.6f),
                                    unselectedTextColor = Color.White.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = DashboardRoute,
                        enterTransition = {
                            fadeIn(animationSpec = tween(220)) +
                                scaleIn(initialScale = 0.97f, animationSpec = tween(220))
                        },
                        exitTransition = { fadeOut(animationSpec = tween(160)) },
                        popEnterTransition = {
                            fadeIn(animationSpec = tween(220)) +
                                scaleIn(initialScale = 0.98f, animationSpec = tween(220))
                        },
                        popExitTransition = { fadeOut(animationSpec = tween(160)) },
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 1040.dp)
                    ) {
                        composable<DashboardRoute> {
                            DashboardStateContent(state = dashboardState) { data ->
                                DashboardScreen(
                                    data = data,
                                    userEmail = authUser.email,
                                    userPhotoUrl = authUser.photoUrl,
                                    onStartExerciseClick = { navigateTopLevel(TopLevelTab.WORKOUT) },
                                    onQuickStartSport = { sport ->
                                        navController.navigate(
                                            StretchingRoute(
                                                sportName = sport.name,
                                                trackingModeName = sport.defaultTrackingMode.name
                                            )
                                        )
                                    },
                                    onOpenProgramDetail = { program ->
                                        navController.navigate(WorkoutDetailRoute(program.id))
                                    },
                                    onUpdateDailyGoal = dashboardViewModel::updateDailyGoal,
                                    onUpdateProfile = dashboardViewModel::updateProfile,
                                    onResumeDraft = { draft ->
                                        navController.navigate(
                                            WorkoutRoute(
                                                sportName = draft.sportType.name,
                                                trackingModeName = draft.trackingMode.name,
                                                resumeDraft = true
                                            )
                                        )
                                    },
                                    onDiscardDraft = dashboardViewModel::discardPendingDraft,
                                    onViewHistory = { navigateTopLevel(TopLevelTab.PROGRESS) },
                                    onOpenRecoveryPlanPage = { navController.navigate(RecoveryPlanRoute) },
                                    onOpenProgressionBadgesPage = { navController.navigate(ProgressionBadgesRoute) },
                                    onOpenNearbySpots = { navController.navigate(NearbySpotsRoute) }
                                )
                            }
                        }

                        composable<WorkoutCatalogRoute> {
                            WorkoutCatalogScreen(
                                onSelectProgram = { program ->
                                    navController.navigate(WorkoutDetailRoute(program.id))
                                },
                                onOpenLiveSportsPicker = { navController.navigate(SportsRoute) },
                                onOpenNearbySpots = { navController.navigate(NearbySpotsRoute) }
                            )
                        }

                        composable<WorkoutDetailRoute> { backStackEntry ->
                            val args = backStackEntry.toRoute<WorkoutDetailRoute>()
                            val program = WorkoutCatalog.byId(args.programId)
                            WorkoutDetailScreen(
                                program = program,
                                onBack = { navController.popBackStack() },
                                onStartWorkout = { selectedProgram ->
                                    navController.navigate(
                                        WorkoutRoute(
                                            sportName = selectedProgram.linkedSport.name,
                                            trackingModeName = selectedProgram.defaultTrackingMode.name,
                                            resumeDraft = false,
                                            isHiit = selectedProgram.isHiit,
                                            hiitWorkSec = selectedProgram.hiitWorkSec,
                                            hiitRestSec = selectedProgram.hiitRestSec,
                                            hiitRounds = selectedProgram.hiitRounds
                                        )
                                    )
                                },
                                onStartPreStretch = { selectedProgram ->
                                    navController.navigate(
                                        StretchingRoute(
                                            sportName = selectedProgram.linkedSport.name,
                                            trackingModeName = selectedProgram.defaultTrackingMode.name
                                        )
                                    )
                                }
                            )
                        }

                        composable<ProgressRoute> {
                            DashboardStateContent(state = dashboardState) { data ->
                                ProgressScreen(
                                    data = data,
                                    userHeightCm = appSettings.userHeightCm,
                                    onUpdateWeight = dashboardViewModel::updateProfile,
                                    onDeleteWorkout = dashboardViewModel::deleteWorkout
                                )
                            }
                        }

                        composable<ProfileRoute> {
                            DashboardStateContent(state = dashboardState) { data ->
                                ProfileScreen(
                                    data = data,
                                    settingsViewModel = settingsViewModel,
                                    userEmail = authUser.email,
                                    userPhotoUrl = authUser.photoUrl,
                                    onOpenAllBadges = { navController.navigate(ProgressionBadgesRoute) },
                                    onOpenFullHistory = { navigateTopLevel(TopLevelTab.PROGRESS) },
                                    onSignOutClick = onSignOut
                                )
                            }
                        }

                        composable<RecoveryPlanRoute> {
                            DashboardStateContent(state = dashboardState) { data ->
                                RecoveryAndPlanScreen(
                                    data = data,
                                    onBack = { navController.popBackStack() },
                                    onStartAdaptivePlan = { plan ->
                                        navController.navigate(
                                            WorkoutRoute(
                                                sportName = plan.suggestedSport.name,
                                                trackingModeName = plan.suggestedSport.defaultTrackingMode.name,
                                                resumeDraft = false,
                                                isHiit = plan.isHiitRecommended,
                                                hiitWorkSec = plan.suggestedWorkSec.coerceAtLeast(10),
                                                hiitRestSec = plan.suggestedRestSec.coerceAtLeast(5),
                                                hiitRounds = plan.suggestedRounds.coerceAtLeast(1)
                                            )
                                        )
                                    },
                                    onQuickStartSport = { sport ->
                                        navController.navigate(
                                            StretchingRoute(
                                                sportName = sport.name,
                                                trackingModeName = sport.defaultTrackingMode.name
                                            )
                                        )
                                    }
                                )
                            }
                        }

                        composable<ProgressionBadgesRoute> {
                            DashboardStateContent(state = dashboardState) { data ->
                                ProgressionBadgesScreen(
                                    progression = data.progression,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }

                        composable<SportsRoute> {
                            SportSelectionScreen(
                                onStartStretching = { sport, mode ->
                                    navController.navigate(
                                        StretchingRoute(
                                            sportName = sport.name,
                                            trackingModeName = mode.name
                                        )
                                    )
                                },
                                onBeginWorkout = { sport, mode ->
                                    navController.navigate(
                                        WorkoutRoute(
                                            sportName = sport.name,
                                            trackingModeName = mode.name,
                                            resumeDraft = false
                                        )
                                    )
                                }
                            )
                        }

                        composable<StretchingRoute> { backStackEntry ->
                            val routeArgs = backStackEntry.toRoute<StretchingRoute>()
                            val sport = SportType.fromNameSafe(routeArgs.sportName)
                            StretchingScreen(
                                sportType = sport,
                                appSettings = appSettings,
                                isCoolDown = routeArgs.isCoolDown,
                                onBack = { navController.popBackStack() },
                                onStartWorkoutNow = {
                                    if (routeArgs.isCoolDown) {
                                        navController.popBackStack(DashboardRoute, inclusive = false)
                                    } else {
                                        navController.navigate(
                                            WorkoutRoute(
                                                sportName = sport.name,
                                                trackingModeName = routeArgs.trackingModeName,
                                                resumeDraft = false
                                            )
                                        ) {
                                            popUpTo<StretchingRoute> { inclusive = true }
                                        }
                                    }
                                }
                            )
                        }

                        composable<NearbySpotsRoute> {
                            NearbySpotsScreen(
                                viewModel = nearbySpotsViewModel,
                                onStartWorkoutAtSpot = { sport ->
                                    navController.navigate(
                                        WorkoutRoute(
                                            sportName = sport.name,
                                            trackingModeName = sport.defaultTrackingMode.name,
                                            resumeDraft = false
                                        )
                                    )
                                }
                            )
                        }

                        composable<WorkoutRoute> { backStackEntry ->
                            val routeArgs = backStackEntry.toRoute<WorkoutRoute>()
                            val sport = SportType.fromNameSafe(routeArgs.sportName)
                            val workoutViewModel: WorkoutViewModel = viewModel(
                                key = "workout_${authUser.uid}_${backStackEntry.id}",
                                factory = viewModelFactory {
                                    initializer {
                                        val app = checkNotNull(this[APPLICATION_KEY])
                                        val dao = MoveRoomDatabase.getInstance(app).moveDao()
                                        WorkoutViewModel(
                                            repository = MoveRepository(dao),
                                            draftStore = WorkoutDraftStore(app),
                                            cloudRepository = MoveCloudRepository(app),
                                            currentUserId = authUser.uid
                                        )
                                    }
                                }
                            )

                            val currentData = (dashboardState as? UiState.Success)?.data
                            LaunchedEffect(backStackEntry.id) {
                                val draft = currentData?.pendingDraft
                                if (routeArgs.resumeDraft && draft != null) {
                                    workoutViewModel.restoreFromDraft(draft)
                                } else {
                                    val selectedMode = routeArgs.trackingModeName
                                        .takeIf { it.isNotBlank() }
                                        ?.let { TrackingMode.fromNameSafe(it) }
                                        ?: sport.defaultTrackingMode
                                    workoutViewModel.startFreshSession(
                                        sportType = sport,
                                        initialMode = selectedMode,
                                        timerStyle = if (routeArgs.isHiit) WorkoutTimerStyle.HIIT else WorkoutTimerStyle.FREE,
                                        hiitWorkSec = routeArgs.hiitWorkSec,
                                        hiitRestSec = routeArgs.hiitRestSec,
                                        hiitRounds = routeArgs.hiitRounds
                                    )
                                }
                            }

                            WorkoutScreen(
                                viewModel = workoutViewModel,
                                appSettings = appSettings,
                                onExitToDashboard = {
                                    navController.popBackStack(DashboardRoute, inclusive = false)
                                },
                                onStartCoolDown = { completedSport ->
                                    navController.navigate(
                                        StretchingRoute(
                                            sportName = completedSport.name,
                                            trackingModeName = "",
                                            isCoolDown = true
                                        )
                                    ) {
                                        popUpTo<WorkoutRoute> { inclusive = true }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardStateContent(
    state: UiState<DashboardData>,
    content: @Composable (DashboardData) -> Unit
) {
    when (state) {
        is UiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ObsidianBlack),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = NeonLime)
                    Text(
                        text = "กำลังโหลดข้อมูล Green Pulse...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.78f)
                    )
                }
            }
        }
        is UiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ObsidianBlack)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        is UiState.Success -> content(state.data)
    }
}
