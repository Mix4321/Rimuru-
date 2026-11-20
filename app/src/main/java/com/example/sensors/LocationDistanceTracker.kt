package com.example.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.data.local.DetectionSensitivity
import com.example.data.model.SportType
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.math.max
import kotlin.math.roundToInt

data class GpsTrackingSnapshot(
    val hasLocationPermission: Boolean = false,
    val isGpsEnabled: Boolean = false,
    val hasFix: Boolean = false,
    val accuracyMeters: Float? = null,
    val currentSpeedKmh: Float = 0f,
    val currentLat: Double? = null,
    val currentLng: Double? = null,
    val providerLabel: String = "GPS"
)

class LocationDistanceTracker(
    private val context: Context,
    private val sportProvider: () -> SportType,
    private val sensitivityProvider: () -> DetectionSensitivity,
    private val onDistanceAddedMeters: (Int) -> Unit,
    private val onGpsStateChanged: (GpsTrackingSnapshot) -> Unit,
    private val onRoutePointRecorded: (lat: Double, lng: Double, speedKmh: Float) -> Unit = { _, _, _ -> }
) : LocationListener {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val fusedClient: FusedLocationProviderClient? = runCatching {
        LocationServices.getFusedLocationProviderClient(context.applicationContext)
    }.getOrNull()

    private var isStarted = false
    private var usingFusedClient = false
    private var lastValidLocation: Location? = null
    private var pendingMetersBuffer: Float = 0f
    private var smoothedSpeedKmh: Float = 0f
    private var lastValidSpeedMps: Float = 0f
    private var stationaryStreak: Int = 0
    private var latestLat: Double? = null
    private var latestLng: Double? = null

    private val fusedCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (loc in result.locations) {
                onLocationChanged(loc)
            }
        }
    }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun isLocationServiceEnabled(): Boolean {
        val gps = runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
        val net = runCatching { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)
        return gps || net
    }

    @SuppressLint("MissingPermission")
    fun start() {
        stop()
        val hasPerm = hasLocationPermission()
        val locationEnabled = isLocationServiceEnabled()

        if (!hasPerm) {
            onGpsStateChanged(
                GpsTrackingSnapshot(
                    hasLocationPermission = false,
                    isGpsEnabled = locationEnabled,
                    hasFix = false
                )
            )
            return
        }

        lastValidLocation = null
        pendingMetersBuffer = 0f
        smoothedSpeedKmh = 0f
        lastValidSpeedMps = 0f
        stationaryStreak = 0

        var registeredAny = false
        usingFusedClient = false

        // 1. Prefer Google Play Services FusedLocationProviderClient for rapid 1-2s GNSS+Wi-Fi lock
        val client = fusedClient
        if (client != null) {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMinUpdateIntervalMillis(500L)
                .setMinUpdateDistanceMeters(1.2f)
                .setWaitForAccurateLocation(false)
                .build()

            runCatching {
                client.requestLocationUpdates(request, fusedCallback, Looper.getMainLooper())
                registeredAny = true
                usingFusedClient = true
            }

            // Warm-start live map with recent cached location (< 2 min old) so map centers immediately
            if (registeredAny) {
                runCatching {
                    client.lastLocation.addOnSuccessListener { cached ->
                        if (!isStarted || cached == null) return@addOnSuccessListener
                        val ageMs = System.currentTimeMillis() - cached.time
                        if (ageMs in 0..120_000L && latestLat == null) {
                            latestLat = cached.latitude
                            latestLng = cached.longitude
                            val acc = if (cached.hasAccuracy()) cached.accuracy else null
                            onGpsStateChanged(
                                GpsTrackingSnapshot(
                                    hasLocationPermission = true,
                                    isGpsEnabled = locationEnabled,
                                    hasFix = lastValidLocation != null,
                                    accuracyMeters = acc,
                                    currentSpeedKmh = smoothedSpeedKmh,
                                    currentLat = cached.latitude,
                                    currentLng = cached.longitude,
                                    providerLabel = "Fused GPS"
                                )
                            )
                        }
                    }
                }
            }
        }

        // 2. Always keep LocationManager fallback if Fused isn't active
        if (!registeredAny) {
            val gpsEnabled = runCatching {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            }.getOrDefault(false)

            if (gpsEnabled) {
                runCatching {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        1000L,
                        1.5f,
                        this,
                        Looper.getMainLooper()
                    )
                    registeredAny = true
                }
            }

            if (!registeredAny && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val hasFused = runCatching {
                    locationManager.allProviders.contains(LocationManager.FUSED_PROVIDER)
                }.getOrDefault(false)
                if (hasFused) {
                    runCatching {
                        locationManager.requestLocationUpdates(
                            LocationManager.FUSED_PROVIDER,
                            1200L,
                            1.5f,
                            this,
                            Looper.getMainLooper()
                        )
                        registeredAny = true
                    }
                }
            }

            if (!registeredAny) {
                val netEnabled = runCatching {
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                }.getOrDefault(false)
                if (netEnabled) {
                    runCatching {
                        locationManager.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER,
                            1800L,
                            2.5f,
                            this,
                            Looper.getMainLooper()
                        )
                        registeredAny = true
                    }
                }
            }
        }

        isStarted = registeredAny
        onGpsStateChanged(
            GpsTrackingSnapshot(
                hasLocationPermission = true,
                isGpsEnabled = locationEnabled,
                hasFix = false,
                currentLat = latestLat,
                currentLng = latestLng,
                providerLabel = if (usingFusedClient) "Fused GPS" else "GPS"
            )
        )
    }

    fun stop() {
        if (isStarted) {
            if (usingFusedClient) {
                runCatching { fusedClient?.removeLocationUpdates(fusedCallback) }
            }
            runCatching { locationManager.removeUpdates(this) }
            isStarted = false
            usingFusedClient = false
        }
    }

    override fun onLocationChanged(location: Location) {
        if (!isStarted) return

        val sport = sportProvider()
        val sensitivity = sensitivityProvider()
        val accuracy = if (location.hasAccuracy()) location.accuracy else 999f
        val label = if (usingFusedClient) "Fused GPS" else (location.provider?.uppercase() ?: "GPS")

        // Always update approximate map center if accuracy is at least reasonable (<= 85m)
        if (accuracy <= 85f) {
            latestLat = location.latitude
            latestLng = location.longitude
        }

        if (accuracy > sensitivity.gpsMaxAccuracyMeters) {
            onGpsStateChanged(
                GpsTrackingSnapshot(
                    hasLocationPermission = true,
                    isGpsEnabled = true,
                    hasFix = lastValidLocation != null,
                    accuracyMeters = accuracy,
                    currentSpeedKmh = smoothedSpeedKmh,
                    currentLat = latestLat ?: location.latitude,
                    currentLng = latestLng ?: location.longitude,
                    providerLabel = label
                )
            )
            return
        }

        latestLat = location.latitude
        latestLng = location.longitude

        val prev = lastValidLocation
        if (prev == null) {
            lastValidLocation = location
            val initialMps = if (location.hasSpeed() && location.speed > 0.45f) location.speed else 0f
            lastValidSpeedMps = initialMps
            smoothedSpeedKmh = (initialMps * 3.6f * 10f).roundToInt() / 10f
            onRoutePointRecorded(location.latitude, location.longitude, smoothedSpeedKmh)
            onGpsStateChanged(
                GpsTrackingSnapshot(
                    hasLocationPermission = true,
                    isGpsEnabled = true,
                    hasFix = true,
                    accuracyMeters = accuracy,
                    currentSpeedKmh = smoothedSpeedKmh,
                    currentLat = location.latitude,
                    currentLng = location.longitude,
                    providerLabel = label
                )
            )
            return
        }

        val deltaMeters = prev.distanceTo(location)
        val dtSec = ((location.time - prev.time).coerceAtLeast(250L)) / 1000.0f
        val impliedSpeedMps = deltaMeters / dtSec
        val maxPlausibleSpeedMps = when (sport) {
            SportType.WALKING -> 3.8f   // ~13.7 km/h
            SportType.CYCLING -> 19.5f  // ~70 km/h
            else -> 9.8f                // ~35 km/h running / field sports
        }
        val maxAccelSpike = lastValidSpeedMps + 4.8f * dtSec + 3.5f
        val minDriftThreshold = max(2.0f, accuracy * 0.34f)

        val isGenuineMovement = deltaMeters >= minDriftThreshold &&
            impliedSpeedMps in 0.35f..minOf(maxPlausibleSpeedMps, maxAccelSpike)

        val instantSpeedMps = when {
            !isGenuineMovement -> 0f
            location.hasSpeed() && location.speed in 0.35f..maxPlausibleSpeedMps -> location.speed
            else -> impliedSpeedMps
        }

        if (isGenuineMovement) {
            stationaryStreak = 0
            lastValidSpeedMps = instantSpeedMps
            val instKmh = instantSpeedMps * 3.6f
            smoothedSpeedKmh = if (smoothedSpeedKmh <= 0.1f) instKmh else (smoothedSpeedKmh * 0.58f + instKmh * 0.42f)
            val roundedSpeedKmh = (smoothedSpeedKmh * 10f).roundToInt() / 10f

            lastValidLocation = location
            onRoutePointRecorded(location.latitude, location.longitude, roundedSpeedKmh)
            pendingMetersBuffer += deltaMeters
            val wholeMeters = pendingMetersBuffer.toInt()
            if (wholeMeters >= 1) {
                pendingMetersBuffer -= wholeMeters
                onDistanceAddedMeters(wholeMeters)
            }
        } else {
            stationaryStreak++
            if (stationaryStreak >= 2) {
                smoothedSpeedKmh = 0f
                lastValidSpeedMps = 0f
            } else {
                smoothedSpeedKmh *= 0.5f
            }
        }

        val displaySpeedKmh = (smoothedSpeedKmh * 10f).roundToInt() / 10f
        onGpsStateChanged(
            GpsTrackingSnapshot(
                hasLocationPermission = true,
                isGpsEnabled = true,
                hasFix = true,
                accuracyMeters = accuracy,
                currentSpeedKmh = displaySpeedKmh,
                currentLat = location.latitude,
                currentLng = location.longitude,
                providerLabel = label
            )
        )
    }

    @Deprecated("Deprecated in Android API")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onProviderEnabled(provider: String) {
        onGpsStateChanged(
            GpsTrackingSnapshot(
                hasLocationPermission = hasLocationPermission(),
                isGpsEnabled = isLocationServiceEnabled(),
                hasFix = lastValidLocation != null,
                currentLat = latestLat,
                currentLng = latestLng,
                providerLabel = if (usingFusedClient) "Fused GPS" else "GPS"
            )
        )
    }

    override fun onProviderDisabled(provider: String) {
        val enabled = isLocationServiceEnabled()
        onGpsStateChanged(
            GpsTrackingSnapshot(
                hasLocationPermission = hasLocationPermission(),
                isGpsEnabled = enabled,
                hasFix = if (enabled) lastValidLocation != null else false,
                currentLat = latestLat,
                currentLng = latestLng,
                providerLabel = if (usingFusedClient) "Fused GPS" else "GPS"
            )
        )
    }
}
