package com.example.ui.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.GeoCoord
import com.example.data.remote.NavigationRouteResult
import com.example.data.remote.NavigationStep
import com.example.data.remote.NearbySpotCategory
import com.example.data.remote.NearbySpotsRepository
import com.example.data.remote.SearchPlaceSuggestion
import com.example.data.remote.TravelMode
import com.example.data.remote.WorkoutSpot
import com.example.ui.components.MoveMapStyle
import com.example.ui.components.haversineMeters
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class NearbySpotsUiState(
    val hasLocationPermission: Boolean = false,
    val isGpsEnabled: Boolean = false,
    val isLocating: Boolean = false,
    val userLat: Double? = null,
    val userLng: Double? = null,
    val searchCenterLat: Double? = null,
    val searchCenterLng: Double? = null,
    val searchAreaLabel: String? = null,
    val isUsingTemporaryFallback: Boolean = false,
    val accuracyMeters: Float? = null,
    val searchQuery: String = "",
    val isSearchModeActive: Boolean = false,
    val placeSuggestions: List<SearchPlaceSuggestion> = emptyList(),
    val isSearchingPlaces: Boolean = false,
    val searchError: String? = null,
    val selectedCategory: NearbySpotCategory = NearbySpotCategory.ALL,
    val radiusMeters: Int = NearbySpotsRepository.UNLIMITED_RADIUS_METERS,
    val isLoadingSpots: Boolean = false,
    val spotsError: String? = null,
    val allSpots: List<WorkoutSpot> = emptyList(),
    val remoteSearchSpots: List<WorkoutSpot> = emptyList(),
    val filteredSpots: List<WorkoutSpot> = emptyList(),
    val selectedSpot: WorkoutSpot? = null,
    val travelMode: TravelMode = TravelMode.WALK,
    val isRouting: Boolean = false,
    val routingError: String? = null,
    val activeRoute: NavigationRouteResult? = null,
    val isNavigatingLive: Boolean = false,
    val currentStepIndex: Int = 0,
    val remainingDistanceMeters: Int? = null,
    val hasArrived: Boolean = false,
    val followUserOnMap: Boolean = true,
    val mapStyle: MoveMapStyle = MoveMapStyle.STREET,
    val fitRouteTick: Int = 0,
    val recenterTick: Int = 0,
    val isGoogleMapsKeyConfigured: Boolean = false
) {
    val activeCenterLat: Double? get() = searchCenterLat ?: userLat
    val activeCenterLng: Double? get() = searchCenterLng ?: userLng

    val currentNavStep: NavigationStep?
        get() = activeRoute?.steps?.getOrNull(currentStepIndex)

    val nextNavStep: NavigationStep?
        get() = activeRoute?.steps?.getOrNull(currentStepIndex + 1)
}

