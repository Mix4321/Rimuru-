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
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsHandball
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsHandball
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.auroraGlowBackdrop
import com.example.ui.theme.MintBright
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
import com.example.ui.screens.AccountRegistrationScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.NearbySpotsScreen
import com.example.ui.screens.ProgressionBadgesScreen
import com.example.ui.screens.RecoveryAndPlanScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SportSelectionScreen
import com.example.ui.screens.StretchingScreen
import com.example.ui.screens.WorkoutScreen
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.ForestGreenDeep
import com.example.ui.theme.ForestGreenMedium
import com.example.ui.theme.MoveTheme
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
data object SportsRoute

@Serializable
data object NearbySpotsRoute

@Serializable
data object HistoryRoute

@Serializable
data object SettingsRoute

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
    DASHBOARD(
        labelRes = R.string.nav_dashboard,
        selectedIcon = Icons.Rounded.Dashboard,
        unselectedIcon = Icons.Outlined.Dashboard,
        routeObj = DashboardRoute,
        tag = "nav_tab_dashboard"
    ),
    SPORTS(
        labelRes = R.string.nav_sports,
        selectedIcon = Icons.Rounded.SportsHandball,
        unselectedIcon = Icons.Outlined.SportsHandball,
        routeObj = SportsRoute,
        tag = "nav_tab_sports"
    ),
    PLACES(
        labelRes = R.string.nav_places,
        selectedIcon = Icons.Rounded.Explore,
        unselectedIcon = Icons.Outlined.Explore,
        routeObj = NearbySpotsRoute,
        tag = "nav_tab_places"
    ),
    HISTORY(
        labelRes = R.string.nav_history,
        selectedIcon = Icons.Rounded.History,
        unselectedIcon = Icons.Outlined.History,
        routeObj = HistoryRoute,
        tag = "nav_tab_history"
    ),
    SETTINGS(
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Rounded.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        routeObj = SettingsRoute,
        tag = "nav_tab_settings"
    )
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val settingsStore = remember(context) { SettingsStore(context.applicationContext) }
    val rootSettings by settingsStore.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())

    val isDarkTheme = when (rootSettings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
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
                    AuthenticatedMoveShell(
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
                    colors = listOf(ForestGreenDeep, ForestGreenMedium, ForestGreen)
                )
            )
            .auroraGlowBackdrop(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.16f),
                modifier = Modifier
                    .size(80.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.DirectionsRun,
                        contentDescription = null,
                        tint = MintBright,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }
            Text(
                text = "MOVE",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold
            )
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = "กำลังเชื่อมต่อบัญชีนักกีฬาของคุณ...",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun AuthenticatedMoveShell(
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
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp
                    ) {
                        TopLevelTab.entries.forEach { tab ->
                            val selected = currentDest?.hierarchy?.any {
                                when (tab) {
                                    TopLevelTab.DASHBOARD -> it.hasRoute<DashboardRoute>()
                                    TopLevelTab.SPORTS -> it.hasRoute<SportsRoute>()
                                    TopLevelTab.PLACES -> it.hasRoute<NearbySpotsRoute>()
                                    TopLevelTab.HISTORY -> it.hasRoute<HistoryRoute>()
                                    TopLevelTab.SETTINGS -> it.hasRoute<SettingsRoute>()
                                }
                            } == true
                            val iconScale by animateFloatAsState(
                                targetValue = if (selected) 1.14f else 1f,
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
                                label = { Text(stringResource(tab.labelRes)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.testTag(tab.tag)
                            )
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
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.size(12.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.DirectionsRun,
                            contentDescription = "MOVE",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                        Text(
                            text = "MOVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.size(20.dp))
                        TopLevelTab.entries.forEach { tab ->
                            val selected = currentDest?.hierarchy?.any {
                                when (tab) {
                                    TopLevelTab.DASHBOARD -> it.hasRoute<DashboardRoute>()
                                    TopLevelTab.SPORTS -> it.hasRoute<SportsRoute>()
                                    TopLevelTab.PLACES -> it.hasRoute<NearbySpotsRoute>()
                                    TopLevelTab.HISTORY -> it.hasRoute<HistoryRoute>()
                                    TopLevelTab.SETTINGS -> it.hasRoute<SettingsRoute>()
                                }
                            } == true
                            val railScale by animateFloatAsState(
                                targetValue = if (selected) 1.14f else 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "rail_icon_scale"
                            )
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes),
                                        modifier = Modifier.graphicsLayer {
                                            scaleX = railScale
                                            scaleY = railScale
                                        }
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
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
                        exitTransition = {
                            fadeOut(animationSpec = tween(160))
                        },
                        popEnterTransition = {
                            fadeIn(animationSpec = tween(220)) +
                                scaleIn(initialScale = 0.98f, animationSpec = tween(220))
                        },
                        popExitTransition = {
                            fadeOut(animationSpec = tween(160))
                        },
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
                                    onStartExerciseClick = { navigateTopLevel(TopLevelTab.SPORTS) },
                                    onQuickStartSport = { sport ->
                                        navController.navigate(
                                            StretchingRoute(
                                                sportName = sport.name,
                                                trackingModeName = sport.defaultTrackingMode.name
                                            )
                                        )
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
                                    onViewHistory = { navigateTopLevel(TopLevelTab.HISTORY) },
                                    onOpenRecoveryPlanPage = {
                                        navController.navigate(RecoveryPlanRoute)
                                    },
                                    onOpenProgressionBadgesPage = {
                                        navController.navigate(ProgressionBadgesRoute)
                                    },
                                    onOpenNearbySpots = {
                                        navigateTopLevel(TopLevelTab.PLACES)
                                    }
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

                        composable<HistoryRoute> {
                            DashboardStateContent(state = dashboardState) { data ->
                                HistoryScreen(
                                    data = data,
                                    onDeleteWorkout = dashboardViewModel::deleteWorkout
                                )
                            }
                        }

                        composable<SettingsRoute> {
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                userEmail = authUser.email,
                                userPhotoUrl = authUser.photoUrl,
                                onSignOutClick = onSignOut
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
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "กำลังโหลดข้อมูล MOVE...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        is UiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
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
