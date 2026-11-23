package app.woozy.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import app.woozy.di.AppScope
import app.woozy.domain.model.LocationFix
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of device position for the whole app.
 *
 * GPS is only active while something collects [fixes] (plus a 5 s grace period for configuration
 * changes) — when the map is not on screen and no navigation is running, location is off.
 * Uses the fused provider when Google Play services is present, otherwise the platform GPS +
 * network providers (de-Googled devices).
 */
@Singleton
class LocationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val statusMonitor: LocationStatusMonitor,
    @AppScope scope: CoroutineScope,
) {
    private val useFused: Boolean by lazy {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val fixes: SharedFlow<LocationFix> = statusMonitor.permission
        .map { it != LocationPermission.NOT_GRANTED }
        .distinctUntilChanged()
        .flatMapLatest { granted ->
            when {
                !granted -> emptyFlow()
                useFused -> fusedUpdates()
                else -> platformUpdates()
            }
        }
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), replay = 1)

    @SuppressLint("MissingPermission") // Guarded by the permission flow above + SecurityException catch.
    private fun fusedUpdates(): Flow<LocationFix> = callbackFlow {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { trySend(it.toFix()) }
            }
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
            .setMinUpdateIntervalMillis(500L)
            .setWaitForAccurateLocation(false)
            .build()
        try {
            client.lastLocation.addOnSuccessListener { last -> last?.let { trySend(it.toFix(cached = true)) } }
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission revoked while starting updates", e)
            close()
        }
        awaitClose { client.removeLocationUpdates(callback) }
    }

    @SuppressLint("MissingPermission")
    private fun platformUpdates(): Flow<LocationFix> = callbackFlow {
        val manager = context.getSystemService(LocationManager::class.java)
        if (manager == null) {
            close()
            return@callbackFlow
        }
        var lastGpsNanos = 0L
        val listener = LocationListener { location ->
            val isGps = location.provider == LocationManager.GPS_PROVIDER
            if (isGps) lastGpsNanos = location.elapsedRealtimeNanos
            // Network fixes only fill gaps; never let them override a fresh GPS fix.
            val gpsFresh = location.elapsedRealtimeNanos - lastGpsNanos < 10_000_000_000L
            if (isGps || !gpsFresh) trySend(location.toFix())
        }
        try {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { provider ->
                if (manager.allProviders.contains(provider)) {
                    manager.getLastKnownLocation(provider)?.let { trySend(it.toFix(cached = true)) }
                    manager.requestLocationUpdates(provider, 1_000L, 0f, listener, Looper.getMainLooper())
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission revoked while starting updates", e)
            close()
        }
        awaitClose { manager.removeUpdates(listener) }
    }

    private fun Location.toFix(cached: Boolean = false) = LocationFix(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        speedMps = if (hasSpeed()) speed else null,
        speedAccuracyMps = if (hasSpeedAccuracy()) speedAccuracyMetersPerSecond else null,
        bearingDegrees = if (hasBearing()) bearing else null,
        bearingAccuracyDegrees = if (hasBearingAccuracy()) bearingAccuracyDegrees else null,
        altitudeMeters = if (hasAltitude()) altitude else null,
        timeMillis = time,
        elapsedRealtimeNanos = elapsedRealtimeNanos,
        provider = provider,
        isCached = cached,
    )

    private companion object {
        const val TAG = "LocationRepository"
    }
}