class NearbySpotsViewModel(
    application: Application,
    private val repository: NearbySpotsRepository = NearbySpotsRepository()
) : AndroidViewModel(application), LocationListener {

    private val appContext: Context = application.applicationContext
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val fusedClient: FusedLocationProviderClient? = runCatching {
        LocationServices.getFusedLocationProviderClient(appContext)
    }.getOrNull()

    private val _uiState = MutableStateFlow(NearbySpotsUiState())
    val uiState: StateFlow<NearbySpotsUiState> = _uiState.asStateFlow()

    private var isLocationListening = false
    private var usingFused = false
    private var hasFetchedInitialSpots = false
    private var lastRouteOrigin: GeoCoord? = null
    private var spotsFetchJob: Job? = null
    private var routeFetchJob: Job? = null
    private var searchJob: Job? = null
    private var gpsFallbackWatchdogJob: Job? = null
    private val routeCache = LinkedHashMap<String, NavigationRouteResult>(48, 0.75f, true)

    private val fusedCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.lastOrNull()?.let { onLocationChanged(it) }
        }
    }

    init {
        _uiState.update { it.copy(isGoogleMapsKeyConfigured = repository.isGoogleMapsKeyConfigured()) }
        refreshPermissionAndLocationState()
    }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun isLocationProviderEnabled(): Boolean {
        val gps = runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
        val net = runCatching { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)
        return gps || net
    }

    @SuppressLint("MissingPermission")
    fun refreshPermissionAndLocationState() {
        val hasPerm = hasLocationPermission()
        val gpsEnabled = isLocationProviderEnabled()
        _uiState.update {
            it.copy(
                hasLocationPermission = hasPerm,
                isGpsEnabled = gpsEnabled
            )
        }
        if (hasPerm) {
            startLocationUpdates()
        } else {
            stopLocationUpdates()
            // Even without GPS permission, auto-populate default Bangkok region once
            // so the places list & map are immediately useful instead of empty.
            if (!hasFetchedInitialSpots && _uiState.value.allSpots.isEmpty() && _uiState.value.activeCenterLat == null) {
                useFallbackLocationIfNeeded()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (!hasLocationPermission()) return
        if (isLocationListening) return

        _uiState.update { it.copy(isLocating = it.userLat == null) }

        // Warm-start immediately from any cached system location provider (GPS / Network / Passive / Fused)
        findBestCachedSystemLocation()?.let { cached ->
            if (_uiState.value.userLat == null) {
                handleNewUserLocation(cached)
            }
        }

        var started = false
        val client = fusedClient
        if (client != null) {
            runCatching {
                client.lastLocation.addOnSuccessListener { cached ->
                    if (cached != null && _uiState.value.userLat == null) {
                        handleNewUserLocation(cached)
                    }
                }
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { fresh ->
                        if (fresh != null) {
                            handleNewUserLocation(fresh)
                        }
                    }
            }
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2500L)
                .setMinUpdateIntervalMillis(1200L)
                .setMinUpdateDistanceMeters(0f)
                .setWaitForAccurateLocation(false)
                .build()
            runCatching {
                client.requestLocationUpdates(request, fusedCallback, Looper.getMainLooper())
                started = true
                usingFused = true
            }
        }

        // Also register native LocationManager listener as a backup (especially helpful on emulators)
        val nativeProviders = buildList {
            if (runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)) {
                add(LocationManager.GPS_PROVIDER)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                runCatching { locationManager.allProviders.contains(LocationManager.FUSED_PROVIDER) }.getOrDefault(false)
            ) {
                add(LocationManager.FUSED_PROVIDER)
            }
            if (runCatching { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
        }
        for (provider in nativeProviders) {
            runCatching {
                locationManager.requestLocationUpdates(provider, 3000L, 0f, this, Looper.getMainLooper())
                started = true
            }
        }

        isLocationListening = started

        // Watchdog: if GPS takes > 1.6s on emulator / indoors, automatically load spots around default center
        gpsFallbackWatchdogJob?.cancel()
        if (_uiState.value.userLat == null) {
            gpsFallbackWatchdogJob = viewModelScope.launch {
                delay(1600)
                if (_uiState.value.userLat == null && _uiState.value.searchCenterLat == null) {
                    useFallbackLocationIfNeeded()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun findBestCachedSystemLocation(): Location? {
        if (!hasLocationPermission()) return null
        val providers = buildList {
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
        }
        return providers.mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
    }

    fun useFallbackLocationIfNeeded() {
        if (_uiState.value.userLat != null || _uiState.value.searchCenterLat != null) {
            val lat = _uiState.value.activeCenterLat ?: NearbySpotsRepository.DEFAULT_THAILAND_LAT
            val lng = _uiState.value.activeCenterLng ?: NearbySpotsRepository.DEFAULT_THAILAND_LNG
            fetchNearbySpots(centerLat = lat, centerLng = lng)
            return
        }
        hasFetchedInitialSpots = true
        _uiState.update {
            it.copy(
                isLocating = false,
                searchCenterLat = NearbySpotsRepository.DEFAULT_THAILAND_LAT,
                searchCenterLng = NearbySpotsRepository.DEFAULT_THAILAND_LNG,
                searchAreaLabel = "กรุงเทพฯ (รอสัญญาณ GPS)",
                isUsingTemporaryFallback = true,
                followUserOnMap = false,
                recenterTick = it.recenterTick + 1
            )
        }
        fetchNearbySpots(
            centerLat = NearbySpotsRepository.DEFAULT_THAILAND_LAT,
            centerLng = NearbySpotsRepository.DEFAULT_THAILAND_LNG
        )
    }

    fun refreshOrGatherNearbySpots() {
        val state = _uiState.value
        val cLat = state.activeCenterLat
        val cLng = state.activeCenterLng
        if (cLat != null && cLng != null) {
            fetchNearbySpots(centerLat = cLat, centerLng = cLng, forceRefresh = true)
        } else {
            if (hasLocationPermission()) {
                isLocationListening = false
                startLocationUpdates()
            }
            useFallbackLocationIfNeeded()
        }
    }

    fun stopLocationUpdates() {
        gpsFallbackWatchdogJob?.cancel()
        if (!isLocationListening) return
        if (usingFused) {
            runCatching { fusedClient?.removeLocationUpdates(fusedCallback) }
        }
        runCatching { locationManager.removeUpdates(this) }
        isLocationListening = false
        usingFused = false
    }

    override fun onLocationChanged(location: Location) {
        handleNewUserLocation(location)
    }

    private fun handleNewUserLocation(location: Location) {
        gpsFallbackWatchdogJob?.cancel()
        val lat = location.latitude
        val lng = location.longitude
        val acc = if (location.hasAccuracy()) location.accuracy else null
        val wasUsingFallback = _uiState.value.isUsingTemporaryFallback

        _uiState.update { state ->
            val clearFallback = state.isUsingTemporaryFallback
            val updatedAll = if (state.searchCenterLat == null || clearFallback) {
                state.allSpots.map { spot ->
                    spot.copy(
                        distanceMeters = haversineMeters(lat, lng, spot.lat, spot.lng)
                            .roundToInt()
                            .coerceAtLeast(1)
                    )
                }.sortedBy { it.distanceMeters }
            } else {
                state.allSpots
            }
            val updatedRemote = state.remoteSearchSpots.map { spot ->
                spot.copy(
                    distanceMeters = haversineMeters(lat, lng, spot.lat, spot.lng)
                        .roundToInt()
                        .coerceAtLeast(1)
                )
            }.sortedBy { it.distanceMeters }

            val visible = computeVisibleSpots(
                allSpots = updatedAll,
                remoteSearchSpots = updatedRemote,
                isSearchModeActive = state.isSearchModeActive,
                category = state.selectedCategory,
                query = state.searchQuery
            )
            val updatedSelected = state.selectedSpot?.let { sel ->
                visible.firstOrNull { it.id == sel.id } ?: sel
            }
            state.copy(
                isLocating = false,
                isGpsEnabled = true,
                userLat = lat,
                userLng = lng,
                accuracyMeters = acc,
                searchCenterLat = if (clearFallback) null else state.searchCenterLat,
                searchCenterLng = if (clearFallback) null else state.searchCenterLng,
                searchAreaLabel = if (clearFallback) null else state.searchAreaLabel,
                isUsingTemporaryFallback = false,
                followUserOnMap = if (clearFallback) true else state.followUserOnMap,
                recenterTick = if (clearFallback) state.recenterTick + 1 else state.recenterTick,
                allSpots = updatedAll,
                remoteSearchSpots = updatedRemote,
                filteredSpots = visible,
                selectedSpot = updatedSelected
            )
        }

        val distanceFromFallback = haversineMeters(
            lat,
            lng,
            NearbySpotsRepository.DEFAULT_THAILAND_LAT,
            NearbySpotsRepository.DEFAULT_THAILAND_LNG
        )
        val shouldSkipFallbackRefetch = wasUsingFallback &&
            distanceFromFallback <= 650.0 &&
            (_uiState.value.allSpots.isNotEmpty() || spotsFetchJob?.isActive == true)

        if (!hasFetchedInitialSpots || (wasUsingFallback && !shouldSkipFallbackRefetch)) {
            hasFetchedInitialSpots = true
            fetchNearbySpots(centerLat = lat, centerLng = lng)
        }

        val state = _uiState.value
        if (state.isNavigatingLive && state.selectedSpot != null) {
            updateLiveNavigationProgress(lat, lng, state.selectedSpot, state.activeRoute)
        }
    }

    private fun updateLiveNavigationProgress(
        userLat: Double,
        userLng: Double,
        dest: WorkoutSpot,
        route: NavigationRouteResult?
    ) {
        val remaining = haversineMeters(userLat, userLng, dest.lat, dest.lng).roundToInt().coerceAtLeast(0)
        val arrived = remaining <= 25

        var nextStepIdx = _uiState.value.currentStepIndex
        if (route != null && route.steps.isNotEmpty()) {
            for (idx in nextStepIdx until route.steps.lastIndex) {
                val nextManeuver = route.steps[idx + 1]
                val distToManeuver = haversineMeters(
                    userLat,
                    userLng,
                    nextManeuver.location.lat,
                    nextManeuver.location.lng
                )
                if (distToManeuver <= 28.0) {
                    nextStepIdx = idx + 1
                }
            }
            if (arrived) {
                nextStepIdx = route.steps.lastIndex
            }
        }

        _uiState.update {
            it.copy(
                remainingDistanceMeters = remaining,
                currentStepIndex = nextStepIdx,
                hasArrived = arrived
            )
        }

        val lastOrig = lastRouteOrigin
        if (!arrived && lastOrig != null && haversineMeters(userLat, userLng, lastOrig.lat, lastOrig.lng) > 65.0) {
            requestRouteToSpot(dest, _uiState.value.travelMode, autoFit = false)
        }
    }

    fun fetchNearbySpots(
        centerLat: Double? = _uiState.value.activeCenterLat,
        centerLng: Double? = _uiState.value.activeCenterLng,
        radiusMeters: Int = _uiState.value.radiusMeters,
        forceRefresh: Boolean = false
    ) {
        val lat = centerLat ?: return
        val lng = centerLng ?: return

        spotsFetchJob?.cancel()
        spotsFetchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSpots = true, spotsError = null) }
            val result = repository.fetchNearbySpots(
                userLat = lat,
                userLng = lng,
                radiusMeters = radiusMeters,
                forceRefresh = forceRefresh,
                onPartialSpots = { partial ->
                    applyFetchedSpotsUpdate(partial, lat, lng, isStillLoading = true)
                }
            )
            result.fold(
                onSuccess = { spots ->
                    applyFetchedSpotsUpdate(spots, lat, lng, isStillLoading = false)
                },
                onFailure = { err ->
                    _uiState.update { state ->
                        if (state.allSpots.isNotEmpty()) {
                            state.copy(isLoadingSpots = false, spotsError = null)
                        } else {
                            state.copy(
                                isLoadingSpots = false,
                                spotsError = "ไม่สามารถดึงสถานที่ใกล้คุณได้ในขณะนี้ (${err.localizedMessage ?: "โปรดตรวจสอบอินเทอร์เน็ต"})"
                            )
                        }
                    }
                }
            )
        }
    }

    private fun applyFetchedSpotsUpdate(
        spots: List<WorkoutSpot>,
        fallbackLat: Double,
        fallbackLng: Double,
        isStillLoading: Boolean
    ) {
        val uLat = _uiState.value.userLat ?: fallbackLat
        val uLng = _uiState.value.userLng ?: fallbackLng
        val distanceSorted = spots.map { s ->
            s.copy(distanceMeters = haversineMeters(uLat, uLng, s.lat, s.lng).roundToInt().coerceAtLeast(1))
        }.sortedBy { it.distanceMeters }

        _uiState.update { state ->
            val visible = computeVisibleSpots(
                allSpots = distanceSorted,
                remoteSearchSpots = state.remoteSearchSpots,
                isSearchModeActive = state.isSearchModeActive,
                category = state.selectedCategory,
                query = state.searchQuery
            )
            val preservedSelection = state.selectedSpot?.let { prev ->
                visible.firstOrNull { it.id == prev.id } ?: prev
            }
            state.copy(
                isLoadingSpots = isStillLoading,
                spotsError = null,
                allSpots = distanceSorted,
                filteredSpots = visible,
                selectedSpot = preservedSelection
            )
        }
    }

    fun onSearchQueryChanged(newQuery: String) {
        val trimmed = newQuery.trim()
        searchJob?.cancel()

        if (trimmed.isEmpty()) {
            _uiState.update { state ->
                state.copy(
                    searchQuery = "",
                    isSearchModeActive = false,
                    isSearchingPlaces = false,
                    searchError = null,
                    placeSuggestions = emptyList(),
                    remoteSearchSpots = emptyList(),
                    filteredSpots = computeVisibleSpots(
                        allSpots = state.allSpots,
                        remoteSearchSpots = emptyList(),
                        isSearchModeActive = false,
                        category = state.selectedCategory,
                        query = ""
                    )
                )
            }
            return
        }

        _uiState.update { state ->
            state.copy(
                searchQuery = newQuery,
                isSearchModeActive = trimmed.length >= 2,
                searchError = null,
                filteredSpots = computeVisibleSpots(
                    allSpots = state.allSpots,
                    remoteSearchSpots = state.remoteSearchSpots,
                    isSearchModeActive = trimmed.length >= 2,
                    category = state.selectedCategory,
                    query = newQuery
                )
            )
        }

        if (trimmed.length >= 2) {
            searchJob = viewModelScope.launch {
                delay(420)
                executeRemoteSearch(trimmed, recenterToTopIfNeeded = false)
            }
        }
    }

    fun submitSearch() {
        val trimmed = _uiState.value.searchQuery.trim()
        if (trimmed.length < 2) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            executeRemoteSearch(trimmed, recenterToTopIfNeeded = true)
        }
    }

    private suspend fun executeRemoteSearch(query: String, recenterToTopIfNeeded: Boolean) {
        _uiState.update { it.copy(isSearchingPlaces = true, searchError = null, isSearchModeActive = true) }
        val stateBefore = _uiState.value
        val result = repository.searchWorkoutSpotsAndPlaces(
            query = query,
            originLat = stateBefore.userLat ?: stateBefore.searchCenterLat,
            originLng = stateBefore.userLng ?: stateBefore.searchCenterLng
        )
        result.fold(
            onSuccess = { combined ->
                val uLat = _uiState.value.userLat ?: _uiState.value.searchCenterLat
                val uLng = _uiState.value.userLng ?: _uiState.value.searchCenterLng
                val distFixedSpots = if (uLat != null && uLng != null) {
                    combined.spots.map { s ->
                        s.copy(distanceMeters = haversineMeters(uLat, uLng, s.lat, s.lng).roundToInt().coerceAtLeast(1))
                    }.sortedBy { it.distanceMeters }
                } else {
                    combined.spots
                }

                _uiState.update { state ->
                    val mergedVisible = computeVisibleSpots(
                        allSpots = state.allSpots,
                        remoteSearchSpots = distFixedSpots,
                        isSearchModeActive = true,
                        category = state.selectedCategory,
                        query = state.searchQuery
                    )
                    val topSpot = mergedVisible.firstOrNull()
                    val shouldRecenter = recenterToTopIfNeeded && topSpot != null
                    state.copy(
                        isSearchingPlaces = false,
                        searchError = null,
                        placeSuggestions = combined.areaSuggestions,
                        remoteSearchSpots = distFixedSpots,
                        filteredSpots = mergedVisible,
                        searchCenterLat = if (shouldRecenter && state.userLat == null) topSpot!!.lat else state.searchCenterLat,
                        searchCenterLng = if (shouldRecenter && state.userLat == null) topSpot!!.lng else state.searchCenterLng,
                        recenterTick = if (shouldRecenter) state.recenterTick + 1 else state.recenterTick,
                        followUserOnMap = if (shouldRecenter) false else state.followUserOnMap
                    )
                }
            },
            onFailure = { err ->
                _uiState.update {
                    it.copy(
                        isSearchingPlaces = false,
                        searchError = "ค้นหาไม่สำเร็จ (${err.localizedMessage ?: "โปรดตรวจสอบอินเทอร์เน็ต"})"
                    )
                }
            }
        )
    }

    fun selectPlaceSuggestion(suggestion: SearchPlaceSuggestion) {
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                searchQuery = "",
                isSearchModeActive = false,
                placeSuggestions = emptyList(),
                remoteSearchSpots = emptyList(),
                isSearchingPlaces = false,
                searchError = null,
                searchCenterLat = suggestion.lat,
                searchCenterLng = suggestion.lng,
                searchAreaLabel = suggestion.shortName,
                isUsingTemporaryFallback = false,
                followUserOnMap = false,
                recenterTick = it.recenterTick + 1
            )
        }
        fetchNearbySpots(centerLat = suggestion.lat, centerLng = suggestion.lng)
    }

    fun clearSearchAndShowAllNearby() {
        searchJob?.cancel()
        _uiState.update { state ->
            state.copy(
                searchQuery = "",
                isSearchModeActive = false,
                isSearchingPlaces = false,
                searchError = null,
                placeSuggestions = emptyList(),
                remoteSearchSpots = emptyList(),
                filteredSpots = computeVisibleSpots(
                    allSpots = state.allSpots,
                    remoteSearchSpots = emptyList(),
                    isSearchModeActive = false,
                    category = state.selectedCategory,
                    query = ""
                )
            )
        }
    }

    fun resetToGpsLocation() {
        val uLat = _uiState.value.userLat
        val uLng = _uiState.value.userLng
        _uiState.update {
            it.copy(
                searchCenterLat = null,
                searchCenterLng = null,
                searchAreaLabel = null,
                isUsingTemporaryFallback = false,
                searchQuery = "",
                isSearchModeActive = false,
                placeSuggestions = emptyList(),
                remoteSearchSpots = emptyList(),
                searchError = null,
                followUserOnMap = true,
                recenterTick = it.recenterTick + 1
            )
        }
        if (uLat != null && uLng != null) {
            fetchNearbySpots(centerLat = uLat, centerLng = uLng)
        } else {
            isLocationListening = false
            startLocationUpdates()
        }
    }

    fun selectCategory(category: NearbySpotCategory) {
        _uiState.update { state ->
            state.copy(
                selectedCategory = category,
                filteredSpots = computeVisibleSpots(
                    allSpots = state.allSpots,
                    remoteSearchSpots = state.remoteSearchSpots,
                    isSearchModeActive = state.isSearchModeActive,
                    category = category,
                    query = state.searchQuery
                )
            )
        }
    }

    fun selectRadius(radiusMeters: Int) {
        if (_uiState.value.radiusMeters == radiusMeters) return
        _uiState.update { it.copy(radiusMeters = radiusMeters) }
        fetchNearbySpots(radiusMeters = radiusMeters)
    }

    fun selectSpot(spot: WorkoutSpot, startNavigationImmediately: Boolean = false) {
        val currentState = _uiState.value
        val isSameSpot = currentState.selectedSpot?.id == spot.id
        val preservedRoute = if (isSameSpot) currentState.activeRoute else null

        if (isSameSpot && preservedRoute != null && startNavigationImmediately && !currentState.isRouting) {
            _uiState.update {
                it.copy(
                    isNavigatingLive = true,
                    followUserOnMap = true,
                    hasArrived = false,
                    currentStepIndex = 0,
                    fitRouteTick = it.fitRouteTick + 1
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                selectedSpot = spot,
                activeRoute = preservedRoute,
                placeSuggestions = emptyList(),
                isNavigatingLive = startNavigationImmediately,
                hasArrived = false,
                currentStepIndex = 0,
                remainingDistanceMeters = preservedRoute?.totalDistanceMeters ?: spot.distanceMeters,
                routingError = null,
                searchCenterLat = if (it.userLat == null) spot.lat else it.searchCenterLat,
                searchCenterLng = if (it.userLng == null) spot.lng else it.searchCenterLng,
                recenterTick = if (it.userLat == null) it.recenterTick + 1 else it.recenterTick
            )
        }
        requestRouteToSpot(spot, _uiState.value.travelMode, autoFit = true)
    }

    fun clearSelectedSpot() {
        routeFetchJob?.cancel()
        _uiState.update {
            it.copy(
                selectedSpot = null,
                activeRoute = null,
                isRouting = false,
                routingError = null,
                isNavigatingLive = false,
                hasArrived = false,
                currentStepIndex = 0,
                remainingDistanceMeters = null
            )
        }
    }

    fun selectTravelMode(mode: TravelMode) {
        if (_uiState.value.travelMode == mode) return
        _uiState.update { it.copy(travelMode = mode) }
        _uiState.value.selectedSpot?.let { spot ->
            requestRouteToSpot(spot, mode, autoFit = !_uiState.value.isNavigatingLive)
        }
    }

    fun startLiveNavigation() {
        val spot = _uiState.value.selectedSpot ?: return
        _uiState.update {
            it.copy(
                isNavigatingLive = true,
                followUserOnMap = true,
                hasArrived = false,
                currentStepIndex = 0,
                fitRouteTick = if (it.activeRoute != null) it.fitRouteTick + 1 else it.fitRouteTick,
                recenterTick = if (it.activeRoute == null) it.recenterTick + 1 else it.recenterTick
            )
        }
        if (_uiState.value.activeRoute == null && !_uiState.value.isRouting) {
            requestRouteToSpot(spot, _uiState.value.travelMode, autoFit = true)
        }
    }

    fun stopLiveNavigation() {
        _uiState.update {
            it.copy(
                isNavigatingLive = false,
                hasArrived = false
            )
        }
    }

    fun toggleMapStyle() {
        _uiState.update { it.copy(mapStyle = it.mapStyle.next()) }
    }

    fun recenterOnUser() {
        _uiState.update {
            it.copy(
                followUserOnMap = true,
                recenterTick = it.recenterTick + 1
            )
        }
    }

    fun onUserPanMap() {
        _uiState.update { it.copy(followUserOnMap = false) }
    }

    private fun requestRouteToSpot(spot: WorkoutSpot, mode: TravelMode, autoFit: Boolean) {
        val state = _uiState.value
        val origLat = state.userLat ?: state.searchCenterLat ?: return
        val origLng = state.userLng ?: state.searchCenterLng ?: return

        val cacheKey = "${spot.id}_${mode.name}_${(origLat * 2000).roundToInt()}_${(origLng * 2000).roundToInt()}"
        val cachedRoute = routeCache[cacheKey]
        if (cachedRoute != null) {
            routeFetchJob?.cancel()
            lastRouteOrigin = GeoCoord(origLat, origLng)
            _uiState.update { curr ->
                curr.copy(
                    isRouting = false,
                    routingError = null,
                    activeRoute = cachedRoute,
                    currentStepIndex = 0,
                    remainingDistanceMeters = cachedRoute.totalDistanceMeters,
                    fitRouteTick = if (autoFit) curr.fitRouteTick + 1 else curr.fitRouteTick
                )
            }
            return
        }

        routeFetchJob?.cancel()
        routeFetchJob = viewModelScope.launch {
            _uiState.update { it.copy(isRouting = true, routingError = null) }
            lastRouteOrigin = GeoCoord(origLat, origLng)
            val result = repository.fetchNavigationRoute(
                originLat = origLat,
                originLng = origLng,
                destLat = spot.lat,
                destLng = spot.lng,
                travelMode = mode
            )
            result.fold(
                onSuccess = { route ->
                    if (routeCache.size >= 40) {
                        routeCache.keys.firstOrNull()?.let { routeCache.remove(it) }
                    }
                    routeCache[cacheKey] = route
                    _uiState.update { curr ->
                        if (curr.selectedSpot?.id != spot.id) {
                            curr
                        } else {
                            curr.copy(
                                isRouting = false,
                                routingError = null,
                                activeRoute = route,
                                currentStepIndex = 0,
                                remainingDistanceMeters = route.totalDistanceMeters,
                                fitRouteTick = if (autoFit) curr.fitRouteTick + 1 else curr.fitRouteTick
                            )
                        }
                    }
                },
                onFailure = { err ->
                    _uiState.update { curr ->
                        curr.copy(
                            isRouting = false,
                            routingError = "คำนวณเส้นทางไม่สำเร็จ (${err.localizedMessage ?: "โปรดลองอีกครั้ง"})"
                        )
                    }
                }
            )
        }
    }

    private fun computeVisibleSpots(
        allSpots: List<WorkoutSpot>,
        remoteSearchSpots: List<WorkoutSpot>,
        isSearchModeActive: Boolean,
        category: NearbySpotCategory,
        query: String
    ): List<WorkoutSpot> {
        val cleanQ = query.trim().lowercase()
        val localMatches = allSpots.filter { spot ->
            val catMatch = category == NearbySpotCategory.ALL || spot.category == category
            val qMatch = cleanQ.isBlank() ||
                spot.name.lowercase().contains(cleanQ) ||
                spot.categoryDetailThai.lowercase().contains(cleanQ) ||
                (spot.addressSummary?.lowercase()?.contains(cleanQ) == true) ||
                (spot.sportTags?.lowercase()?.contains(cleanQ) == true) ||
                spot.sportBadgesThai.any { it.lowercase().contains(cleanQ) }
            catMatch && qMatch
        }

        if (!isSearchModeActive) return localMatches

        val merged = LinkedHashMap<String, WorkoutSpot>()
        for (spot in localMatches) {
            val key = "${spot.name.lowercase()}_${(spot.lat * 1000).roundToInt()}_${(spot.lng * 1000).roundToInt()}"
            merged.putIfAbsent(key, spot)
        }
        for (spot in remoteSearchSpots) {
            val catMatch = category == NearbySpotCategory.ALL || spot.category == category
            if (catMatch) {
                val key = "${spot.name.lowercase()}_${(spot.lat * 1000).roundToInt()}_${(spot.lng * 1000).roundToInt()}"
                merged.putIfAbsent(key, spot)
            }
        }
        return merged.values.sortedBy { it.distanceMeters }
    }

    override fun onCleared() {
        super.onCleared()
        stopLocationUpdates()
    }
}
