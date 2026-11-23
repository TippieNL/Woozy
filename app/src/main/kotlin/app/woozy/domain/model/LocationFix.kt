package app.woozy.domain.model

/**
 * A single position fix from the device, decoupled from android.location.Location so the
 * domain/logic layers stay unit-testable.
 */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val speedMps: Float?,
    val speedAccuracyMps: Float?,
    val bearingDegrees: Float?,
    val bearingAccuracyDegrees: Float?,
    val altitudeMeters: Double?,
    /** Wall-clock time of the fix (UTC millis). */
    val timeMillis: Long,
    /** Monotonic time of the fix; use this for deltas, never wall-clock time. */
    val elapsedRealtimeNanos: Long,
    val provider: String?,
    /** True for a cached "last known" fix delivered at start-up; never use it for speed. */
    val isCached: Boolean = false,
)

data class GeoPoint(val latitude: Double, val longitude: Double)

fun LocationFix.toGeoPoint() = GeoPoint(latitude, longitude)
