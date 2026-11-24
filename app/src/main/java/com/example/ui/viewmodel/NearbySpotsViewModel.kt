package com.example.ui.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
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
    val accuracyMeters: Float? = null,
    val searchQuery: String = "",
    val placeSuggestions: List<SearchPlaceSuggestion> = emptyList(),
    val isSearchingPlaces: Boolean = false,
    val selectedCategory: NearbySpotCategory = NearbySpotCategory.ALL,
    val radiusMeters: Int = 5000,
    val isLoadingSpots: Boolean = false,
    val spotsError: String? = null,
    val allSpots: List<WorkoutSpot> = emptyList(),
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
    val recenterTick: Int = 0
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

    private val fusedCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.lastOrNull()?.let { onLocationChanged(it) }
        }
    }

    init {
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
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (!hasLocationPermission()) return
        if (isLocationListening) return

        _uiState.update { it.copy(isLocating = it.userLat == null) }

        var started = false
        val client = fusedClient
        if (client != null) {
            runCatching {
                client.lastLocation.addOnSuccessListener { cached ->
                    if (cached != null && _uiState.value.userLat == null) {
                        handleNewUserLocation(cached)
                    }
                }
            }
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2500L)
                .setMinUpdateIntervalMillis(1200L)
                .setMinUpdateDistanceMeters(3f)
                .build()
            runCatching {
                client.requestLocationUpdates(request, fusedCallback, Looper.getMainLooper())
                started = true
                usingFused = true
            }
        }

        if (!started) {
            val provider = when {
                runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) ->
                    LocationManager.GPS_PROVIDER
                runCatching { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) ->
                    LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider != null) {
                runCatching {
                    locationManager.getLastKnownLocation(provider)?.let { cached ->
                        if (_uiState.value.userLat == null) handleNewUserLocation(cached)
                    }
                    locationManager.requestLocationUpdates(provider, 2500L, 3f, this, Looper.getMainLooper())
                    started = true
                }
            }
        }

        isLocationListening = started
    }

    fun stopLocationUpdates() {
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
        val lat = location.latitude
        val lng = location.longitude
        val acc = if (location.hasAccuracy()) location.accuracy else null

        _uiState.update { state ->
            val updatedSpots = if (state.searchCenterLat == null) {
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
            val updatedSelected = state.selectedSpot?.let { sel ->
                updatedSpots.firstOrNull { it.id == sel.id } ?: sel
            }
            state.copy(
                isLocating = false,
                isGpsEnabled = true,
                userLat = lat,
                userLng = lng,
                accuracyMeters = acc,
                allSpots = updatedSpots,
                filteredSpots = filterSpots(updatedSpots, state.selectedCategory, state.searchQuery),
                selectedSpot = updatedSelected
            )
        }

        if (!hasFetchedInitialSpots) {
            hasFetchedInitialSpots = true
            fetchNearbySpots(centerLat = lat, centerLng = lng)
        }

        // Update live turn-by-turn navigation progress when active
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
            // Advance step index when user passes within 28m of the upcoming maneuver point
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

        // Re-route cleanly if user moved > 65m away from last routed origin while navigating
        val lastOrig = lastRouteOrigin
        if (!arrived && lastOrig != null && haversineMeters(userLat, userLng, lastOrig.lat, lastOrig.lng) > 65.0) {
            requestRouteToSpot(dest, _uiState.value.travelMode, autoFit = false)
        }
    }

    fun fetchNearbySpots(
        centerLat: Double? = _uiState.value.activeCenterLat,
        centerLng: Double? = _uiState.value.activeCenterLng,
        radiusMeters: Int = _uiState.value.radiusMeters
    ) {
        val lat = centerLat ?: return
        val lng = centerLng ?: return

        spotsFetchJob?.cancel()
        spotsFetchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSpots = true, spotsError = null) }
            val result = repository.fetchNearbySpots(lat, lng, radiusMeters)
            result.fold(
                onSuccess = { spots ->
                    val uLat = _uiState.value.userLat ?: lat
                    val uLng = _uiState.value.userLng ?: lng
                    val distanceSorted = spots.map { s ->
                        s.copy(distanceMeters = haversineMeters(uLat, uLng, s.lat, s.lng).roundToInt().coerceAtLeast(1))
                    }.sortedBy { it.distanceMeters }

                    _uiState.update { state ->
                        val filtered = filterSpots(distanceSorted, state.selectedCategory, state.searchQuery)
                        val preservedSelection = state.selectedSpot?.let { prev ->
                            distanceSorted.firstOrNull { it.id == prev.id }
                        }
                        state.copy(
                            isLoadingSpots = false,
                            spotsError = null,
                            allSpots = distanceSorted,
                            filteredSpots = filtered,
                            selectedSpot = preservedSelection
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingSpots = false,
                            spotsError = "ไม่สามารถดึงสถานที่ใกล้คุณได้ในขณะนี้ (${err.localizedMessage ?: "โปรดตรวจสอบอินเทอร์เน็ต"})"
                        )
                    }
                }
            )
        }
    }

    fun onSearchQueryChanged(newQuery: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = newQuery,
                filteredSpots = filterSpots(state.allSpots, state.selectedCategory, newQuery),
                placeSuggestions = if (newQuery.trim().length < 2) emptyList() else state.placeSuggestions
            )
        }

        searchJob?.cancel()
        if (newQuery.trim().length >= 2) {
            searchJob = viewModelScope.launch {
                delay(420)
                _uiState.update { it.copy(isSearchingPlaces = true) }
                val res = repository.searchPlacesByKeyword(
                    query = newQuery,
                    nearLat = _uiState.value.activeCenterLat,
                    nearLng = _uiState.value.activeCenterLng
                )
                _uiState.update {
                    it.copy(
                        isSearchingPlaces = false,
                        placeSuggestions = res.getOrDefault(emptyList())
                    )
                }
            }
        }
    }

    fun selectPlaceSuggestion(suggestion: SearchPlaceSuggestion) {
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                searchQuery = "",
                placeSuggestions = emptyList(),
                isSearchingPlaces = false,
                searchCenterLat = suggestion.lat,
                searchCenterLng = suggestion.lng,
                searchAreaLabel = suggestion.shortName,
                followUserOnMap = false,
                recenterTick = it.recenterTick + 1
            )
        }
        fetchNearbySpots(centerLat = suggestion.lat, centerLng = suggestion.lng)
    }

    fun resetToGpsLocation() {
        val uLat = _uiState.value.userLat
        val uLng = _uiState.value.userLng
        _uiState.update {
            it.copy(
                searchCenterLat = null,
                searchCenterLng = null,
                searchAreaLabel = null,
                searchQuery = "",
                placeSuggestions = emptyList(),
                followUserOnMap = true,
                recenterTick = it.recenterTick + 1
            )
        }
        if (uLat != null && uLng != null) {
            fetchNearbySpots(centerLat = uLat, centerLng = uLng)
        } else {
            startLocationUpdates()
        }
    }

    fun selectCategory(category: NearbySpotCategory) {
        _uiState.update { state ->
            state.copy(
                selectedCategory = category,
                filteredSpots = filterSpots(state.allSpots, category, state.searchQuery)
            )
        }
    }

    fun selectRadius(radiusMeters: Int) {
        if (_uiState.value.radiusMeters == radiusMeters) return
        _uiState.update { it.copy(radiusMeters = radiusMeters) }
        fetchNearbySpots(radiusMeters = radiusMeters)
    }

    fun selectSpot(spot: WorkoutSpot, startNavigationImmediately: Boolean = false) {
        _uiState.update {
            it.copy(
                selectedSpot = spot,
                isNavigatingLive = startNavigationImmediately,
                hasArrived = false,
                currentStepIndex = 0,
                remainingDistanceMeters = spot.distanceMeters,
                routingError = null
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
                recenterTick = it.recenterTick + 1
            )
        }
        if (_uiState.value.activeRoute == null) {
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
                    _uiState.update { curr ->
                        curr.copy(
                            isRouting = false,
                            routingError = null,
                            activeRoute = route,
                            currentStepIndex = 0,
                            remainingDistanceMeters = route.totalDistanceMeters,
                            fitRouteTick = if (autoFit) curr.fitRouteTick + 1 else curr.fitRouteTick
                        )
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

    private fun filterSpots(
        spots: List<WorkoutSpot>,
        category: NearbySpotCategory,
        query: String
    ): List<WorkoutSpot> {
        val cleanQ = query.trim().lowercase()
        return spots.filter { spot ->
            val catMatch = category == NearbySpotCategory.ALL || spot.category == category
            val qMatch = cleanQ.isBlank() ||
                spot.name.lowercase().contains(cleanQ) ||
                spot.categoryDetailThai.lowercase().contains(cleanQ) ||
                (spot.addressSummary?.lowercase()?.contains(cleanQ) == true) ||
                (spot.sportTags?.lowercase()?.contains(cleanQ) == true)
            catMatch && qMatch
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopLocationUpdates()
    }
}
