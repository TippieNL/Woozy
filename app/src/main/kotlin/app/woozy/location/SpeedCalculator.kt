package app.woozy.location

import app.woozy.domain.model.LocationFix
import app.woozy.util.Geo

/**
 * Turns raw GPS fixes into a stable speed value.
 *
 * - Prefers the chip-reported Doppler speed (accurate, low latency) when its accuracy is acceptable.
 * - Falls back to distance/time between consecutive fixes when the fix carries no speed.
 * - Rejects physically implausible spikes and smooths with an exponential moving average.
 * - Snaps to 0 below [stationaryThresholdMps] so the display doesn't jitter at traffic lights.
 *
 * Not thread-safe; feed it from a single coroutine.
 */
class SpeedCalculator(
    private val alpha: Double = 0.5,
    private val stationaryThresholdMps: Double = 0.85, // ~3 km/h
    private val maxPlausibleMps: Double = 90.0, // ~324 km/h
    private val maxSpeedAccuracyMps: Float = 3.0f,
    private val maxFixAccuracyForDerivationM: Float = 25f,
    private val staleAfterNanos: Long = 5_000_000_000L,
) {
    private var previous: LocationFix? = null
    private var smoothed: Double? = null
    private var lastValidNanos: Long = 0L

    fun reset() {
        previous = null
        smoothed = null
        lastValidNanos = 0L
    }

    /** @return smoothed speed in m/s, or null when speed is currently unknown. */
    fun update(fix: LocationFix): Double? {
        if (fix.isCached) return smoothed
        val raw = rawSpeed(fix)
        val prev = previous
        // Only advance the reference fix on forward time, so out-of-order fixes are ignored.
        if (prev == null || fix.elapsedRealtimeNanos > prev.elapsedRealtimeNanos) previous = fix

        if (raw == null || raw > maxPlausibleMps) {
            if (fix.elapsedRealtimeNanos - lastValidNanos > staleAfterNanos) smoothed = null
            return displayValue()
        }
        lastValidNanos = fix.elapsedRealtimeNanos
        val current = smoothed
        smoothed = if (current == null) raw else current + alpha * (raw - current)
        return displayValue()
    }

    private fun displayValue(): Double? = smoothed?.let { if (it < stationaryThresholdMps) 0.0 else it }

    private fun rawSpeed(fix: LocationFix): Double? {
        val reported = fix.speedMps
        if (reported != null && reported >= 0f) {
            val acc = fix.speedAccuracyMps
            if (acc == null || acc <= maxSpeedAccuracyMps) return reported.toDouble()
        }
        val prev = previous ?: return null
        val dtSeconds = (fix.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1e9
        if (dtSeconds !in 0.5..10.0) return null
        val accA = fix.accuracyMeters ?: return null
        val accB = prev.accuracyMeters ?: return null
        if (accA > maxFixAccuracyForDerivationM || accB > maxFixAccuracyForDerivationM) return null
        val distance = Geo.distanceMeters(prev.latitude, prev.longitude, fix.latitude, fix.longitude)
        // Movement smaller than the position uncertainty is indistinguishable from standing still.
        if (distance < (accA + accB) / 2) return 0.0
        return distance / dtSeconds
    }
}
