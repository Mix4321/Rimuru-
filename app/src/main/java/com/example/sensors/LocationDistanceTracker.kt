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
import kotlin.math.max
import kotlin.math.roundToInt

data class GpsTrackingSnapshot(
    val hasLocationPermission: Boolean = false,
    val isGpsEnabled: Boolean = false,
    val hasFix: Boolean = false,
    val accuracyMeters: Float? = null,
    val currentSpeedKmh: Float = 0f
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
    private var isStarted = false
    private var lastValidLocation: Location? = null
    private var pendingMetersBuffer: Float = 0f
    private var smoothedSpeedKmh: Float = 0f
    private var lastValidSpeedMps: Float = 0f
    private var stationaryStreak: Int = 0

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

    @SuppressLint("MissingPermission")
    fun start() {
        stop()
        val hasPerm = hasLocationPermission()
        val gpsEnabled = runCatching {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }.getOrDefault(false)

        if (!hasPerm) {
            onGpsStateChanged(
                GpsTrackingSnapshot(
                    hasLocationPermission = false,
                    isGpsEnabled = gpsEnabled,
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
                        1400L,
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
                        2000L,
                        2.5f,
                        this,
                        Looper.getMainLooper()
                    )
                    registeredAny = true
                }
            }
        }

        isStarted = registeredAny
        onGpsStateChanged(
            GpsTrackingSnapshot(
                hasLocationPermission = true,
                isGpsEnabled = gpsEnabled,
                hasFix = false
            )
        )
    }

    fun stop() {
        if (isStarted) {
            runCatching { locationManager.removeUpdates(this) }
            isStarted = false
        }
    }

    override fun onLocationChanged(location: Location) {
        if (!isStarted) return

        val sport = sportProvider()
        val sensitivity = sensitivityProvider()
        val accuracy = if (location.hasAccuracy()) location.accuracy else 999f

        if (accuracy > sensitivity.gpsMaxAccuracyMeters) {
            onGpsStateChanged(
                GpsTrackingSnapshot(
                    hasLocationPermission = true,
                    isGpsEnabled = true,
                    hasFix = lastValidLocation != null,
                    accuracyMeters = accuracy,
                    currentSpeedKmh = smoothedSpeedKmh
                )
            )
            return
        }

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
                    currentSpeedKmh = smoothedSpeedKmh
                )
            )
            return
        }

        val deltaMeters = prev.distanceTo(location)
        val dtSec = ((location.time - prev.time).coerceAtLeast(250L)) / 1000.0f
        val impliedSpeedMps = deltaMeters / dtSec
        val maxPlausibleSpeedMps = when (sport) {
            SportType.WALKING -> 3.6f   // ~13 km/h
            SportType.CYCLING -> 18.5f  // ~66 km/h
            else -> 9.5f                // ~34 km/h running / field sports
        }
        val maxAccelSpike = lastValidSpeedMps + 4.8f * dtSec + 3.5f
        val minDriftThreshold = max(2.2f, accuracy * 0.36f)

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
                currentSpeedKmh = displaySpeedKmh
            )
        )
    }

    @Deprecated("Deprecated in Android API")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onProviderEnabled(provider: String) {
        onGpsStateChanged(
            GpsTrackingSnapshot(
                hasLocationPermission = hasLocationPermission(),
                isGpsEnabled = true,
                hasFix = lastValidLocation != null
            )
        )
    }

    override fun onProviderDisabled(provider: String) {
        onGpsStateChanged(
            GpsTrackingSnapshot(
                hasLocationPermission = hasLocationPermission(),
                isGpsEnabled = false,
                hasFix = false
            )
        )
    }
}
