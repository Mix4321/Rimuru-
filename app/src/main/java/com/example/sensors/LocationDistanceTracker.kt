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

        var registeredAny = false
        if (gpsEnabled) {
            runCatching {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1200L,
                    2f,
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
                        1800L,
                        2f,
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
                        2200L,
                        3f,
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
                    currentSpeedKmh = 0f
                )
            )
            return
        }

        val prev = lastValidLocation
        if (prev == null) {
            lastValidLocation = location
            val initialSpeed = if (location.hasSpeed() && location.speed > 0.45f) location.speed * 3.6f else 0f
            onRoutePointRecorded(location.latitude, location.longitude, initialSpeed)
            onGpsStateChanged(
                GpsTrackingSnapshot(
                    hasLocationPermission = true,
                    isGpsEnabled = true,
                    hasFix = true,
                    accuracyMeters = accuracy,
                    currentSpeedKmh = initialSpeed
                )
            )
            return
        }

        val deltaMeters = prev.distanceTo(location)
        val dtSec = ((location.time - prev.time).coerceAtLeast(150L)) / 1000.0f
        val impliedSpeedMps = deltaMeters / dtSec
        val maxPlausibleSpeedMps = when (sport) {
            SportType.WALKING -> 3.6f   // ~13 km/h
            SportType.CYCLING -> 18.5f  // ~66 km/h
            else -> 9.5f                // ~34 km/h running / field sports
        }
        val minDriftThreshold = max(2.4f, accuracy * 0.40f)

        val isGenuineMovement = deltaMeters >= minDriftThreshold &&
            impliedSpeedMps in 0.35f..maxPlausibleSpeedMps
        val measuredSpeedMps = when {
            !isGenuineMovement -> 0f
            location.hasSpeed() && location.speed in 0.35f..maxPlausibleSpeedMps -> location.speed
            else -> impliedSpeedMps
        }
        val roundedSpeedKmh = (measuredSpeedMps * 3.6f * 10f).roundToInt() / 10f

        if (isGenuineMovement) {
            lastValidLocation = location
            onRoutePointRecorded(location.latitude, location.longitude, roundedSpeedKmh)
            pendingMetersBuffer += deltaMeters
            val wholeMeters = pendingMetersBuffer.toInt()
            if (wholeMeters >= 1) {
                pendingMetersBuffer -= wholeMeters
                onDistanceAddedMeters(wholeMeters)
            }
        }

        onGpsStateChanged(
            GpsTrackingSnapshot(
                hasLocationPermission = true,
                isGpsEnabled = true,
                hasFix = true,
                accuracyMeters = accuracy,
                currentSpeedKmh = roundedSpeedKmh
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
