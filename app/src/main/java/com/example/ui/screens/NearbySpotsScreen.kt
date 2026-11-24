package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsBasketball
import androidx.compose.material.icons.rounded.Straight
import androidx.compose.material.icons.rounded.TurnLeft
import androidx.compose.material.icons.rounded.TurnRight
import androidx.compose.material.icons.rounded.UTurnLeft
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.SportType
import com.example.data.remote.NavigationStep
import com.example.data.remote.NearbySpotCategory
import com.example.data.remote.NearbySpotsRepository
import com.example.data.remote.TravelMode
import com.example.data.remote.WorkoutSpot
import com.example.ui.components.LivePulseBeacon
import com.example.ui.components.NearbyNavigationMap
import com.example.ui.components.StaggeredEntrance
import com.example.ui.components.auroraGlowBackdrop
import com.example.ui.components.bounceClickable
import com.example.ui.components.shimmerSweepEffect
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.EmeraldVibrant
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.ForestGreenDeep
import com.example.ui.theme.MintBright
import com.example.ui.viewmodel.NearbySpotsUiState
import com.example.ui.viewmodel.NearbySpotsViewModel

@Composable
fun NearbySpotsScreen(
    viewModel: NearbySpotsViewModel,
    onStartWorkoutAtSpot: (SportType) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshPermissionAndLocationState()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissionAndLocationState()
    }

    val requestLocationPermissions = {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 720.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Header + Search + Radius & Category Filters
            NearbyHeaderControls(
                uiState = uiState,
                onSearchChange = viewModel::onSearchQueryChanged,
                onSelectSuggestion = viewModel::selectPlaceSuggestion,
                onResetGps = viewModel::resetToGpsLocation,
                onSelectRadius = viewModel::selectRadius,
                onSelectCategory = viewModel::selectCategory,
                onRefreshSpots = { viewModel.fetchNearbySpots() }
            )

            if (isWide) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    NearbyMapPanel(
                        uiState = uiState,
                        onSpotMarkerClick = { viewModel.selectSpot(it) },
                        onUserPanDetected = viewModel::onUserPanMap,
                        onToggleMapStyle = viewModel::toggleMapStyle,
                        onRecenter = viewModel::recenterOnUser,
                        onStopLiveNav = viewModel::stopLiveNavigation,
                        onRequestLocation = requestLocationPermissions,
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                    )

                    NearbySpotsListColumn(
                        uiState = uiState,
                        context = context,
                        onSelectSpot = { spot -> viewModel.selectSpot(spot) },
                        onNavigateSpot = { spot -> viewModel.selectSpot(spot, startNavigationImmediately = true) },
                        onClearSelection = viewModel::clearSelectedSpot,
                        onSelectTravelMode = viewModel::selectTravelMode,
                        onStartLiveNav = viewModel::startLiveNavigation,
                        onStopLiveNav = viewModel::stopLiveNavigation,
                        onStartWorkoutAtSpot = onStartWorkoutAtSpot,
                        onRequestLocation = requestLocationPermissions,
                        onExpandRadius = { viewModel.selectRadius(10_000) },
                        onRetryFetch = { viewModel.fetchNearbySpots() },
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxHeight()
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "nearby_map_panel") {
                        NearbyMapPanel(
                            uiState = uiState,
                            onSpotMarkerClick = { viewModel.selectSpot(it) },
                            onUserPanDetected = viewModel::onUserPanMap,
                            onToggleMapStyle = viewModel::toggleMapStyle,
                            onRecenter = viewModel::recenterOnUser,
                            onStopLiveNav = viewModel::stopLiveNavigation,
                            onRequestLocation = requestLocationPermissions,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (uiState.isNavigatingLive) 310.dp else 245.dp)
                        )
                    }

                    if (uiState.selectedSpot != null) {
                        item(key = "selected_spot_card") {
                            SelectedSpotNavigationCard(
                                spot = uiState.selectedSpot!!,
                                uiState = uiState,
                                context = context,
                                onClear = viewModel::clearSelectedSpot,
                                onSelectTravelMode = viewModel::selectTravelMode,
                                onStartLiveNav = viewModel::startLiveNavigation,
                                onStopLiveNav = viewModel::stopLiveNavigation,
                                onStartWorkoutAtSpot = { onStartWorkoutAtSpot(uiState.selectedSpot!!.suggestedSport) }
                            )
                        }
                    }

                    if (!uiState.hasLocationPermission && uiState.searchCenterLat == null) {
                        item(key = "location_permission_card") {
                            LocationPermissionPromptCard(onRequestLocation = requestLocationPermissions)
                        }
                    }

                    item(key = "spots_section_header") {
                        SpotsListSectionHeader(
                            uiState = uiState,
                            onExpandRadius = { viewModel.selectRadius(10_000) },
                            onRetryFetch = { viewModel.fetchNearbySpots() }
                        )
                    }

                    if (uiState.filteredSpots.isNotEmpty()) {
                        itemsIndexed(uiState.filteredSpots, key = { _, spot -> spot.id }) { index, spot ->
                            StaggeredEntrance(index = index) {
                                NearbySpotRowCard(
                                    spot = spot,
                                    isSelected = uiState.selectedSpot?.id == spot.id,
                                    onClick = { viewModel.selectSpot(spot) },
                                    onQuickNavigate = {
                                        viewModel.selectSpot(spot, startNavigationImmediately = true)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NearbyHeaderControls(
    uiState: NearbySpotsUiState,
    onSearchChange: (String) -> Unit,
    onSelectSuggestion: (com.example.data.remote.SearchPlaceSuggestion) -> Unit,
    onResetGps: () -> Unit,
    onSelectRadius: (Int) -> Unit,
    onSelectCategory: (NearbySpotCategory) -> Unit,
    onRefreshSpots: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "แหล่งออกกำลังกายใกล้ฉัน",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (uiState.searchAreaLabel != null) {
                        "กำลังค้นหารอบพื้นที่: ${uiState.searchAreaLabel}"
                    } else {
                        "รวบรวมสวน ลู่วิ่ง ฟิตเนส และสนามกีฬาที่ใกล้ที่สุดพร้อมนำทาง"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (uiState.searchAreaLabel != null) {
                TextButton(
                    onClick = onResetGps,
                    modifier = Modifier.testTag("reset_to_gps_button")
                ) {
                    Icon(Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ตำแหน่งฉัน", fontWeight = FontWeight.Bold)
                }
            } else {
                IconButton(
                    onClick = onRefreshSpots,
                    enabled = !uiState.isLoadingSpots && uiState.activeCenterLat != null,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("refresh_nearby_spots_button")
                ) {
                    if (uiState.isLoadingSpots) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "อัปเดตสถานที่ใกล้ฉัน",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("ค้นหาชื่อสถานที่ ย่าน หรือสวนสาธารณะ...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "ล้างคำค้นหา")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("nearby_search_input")
        )

        AnimatedVisibility(visible = uiState.placeSuggestions.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    uiState.placeSuggestions.take(4).forEachIndexed { idx, suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClickable { onSelectSuggestion(suggestion) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = suggestion.shortName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = suggestion.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (idx < uiState.placeSuggestions.take(4).lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        }

        // Category + Radius Filter Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            items(listOf(2000, 5000, 10000), key = { "rad_$it" }) { radius ->
                val selected = uiState.radiusMeters == radius
                FilterChip(
                    selected = selected,
                    onClick = { onSelectRadius(radius) },
                    label = {
                        Text(
                            text = "รัศมี ${radius / 1000} กม.",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ForestGreen,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.testTag("radius_chip_$radius")
                )
            }

            items(NearbySpotCategory.entries, key = { it.name }) { category ->
                val selected = uiState.selectedCategory == category
                val count = if (category == NearbySpotCategory.ALL) {
                    uiState.allSpots.size
                } else {
                    uiState.allSpots.count { it.category == category }
                }
                FilterChip(
                    selected = selected,
                    onClick = { onSelectCategory(category) },
                    label = {
                        Text(
                            text = if (count > 0) "${category.thaiLabel} ($count)" else category.thaiLabel
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = categoryIcon(category),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("category_chip_${category.name}")
                )
            }
        }
    }
}

@Composable
private fun NearbyMapPanel(
    uiState: NearbySpotsUiState,
    onSpotMarkerClick: (WorkoutSpot) -> Unit,
    onUserPanDetected: () -> Unit,
    onToggleMapStyle: () -> Unit,
    onRecenter: () -> Unit,
    onStopLiveNav: () -> Unit,
    onRequestLocation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = ForestGreenDeep),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            NearbyNavigationMap(
                spots = uiState.filteredSpots,
                selectedSpot = uiState.selectedSpot,
                activeRoute = uiState.activeRoute,
                userLat = uiState.userLat,
                userLng = uiState.userLng,
                searchCenterLat = uiState.searchCenterLat,
                searchCenterLng = uiState.searchCenterLng,
                accuracyMeters = uiState.accuracyMeters,
                mapStyle = uiState.mapStyle,
                followUser = uiState.followUserOnMap,
                isNavigatingLive = uiState.isNavigatingLive,
                fitRouteTick = uiState.fitRouteTick,
                recenterTick = uiState.recenterTick,
                onSpotMarkerClick = onSpotMarkerClick,
                onUserPanDetected = onUserPanDetected,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("nearby_spots_map")
            )

            // Top overlay: Turn-by-Turn Live Banner OR Status Pill + Map Buttons
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.isNavigatingLive && uiState.selectedSpot != null) {
                    LiveTurnByTurnBanner(
                        spot = uiState.selectedSpot,
                        uiState = uiState,
                        onStop = onStopLiveNav
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = ForestGreenDeep.copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, MintBright.copy(alpha = 0.35f)),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LivePulseBeacon(
                                color = if (uiState.userLat != null) MintBright else AmberWarm,
                                isActive = uiState.isLocating || uiState.isNavigatingLive || uiState.userLat != null,
                                size = 10.dp
                            )
                            Text(
                                text = when {
                                    !uiState.hasLocationPermission && uiState.searchCenterLat == null ->
                                        "แตะเปิด GPS เพื่อหาสถานที่ใกล้สุด"
                                    uiState.isLoadingSpots -> "กำลังรวบรวมสถานที่ใกล้ฉัน..."
                                    uiState.filteredSpots.isNotEmpty() -> {
                                        val nearest = uiState.filteredSpots.first()
                                        "พบ ${uiState.filteredSpots.size} แห่ง • ใกล้สุด ${NearbySpotsRepository.formatSpotDistanceThai(nearest.distanceMeters)}"
                                    }
                                    uiState.userLat != null -> "ไม่พบสถานที่ในรัศมี ${uiState.radiusMeters / 1000} กม."
                                    else -> "กำลังจับตำแหน่ง GPS..."
                                },
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = onToggleMapStyle,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(ForestGreenDeep.copy(alpha = 0.88f))
                                .testTag("nearby_map_style_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Layers,
                                contentDescription = "สลับรูปแบบแผนที่ (${uiState.mapStyle.thaiLabel})",
                                tint = MintBright
                            )
                        }
                        IconButton(
                            onClick = {
                                if (!uiState.hasLocationPermission && uiState.userLat == null) {
                                    onRequestLocation()
                                } else {
                                    onRecenter()
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (uiState.followUserOnMap) ForestGreen.copy(alpha = 0.92f)
                                    else ForestGreenDeep.copy(alpha = 0.88f)
                                )
                                .testTag("nearby_map_recenter_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MyLocation,
                                contentDescription = "จัดตำแหน่งมาที่ตัวฉัน",
                                tint = if (uiState.followUserOnMap) MintBright else Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveTurnByTurnBanner(
    spot: WorkoutSpot,
    uiState: NearbySpotsUiState,
    onStop: () -> Unit
) {
    val currentStep = uiState.currentNavStep
    val remaining = uiState.remainingDistanceMeters ?: uiState.activeRoute?.totalDistanceMeters ?: spot.distanceMeters

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ForestGreenDeep.copy(alpha = 0.95f),
        border = BorderStroke(1.5.dp, MintBright.copy(alpha = 0.65f)),
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("live_navigation_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .auroraGlowBackdrop(primaryGlow = MintBright, secondaryGlow = EmeraldVibrant)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(EmeraldVibrant.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (uiState.hasArrived) {
                        Icons.Rounded.Flag
                    } else {
                        maneuverIcon(currentStep?.maneuverType, currentStep?.maneuverModifier)
                    },
                    contentDescription = null,
                    tint = MintBright,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (uiState.hasArrived) {
                        "ถึง ${spot.name} แล้ว!"
                    } else {
                        currentStep?.instructionThai ?: "มุ่งหน้าไปยัง ${spot.name}"
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val etaText = uiState.activeRoute?.let {
                    NearbySpotsRepository.formatEtaThai(it.durationSeconds)
                } ?: ""
                Text(
                    text = "เหลือระยะ ${NearbySpotsRepository.formatSpotDistanceThai(remaining)}" +
                        if (etaText.isNotBlank()) " • $etaText (${uiState.travelMode.thaiLabel})" else "",
                    color = MintBright,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            TextButton(
                onClick = onStop,
                modifier = Modifier.testTag("stop_live_navigation_button")
            ) {
                Text("หยุด", color = AmberWarm, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun NearbySpotsListColumn(
    uiState: NearbySpotsUiState,
    context: Context,
    onSelectSpot: (WorkoutSpot) -> Unit,
    onNavigateSpot: (WorkoutSpot) -> Unit,
    onClearSelection: () -> Unit,
    onSelectTravelMode: (TravelMode) -> Unit,
    onStartLiveNav: () -> Unit,
    onStopLiveNav: () -> Unit,
    onStartWorkoutAtSpot: (SportType) -> Unit,
    onRequestLocation: () -> Unit,
    onExpandRadius: () -> Unit,
    onRetryFetch: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (uiState.selectedSpot != null) {
            item(key = "wide_selected_spot") {
                SelectedSpotNavigationCard(
                    spot = uiState.selectedSpot,
                    uiState = uiState,
                    context = context,
                    onClear = onClearSelection,
                    onSelectTravelMode = onSelectTravelMode,
                    onStartLiveNav = onStartLiveNav,
                    onStopLiveNav = onStopLiveNav,
                    onStartWorkoutAtSpot = { onStartWorkoutAtSpot(uiState.selectedSpot.suggestedSport) }
                )
            }
        }

        if (!uiState.hasLocationPermission && uiState.searchCenterLat == null) {
            item(key = "wide_perm_prompt") {
                LocationPermissionPromptCard(onRequestLocation = onRequestLocation)
            }
        }

        item(key = "wide_section_header") {
            SpotsListSectionHeader(
                uiState = uiState,
                onExpandRadius = onExpandRadius,
                onRetryFetch = onRetryFetch
            )
        }

        itemsIndexed(uiState.filteredSpots, key = { _, spot -> spot.id }) { index, spot ->
            StaggeredEntrance(index = index) {
                NearbySpotRowCard(
                    spot = spot,
                    isSelected = uiState.selectedSpot?.id == spot.id,
                    onClick = { onSelectSpot(spot) },
                    onQuickNavigate = { onNavigateSpot(spot) }
                )
            }
        }
    }
}

@Composable
private fun SelectedSpotNavigationCard(
    spot: WorkoutSpot,
    uiState: NearbySpotsUiState,
    context: Context,
    onClear: () -> Unit,
    onSelectTravelMode: (TravelMode) -> Unit,
    onStartLiveNav: () -> Unit,
    onStopLiveNav: () -> Unit,
    onStartWorkoutAtSpot: () -> Unit
) {
    var showStepsExpanded by remember(spot.id) { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, EmeraldVibrant.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("selected_spot_navigation_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(colors = listOf(EmeraldVibrant, ForestGreen))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = categoryIcon(spot.category),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = spot.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${spot.categoryDetailThai} • ห่าง ${NearbySpotsRepository.formatSpotDistanceThai(spot.distanceMeters)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                IconButton(onClick = onClear, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "ปิดรายละเอียดสถานที่")
                }
            }

            if (!spot.addressSummary.isNullOrBlank() || !spot.openingHours.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (!spot.addressSummary.isNullOrBlank()) {
                            Text(
                                text = "ที่ตั้ง: ${spot.addressSummary}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!spot.openingHours.isNullOrBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "เวลาเปิด: ${spot.openingHours}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Travel Mode Chips (Walk / Bike / Drive)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TravelMode.entries.forEach { mode ->
                    val selected = uiState.travelMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectTravelMode(mode) },
                        label = { Text(mode.thaiLabel, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = {
                            Icon(
                                imageVector = when (mode) {
                                    TravelMode.WALK -> Icons.AutoMirrored.Rounded.DirectionsWalk
                                    TravelMode.BIKE -> Icons.AutoMirrored.Rounded.DirectionsBike
                                    TravelMode.DRIVE -> Icons.Rounded.DirectionsCar
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("travel_mode_${mode.name}")
                    )
                }
            }

            // Route Summary Pill
            if (uiState.isRouting) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = "กำลังคำนวณเส้นทางจริง...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (uiState.activeRoute != null) {
                val route = uiState.activeRoute
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Directions,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "ระยะทางตามเส้นทาง ${NearbySpotsRepository.formatSpotDistanceThai(route.totalDistanceMeters)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "ใช้เวลาประมาณ ${NearbySpotsRepository.formatEtaThai(route.durationSeconds)} (${route.travelMode.thaiLabel})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                        if (route.steps.isNotEmpty()) {
                            TextButton(onClick = { showStepsExpanded = !showStepsExpanded }) {
                                Text(if (showStepsExpanded) "ซ่อน" else "${route.steps.size} ขั้น")
                                Icon(
                                    imageVector = if (showStepsExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            } else if (uiState.routingError != null) {
                Text(
                    text = uiState.routingError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Expandable Turn-by-Turn Steps
            AnimatedVisibility(visible = showStepsExpanded && uiState.activeRoute?.steps?.isNotEmpty() == true) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    uiState.activeRoute?.steps?.forEach { step ->
                        TurnByTurnStepRow(
                            step = step,
                            isCurrent = uiState.isNavigatingLive && step.index - 1 == uiState.currentStepIndex
                        )
                    }
                }
            }

            // Primary Navigation + External Google Maps + Start Workout Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        if (uiState.isNavigatingLive) onStopLiveNav() else onStartLiveNav()
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isNavigatingLive) AmberWarm else ForestGreen
                    ),
                    modifier = Modifier
                        .weight(1.15f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .shimmerSweepEffect(enabled = !uiState.isNavigatingLive, shimmerAlpha = 0.18f)
                        .testTag("start_in_app_nav_button")
                ) {
                    Icon(Icons.Rounded.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (uiState.isNavigatingLive) "หยุดนำทาง" else "นำทางในแอป",
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = { launchExternalNavigation(context, spot, uiState.travelMode) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(0.85f)
                        .height(50.dp)
                        .testTag("open_external_maps_button")
                ) {
                    Icon(Icons.Rounded.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Google Maps", maxLines = 1)
                }
            }

            OutlinedButton(
                onClick = onStartWorkoutAtSpot,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, EmeraldVibrant.copy(alpha = 0.55f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("start_workout_at_spot_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = EmeraldVibrant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "เริ่มออกกำลังกาย (${spot.suggestedSport.thaiName}) ที่นี่",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TurnByTurnStepRow(
    step: NavigationStep,
    isCurrent: Boolean
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isCurrent) {
            EmeraldVibrant.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = if (isCurrent) BorderStroke(1.dp, EmeraldVibrant.copy(alpha = 0.5f)) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = maneuverIcon(step.maneuverType, step.maneuverModifier),
                contentDescription = null,
                tint = if (isCurrent) EmeraldVibrant else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = step.instructionThai,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            if (step.distanceMeters > 0) {
                Text(
                    text = NearbySpotsRepository.formatSpotDistanceThai(step.distanceMeters),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun LocationPermissionPromptCard(onRequestLocation: () -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.GpsFixed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "เปิดสิทธิ์ตำแหน่งเพื่อค้นหาแหล่งออกกำลังกายใกล้ฉัน",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "ระบบจะเรียงสวนสาธารณะ ฟิตเนส และสนามกีฬาที่ใกล้คุณที่สุด พร้อมคำนวณเส้นทางนำทางให้ทันที",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
            Button(
                onClick = onRequestLocation,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("request_nearby_location_permission_button")
            ) {
                Text("อนุญาตตำแหน่ง (GPS)", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SpotsListSectionHeader(
    uiState: NearbySpotsUiState,
    onExpandRadius: () -> Unit,
    onRetryFetch: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "เรียงจากใกล้ฉันที่สุด (${uiState.filteredSpots.size} แห่ง)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (uiState.radiusMeters < 10_000 && uiState.allSpots.size < 5 && uiState.activeCenterLat != null && !uiState.isLoadingSpots) {
                TextButton(onClick = onExpandRadius) {
                    Text("ขยายเป็น 10 กม.", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (uiState.isLoadingSpots && uiState.allSpots.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "กำลังรวบรวมสวน ลู่วิ่ง ฟิตเนส และสนามกีฬาใกล้คุณ...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (uiState.spotsError != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = uiState.spotsError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onRetryFetch) {
                        Text("ลองใหม่", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else if (uiState.filteredSpots.isEmpty() && uiState.activeCenterLat != null && !uiState.isLoadingSpots) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "ไม่พบแหล่งออกกำลังกายที่ตรงเงื่อนไขในรัศมี ${uiState.radiusMeters / 1000} กม.",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (uiState.radiusMeters < 10_000) {
                        OutlinedButton(onClick = onExpandRadius) {
                            Text("ค้นหาในรัศมี 10 กม.")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NearbySpotRowCard(
    spot: WorkoutSpot,
    isSelected: Boolean,
    onClick: () -> Unit,
    onQuickNavigate: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) {
                EmeraldVibrant
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .bounceClickable(onClick = onClick)
            .testTag("nearby_spot_card_${spot.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(categoryColor(spot.category).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon(spot.category),
                    contentDescription = spot.categoryDetailThai,
                    tint = categoryColor(spot.category),
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = spot.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = EmeraldVibrant.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = NearbySpotsRepository.formatSpotDistanceThai(spot.distanceMeters),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = spot.categoryDetailThai,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!spot.addressSummary.isNullOrBlank()) {
                    Text(
                        text = spot.addressSummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = ForestGreen,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .bounceClickable(onClick = onQuickNavigate)
                    .testTag("quick_nav_spot_${spot.id}")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Navigation,
                        contentDescription = "นำทางไปยัง ${spot.name}",
                        tint = MintBright,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

private fun categoryIcon(category: NearbySpotCategory): ImageVector = when (category) {
    NearbySpotCategory.ALL -> Icons.Rounded.LocationOn
    NearbySpotCategory.PARK_RUN -> Icons.Rounded.Park
    NearbySpotCategory.FITNESS_GYM -> Icons.Rounded.FitnessCenter
    NearbySpotCategory.SPORTS_COURT -> Icons.Rounded.SportsBasketball
    NearbySpotCategory.SWIMMING -> Icons.Rounded.Pool
}

private fun categoryColor(category: NearbySpotCategory): Color = when (category) {
    NearbySpotCategory.ALL -> EmeraldVibrant
    NearbySpotCategory.PARK_RUN -> EmeraldVibrant
    NearbySpotCategory.FITNESS_GYM -> AmberWarm
    NearbySpotCategory.SPORTS_COURT -> ForestGreen
    NearbySpotCategory.SWIMMING -> Color(0xFF0284C7)
}

private fun maneuverIcon(type: String?, modifier: String?): ImageVector = when {
    type == "arrive" -> Icons.Rounded.Flag
    modifier == "uturn" -> Icons.Rounded.UTurnLeft
    modifier?.contains("left") == true -> Icons.Rounded.TurnLeft
    modifier?.contains("right") == true -> Icons.Rounded.TurnRight
    else -> Icons.Rounded.Straight
}

private fun launchExternalNavigation(
    context: Context,
    spot: WorkoutSpot,
    travelMode: TravelMode
) {
    val googleNavUri = Uri.parse(
        "google.navigation:q=${spot.lat},${spot.lng}&mode=${travelMode.googleNavMode}"
    )
    val googleIntent = Intent(Intent.ACTION_VIEW, googleNavUri).apply {
        setPackage("com.google.android.apps.maps")
    }
    val geoFallbackUri = Uri.parse(
        "geo:${spot.lat},${spot.lng}?q=${spot.lat},${spot.lng}(${Uri.encode(spot.name)})"
    )
    val webFallbackUri = Uri.parse(
        "https://www.google.com/maps/dir/?api=1&destination=${spot.lat},${spot.lng}&travelmode=${travelMode.osrmProfile}"
    )
    runCatching {
        context.startActivity(googleIntent)
    }.recoverCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, geoFallbackUri))
    }.recoverCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, webFallbackUri))
    }
}
