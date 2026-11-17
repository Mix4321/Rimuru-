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
    private val onDistanceAddedMeters: (Int) -> Unit,
    private val onGpsStateChanged: (GpsTrackingSnapshot) -> Unit
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
                    1500L,
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
                        2000L,
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
                        2500L,
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

        val accuracy = if (location.hasAccuracy()) location.accuracy else 999f
        // Reject poor indoor/cell-tower fixes to prevent huge fake distance jumps
        if (accuracy > 28f) {
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
            onGpsStateChanged(
                GpsTrackingSnapshot(
                    hasLocationPermission = true,
                    isGpsEnabled = true,
                    hasFix = true,
                    accuracyMeters = accuracy,
                    currentSpeedKmh = if (location.hasSpeed() && location.speed > 0.4f) location.speed * 3.6f else 0f
                )
            )
            return
        }

        val deltaMeters = prev.distanceTo(location)
        val dtSec = ((location.time - prev.time).coerceAtLeast(100L)) / 1000.0f
        val impliedSpeedMps = deltaMeters / dtSec
        val minDriftThreshold = max(2.2f, accuracy * 0.35f)

        // Ignore stationary GPS jitter and impossible teleport spikes (> 25 m/s = 90 km/h)
        val isGenuineMovement = deltaMeters >= minDriftThreshold && impliedSpeedMps <= 25f
        val measuredSpeedMps = when {
            !isGenuineMovement -> 0f
            location.hasSpeed() && location.speed in 0.35f..25f -> location.speed
            else -> impliedSpeedMps
        }

        if (isGenuineMovement) {
            lastValidLocation = location
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
                currentSpeedKmh = (measuredSpeedMps * 3.6f * 10f).roundToInt() / 10f
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
